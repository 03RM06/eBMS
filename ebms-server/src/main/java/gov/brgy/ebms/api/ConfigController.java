package gov.brgy.ebms.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

/**
 * Exposes public server configuration and server-side i18n messages.
 *
 * <p>GET /api/v1/config/public — no auth required (permitted in SecurityConfig)
 * <p>GET /api/v1/config/i18n/{locale} — any authenticated user
 */
@RestController
@RequestMapping("/api/v1/config")
public class ConfigController {

    @Value("${barangay.name}")
    private String barangayName;

    @Value("${barangay.doc.prefix}")
    private String docPrefix;

    /** Fixed at first request time — represents the server startup time. */
    private final String buildTime = LocalDateTime.now().toString();

    /**
     * Returns public server configuration.
     * No authentication required — allowed via SecurityConfig permit list.
     */
    @GetMapping("/public")
    public ResponseEntity<Map<String, String>> getPublicConfig() {
        Map<String, String> config = new LinkedHashMap<>();
        config.put("barangayName", barangayName);
        config.put("docPrefix", docPrefix);
        config.put("version", "1.0.0-SNAPSHOT");
        config.put("buildTime", buildTime);
        return ResponseEntity.ok(config);
    }

    /**
     * Returns a flat key-value map of all i18n messages for the requested locale.
     * Supported locales: "en", "fil". Returns 400 for any other value.
     * Requires any authenticated user.
     */
    @GetMapping("/i18n/{locale}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> getI18nMessages(
        @PathVariable String locale
    ) {
        if (!"en".equals(locale) && !"fil".equals(locale)) {
            return ResponseEntity.badRequest().build();
        }

        String resourcePath = "i18n/messages_" + locale + ".properties";
        Properties props;
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            props = PropertiesLoaderUtils.loadProperties(resource);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }

        // Sort keys for deterministic response order
        Map<String, String> messages = new TreeMap<>();
        Set<String> keys = props.stringPropertyNames();
        for (String key : keys) {
            messages.put(key, props.getProperty(key));
        }
        return ResponseEntity.ok(messages);
    }
}
