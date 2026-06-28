package gov.brgy.ebms.api;

import gov.brgy.ebms.security.SecurityUtils;
import gov.brgy.ebms.security.dto.ChangePasswordRequest;
import gov.brgy.ebms.security.dto.LoginRequest;
import gov.brgy.ebms.security.dto.LoginResponse;
import gov.brgy.ebms.security.dto.RefreshRequest;
import gov.brgy.ebms.security.dto.UpdateLocaleRequest;
import gov.brgy.ebms.security.dto.UserResponse;
import gov.brgy.ebms.security.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
        @Valid @RequestBody LoginRequest request,
        HttpServletRequest httpRequest
    ) {
        String ip = httpRequest.getRemoteAddr();
        String ua = httpRequest.getHeader("User-Agent");
        LoginResponse response = authService.login(request, ip, ua);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        LoginResponse response = authService.refresh(request.refreshToken());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        Long userId = SecurityUtils.getAuthenticatedUserId();
        if (userId != null) {
            authService.logout(userId);
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * Returns the current user's profile (username, roles, fullName, preferredLocale).
     * Requires a valid JWT or portal session.
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe() {
        Long userId = SecurityUtils.getAuthenticatedUserId();
        return ResponseEntity.ok(authService.getCurrentUser(userId));
    }

    /**
     * Updates the current user's preferred locale.
     * Accepted values: "en" or "fil".
     */
    @PutMapping("/me/locale")
    public ResponseEntity<Void> updateLocale(@Valid @RequestBody UpdateLocaleRequest request) {
        Long userId = SecurityUtils.getAuthenticatedUserId();
        authService.updateLocale(userId, request.locale());
        return ResponseEntity.noContent().build();
    }

    /**
     * Changes the current user's password.
     * Validates current password, enforces policy, clears forcedPasswordChange flag.
     */
    @PatchMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        Long userId = SecurityUtils.getAuthenticatedUserId();
        authService.changePassword(
            userId,
            request.currentPassword(),
            request.newPassword(),
            request.confirmPassword()
        );
        return ResponseEntity.noContent().build();
    }
}
