package gov.brgy.ebms.desktop.core;

public class Config {

    public static final String BASE_URL = resolveBaseUrl();
    /** Dev self-signed cert flag — set false (or use sysprop ebms.trustAll=false) in production. */
    public static final boolean TRUST_ALL_CERTS = resolveTrustAll();

    private Config() {}

    private static String resolveBaseUrl() {
        String sysprop = System.getProperty("ebms.baseUrl");
        if (sysprop != null) return sysprop;
        String env = System.getenv("EBMS_BASE_URL");
        if (env != null) return env;
        return "https://localhost:8443";
    }

    private static boolean resolveTrustAll() {
        String sysprop = System.getProperty("ebms.trustAll");
        if (sysprop != null) return Boolean.parseBoolean(sysprop);
        return true; // dev default
    }
}
