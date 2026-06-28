package gov.brgy.ebms.security;

import gov.brgy.ebms.security.dto.LoginRequest;
import gov.brgy.ebms.security.entity.Role;
import gov.brgy.ebms.security.entity.User;
import gov.brgy.ebms.security.jwt.JwtProperties;
import gov.brgy.ebms.security.jwt.JwtService;
import gov.brgy.ebms.security.repository.LoginAttemptRepository;
import gov.brgy.ebms.security.repository.RefreshTokenRepository;
import gov.brgy.ebms.security.repository.UserRepository;
import gov.brgy.ebms.security.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for AC-003: Account locked after 5 consecutive failed login attempts;
 * attempts logged to login_attempts.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceLockoutTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private LoginAttemptRepository loginAttemptRepository;

    private JwtService jwtService;
    private PasswordEncoder passwordEncoder;

    @InjectMocks private AuthService authService;

    private static final String CORRECT_PASSWORD = "correct-password";

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret("test-secret-key-for-unit-tests-only-256-bits-long-padded-here-x");
        props.setAccessTokenExpiryMinutes(15);
        props.setRefreshTokenExpiryDays(7);
        jwtService = new JwtService(props);

        passwordEncoder = new BCryptPasswordEncoder(12);

        ReflectionTestUtils.setField(authService, "jwtService", jwtService);
        ReflectionTestUtils.setField(authService, "jwtProperties", props);
        ReflectionTestUtils.setField(authService, "passwordEncoder", passwordEncoder);
        ReflectionTestUtils.setField(authService, "maxFailedAttempts", 5);
        ReflectionTestUtils.setField(authService, "lockoutDurationMinutes", 15L);
    }

    /** AC-003: The 5th failed attempt must set lockedUntil on the user. */
    @Test
    void login_onFifthFailedAttempt_shouldSetLockedUntil() {
        User user = buildUser(4); // already has 4 failed attempts
        when(userRepository.findByUsername("user")).thenReturn(Optional.of(user));
        when(loginAttemptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<User> savedUserCaptor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(savedUserCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() ->
            authService.login(new LoginRequest("user", "wrong-password"), "127.0.0.1", "ua")
        ).isInstanceOf(SecurityException.class);

        User saved = savedUserCaptor.getValue();
        assertThat(saved.getFailedLoginAttempts())
            .as("Failed attempts should be 5 after 5th failure")
            .isEqualTo(5);
        assertThat(saved.getLockedUntil())
            .as("AC-003: lockedUntil must be set after 5 failed attempts")
            .isNotNull();
        assertThat(saved.getLockedUntil())
            .as("Lockout must be in the future")
            .isAfter(LocalDateTime.now());
    }

    /** AC-003: A locked account must throw on the next login attempt. */
    @Test
    void login_whenAccountLocked_shouldThrowLockedMessage() {
        User user = buildUser(5);
        user.setLockedUntil(LocalDateTime.now().plusMinutes(14)); // currently locked
        when(userRepository.findByUsername("user")).thenReturn(Optional.of(user));
        when(loginAttemptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() ->
            authService.login(new LoginRequest("user", CORRECT_PASSWORD), "127.0.0.1", "ua")
        ).isInstanceOf(SecurityException.class)
         .hasMessageContaining("locked");
    }

    /** AC-003: Failed login attempt must be persisted to login_attempts table. */
    @Test
    void login_failedAttempt_shouldBeLoggedToLoginAttempts() {
        User user = buildUser(0);
        when(userRepository.findByUsername("user")).thenReturn(Optional.of(user));
        when(loginAttemptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.save(any())).thenReturn(user);

        assertThatThrownBy(() ->
            authService.login(new LoginRequest("user", "wrong-password"), "10.0.0.1", "ua")
        ).isInstanceOf(SecurityException.class);

        verify(loginAttemptRepository, atLeastOnce()).save(any());
    }

    /** AC-003: Successful login after < 5 failures should reset failed attempt counter. */
    @Test
    void login_successAfterSomeFailed_shouldResetFailedAttempts() {
        User user = buildUser(3); // 3 prior failures — not yet locked
        when(userRepository.findByUsername("user")).thenReturn(Optional.of(user));
        when(loginAttemptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<User> savedUserCaptor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(savedUserCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        authService.login(new LoginRequest("user", CORRECT_PASSWORD), "127.0.0.1", "ua");

        // The save on success should reset failed attempts to 0
        User saved = savedUserCaptor.getAllValues().stream()
            .filter(u -> u.getFailedLoginAttempts() == 0)
            .findFirst()
            .orElse(null);
        assertThat(saved)
            .as("AC-003: successful login must reset failed attempt counter")
            .isNotNull();
        assertThat(saved.getLockedUntil())
            .as("lockedUntil must be cleared on successful login")
            .isNull();
    }

    private User buildUser(int failedAttempts) {
        User user = new User();
        user.setId(1L);
        user.setUsername("user");
        user.setFullName("Test User");
        user.setPasswordHash(passwordEncoder.encode(CORRECT_PASSWORD));
        user.setEnabled(true);
        user.setFailedLoginAttempts(failedAttempts);
        user.setRoles(Set.of());
        return user;
    }
}
