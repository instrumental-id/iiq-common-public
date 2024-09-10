package com.identityworksllc.iiq.common.vo;

/**
 * Enumerates a LogLevel type, particularly for {@link StampedMessage}. The
 * levels are intended to have the same semantics as Commons Logging or Log4j.
 */
public enum LogLevel {
    /**
     * Trace level: indicates very low-level method input/output debugging
     */
    TRACE,

    /**
     * Debug level: indicates a medium-level debugging message
     */
    DEBUG,

    /**
     * Info level: indicates an ordinary log message providing information
     */
    INFO,

    /**
     * Warn level: indicates a potential problem
     */
    WARN,

    /**
     * Error level: indicates a severe problem, possibly one interrupting the process
     */
    ERROR
}
