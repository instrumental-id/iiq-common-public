package com.identityworksllc.iiq.common.logging;

/**
 * Logging constants
 */
public class LoggingConstants {
    /**
     * Client IP address (from request)
     */
    public static final String LOG_CLIENT_IP = "remoteAddr";
    /**
     * Context ID (a UUID) that can be logged with every message (via NDC or MDC)
     */
    public static final String LOG_CTX_ID = "ctx";
    /**
     * The HTTP method of the request (GET, POST, etc.)
     */
    public static final String LOG_HTTP_METHOD = "httpMethod";
    /**
     * The REST endpoint class being called
     */
    public static final String LOG_MDC_ENDPOINT_CLASS = "endpoint:class";
    /**
     * The REST endpoint method being called
     */
    public static final String LOG_MDC_ENDPOINT_METHOD = "endpoint:method";
    /**
     * Logged-in
     */
    public static final String LOG_MDC_PLUGIN = "endpoint:plugin";

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
    public static final String LOG_MDC_USER_DISPLAY = "loggedInUser:displayName";

    public static final String LOG_MDC_USER_TARGET = "target";

    public static final String LOG_MDC_USER_SOURCE = "source";
}
