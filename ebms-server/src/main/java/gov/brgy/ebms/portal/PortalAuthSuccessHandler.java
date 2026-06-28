package gov.brgy.ebms.portal;

import gov.brgy.ebms.security.SecurityUtils;
import gov.brgy.ebms.security.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * SEC-FIX-2: Portal form-login success handler.
 *
 * <p>After successful authentication, loads the DB user record by username and stores
 * the user's primary key ({@code user.getId()}) in the HTTP session under
 * {@link SecurityUtils#SESSION_USER_ID_KEY}. This allows
 * {@link SecurityUtils#getAuthenticatedUserId()} to return a non-null value for portal
 * (session-based) users, which in turn allows {@code enforceResidentOwnership()} to
 * work correctly without silently skipping the ownership check.
 */
@Component
public class PortalAuthSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository userRepository;

    public PortalAuthSuccessHandler(UserRepository userRepository) {
        super("/portal/dashboard");
        setAlwaysUseDefaultTargetUrl(true);
        this.userRepository = userRepository;
    }

    @Override
    public void onAuthenticationSuccess(
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication
    ) throws IOException, ServletException {
        String username = authentication.getName();
        userRepository.findByUsername(username).ifPresent(user ->
            request.getSession().setAttribute(SecurityUtils.SESSION_USER_ID_KEY, user.getId())
        );
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
