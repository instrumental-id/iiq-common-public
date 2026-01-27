package com.identityworksllc.iiq.common.plugin;

/**
 * Logging constants
 */
public class LoggingConstants {

    /**
     * Context ID (a UUID) that can be logged with every message (via NDC)
     */
    public static final String LOG_CTX_ID = "ctx";
    /**
     * The REST endpoint class being called
     */
    public static final String LOG_MDC_ENDPOINT_CLASS = "endpointClass";
    /**
     * The REST endpoint method being called
     */
    public static final String LOG_MDC_ENDPOINT_METHOD = "endpointMethod";
    /**
     * Logged-in
     */
    public static final String LOG_MDC_PLUGIN = "plugin";

    /**
     * The request URI
     */
    public static final String LOG_MDC_URI = "requestUri";
    /**
     * Logged-in user name (Identity.name)
     */
    public static final String LOG_MDC_USER = "loggedInUser";
    /**
     * Logged-in user display name (Identity.displayName)
     */
    public static final String LOG_MDC_USER_DISPLAY = "loggedInUserDisplayName";
}
