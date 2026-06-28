package gov.brgy.ebms.security;

import gov.brgy.ebms.security.dto.LoginRequest;
import gov.brgy.ebms.security.dto.LoginResponse;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private LoginAttemptRepository loginAttemptRepository;

    private JwtService jwtService;
    private PasswordEncoder passwordEncoder;

    @InjectMocks private AuthService authService;

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

    @Test
    void login_shouldSucceedWithValidCredentials() {
        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        user.setFullName("Admin User");
        user.setPasswordHash(passwordEncoder.encode("Admin@1234"));
        user.setEnabled(true);
        user.setFailedLoginAttempts(0);

        Role role = new Role("SUPER_ADMIN", "Super Admin", "Super Admin");
        role.setId(1L);
        user.setRoles(Set.of(role));

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(loginAttemptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LoginResponse response = authService.login(
            new LoginRequest("admin", "Admin@1234"), "127.0.0.1", "test-agent"
        );

        assertThat(response).isNotNull();
        assertThat(response.username()).isEqualTo("admin");
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.roles()).contains("SUPER_ADMIN");
    }

    @Test
    void login_shouldThrowForInvalidPassword() {
        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        user.setPasswordHash(passwordEncoder.encode("correct"));
        user.setEnabled(true);
        user.setFailedLoginAttempts(0);
        user.setRoles(Set.of());

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(loginAttemptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() ->
            authService.login(new LoginRequest("admin", "wrong"), "127.0.0.1", "ua")
        ).isInstanceOf(SecurityException.class)
         .hasMessageContaining("Invalid credentials");
    }

    @Test
    void login_shouldThrowForNonExistentUser() {
        when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());
        when(loginAttemptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() ->
            authService.login(new LoginRequest("nobody", "pass"), "127.0.0.1", "ua")
        ).isInstanceOf(SecurityException.class);
    }
}
