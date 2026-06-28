package gov.brgy.ebms.security;

import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class SecurityUtils {

    /** Session attribute key used by PortalAuthSuccessHandler. */
    public static final String SESSION_USER_ID_KEY = "userId";

    private SecurityUtils() {}

    /**
     * Returns the authenticated user's DB ID.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>JWT API calls: {@code auth.getDetails()} is a {@code Long} set by
     *       {@link gov.brgy.ebms.security.jwt.JwtAuthenticationFilter}.</li>
     *   <li>Portal form-login sessions: {@code auth.getDetails()} is
     *       {@code WebAuthenticationDetails} (remote addr), so we fall back to the
     *       HTTP session attribute {@value #SESSION_USER_ID_KEY} stored by
     *       {@link gov.brgy.ebms.portal.PortalAuthSuccessHandler} at login time.</li>
     * </ol>
     *
     * <p>Returns {@code null} only when no authentication context is present at all
     * (e.g. unauthenticated requests, or pure unit tests without a SecurityContext).
     */
    public static Long getAuthenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }

        // JWT path: JwtAuthenticationFilter stores the DB user ID in details
        if (auth.getDetails() instanceof Long userId) {
            return userId;
        }

        // Portal session fallback: PortalAuthSuccessHandler stores userId in session
        try {
            ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpSession session = attrs.getRequest().getSession(false);
                if (session != null) {
                    Object sessionUserId = session.getAttribute(SESSION_USER_ID_KEY);
                    if (sessionUserId instanceof Long id) {
                        return id;
                    }
                }
            }
        } catch (Exception ignored) {
            // RequestContextHolder unavailable (non-web context, e.g. scheduled tasks)
        }

        return null;
    }
}
