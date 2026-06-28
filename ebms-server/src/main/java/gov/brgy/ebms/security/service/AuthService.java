package gov.brgy.ebms.security.service;

import gov.brgy.ebms.security.dto.LoginRequest;
import gov.brgy.ebms.security.dto.LoginResponse;
import gov.brgy.ebms.security.dto.UserResponse;
import jakarta.persistence.EntityNotFoundException;
import gov.brgy.ebms.security.entity.LoginAttempt;
import gov.brgy.ebms.security.entity.RefreshToken;
import gov.brgy.ebms.security.entity.User;
import gov.brgy.ebms.security.jwt.JwtProperties;
import gov.brgy.ebms.security.jwt.JwtService;
import gov.brgy.ebms.security.repository.LoginAttemptRepository;
import gov.brgy.ebms.security.repository.RefreshTokenRepository;
import gov.brgy.ebms.security.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    /**
     * FIX-14: Dummy bcrypt hash used for constant-time comparison when username is not found.
     * Prevents username enumeration via timing side-channel.
     */
    private static final String DUMMY_HASH =
        "$2a$12$OwZNLkhV8MZZ3PEGmBVVKuOt9hAm/C78f1K.y78Pv1/vbGQ0z5hE6";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final PasswordEncoder passwordEncoder;

    @Value("${security.max-failed-attempts:5}")
    private int maxFailedAttempts;

    @Value("${security.lockout-duration-minutes:15}")
    private long lockoutDurationMinutes;

    public AuthService(
        UserRepository userRepository,
        RefreshTokenRepository refreshTokenRepository,
        LoginAttemptRepository loginAttemptRepository,
        JwtService jwtService,
        JwtProperties jwtProperties,
        PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.loginAttemptRepository = loginAttemptRepository;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public LoginResponse login(LoginRequest request, String ipAddress, String userAgent) {
        Optional<User> userOpt = userRepository.findByUsername(request.username());

        if (userOpt.isEmpty()) {
            // FIX-14: constant-time check to prevent username enumeration via timing
            passwordEncoder.matches(request.password(), DUMMY_HASH);
            logAttempt(request.username(), null, false, ipAddress, userAgent);
            throw new SecurityException("Invalid credentials");
        }

        User user = userOpt.get();

        if (user.isAccountLocked()) {
            logAttempt(request.username(), user.getId(), false, ipAddress, userAgent);
            throw new SecurityException("Account is temporarily locked");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            handleFailedLogin(user);
            logAttempt(request.username(), user.getId(), false, ipAddress, userAgent);
            throw new SecurityException("Invalid credentials");
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        logAttempt(request.username(), user.getId(), true, ipAddress, userAgent);

        List<String> roles = user.getRoles().stream().map(r -> r.getCode()).toList();
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getUsername(), roles);
        String rawRefreshToken = generateRefreshToken(user.getId());

        // FIX-10: signal to client if forced password change is required
        return new LoginResponse(
            accessToken, rawRefreshToken,
            user.getId(), user.getUsername(), user.getFullName(),
            roles,
            user.isForcedPasswordChange()
        );
    }

    @Transactional
    public LoginResponse refresh(String rawRefreshToken) {
        String tokenHash = sha256(rawRefreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
            .orElseThrow(() -> new SecurityException("Invalid refresh token"));

        if (!stored.isValid()) {
            throw new SecurityException("Refresh token expired or revoked");
        }

        stored.revoke();
        refreshTokenRepository.save(stored);

        User user = userRepository.findById(stored.getUserId())
            .orElseThrow(() -> new SecurityException("User not found"));

        List<String> roles = user.getRoles().stream().map(r -> r.getCode()).toList();
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getUsername(), roles);
        String newRawToken = generateRefreshToken(user.getId());

        return new LoginResponse(
            accessToken, newRawToken,
            user.getId(), user.getUsername(), user.getFullName(),
            roles,
            user.isForcedPasswordChange()
        );
    }

    @Transactional
    public void logout(Long userId) {
        if (userId != null) {
            refreshTokenRepository.revokeAllByUserId(userId);
        }
    }

    /**
     * Returns the profile of the currently authenticated user.
     */
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
        return UserResponse.from(user);
    }

    /**
     * Updates the preferred locale for the authenticated user.
     * Accepted values: "en" or "fil".
     */
    @Transactional
    public void updateLocale(Long userId, String locale) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
        user.setPreferredLocale(locale);
        userRepository.save(user);
    }

    /**
     * Changes the authenticated user's password.
     * Validates current password, enforces min-10-chars + letter+digit policy,
     * and clears the forcedPasswordChange flag on success.
     */
    @Transactional
    public void changePassword(Long userId, String currentPassword,
                               String newPassword, String confirmPassword) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new SecurityException("Current password is incorrect");
        }

        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("New password and confirmation do not match");
        }
        if (newPassword.length() < 10) {
            throw new IllegalArgumentException("Password must be at least 10 characters");
        }
        boolean hasLetter = newPassword.chars().anyMatch(Character::isLetter);
        boolean hasDigit  = newPassword.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new IllegalArgumentException(
                "Password must contain at least one letter and one digit");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setForcedPasswordChange(false);
        userRepository.save(user);
    }

    private void handleFailedLogin(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= maxFailedAttempts) {
            user.setLockedUntil(LocalDateTime.now().plusMinutes(lockoutDurationMinutes));
        }
        userRepository.save(user);
    }

    private String generateRefreshToken(Long userId) {
        String raw = UUID.randomUUID().toString();
        String hash = sha256(raw);
        LocalDateTime expiry = LocalDateTime.now().plusDays(jwtProperties.getRefreshTokenExpiryDays());
        refreshTokenRepository.save(new RefreshToken(userId, hash, expiry));
        return raw;
    }

    private void logAttempt(String username, Long userId, boolean success, String ipAddress, String userAgent) {
        loginAttemptRepository.save(new LoginAttempt(username, userId, success, ipAddress, userAgent));
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
