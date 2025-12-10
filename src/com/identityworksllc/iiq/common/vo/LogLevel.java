package com.identityworksllc.iiq.common.vo;

/**
 * Enumerates a LogLevel type, particularly for {@link StampedMessage}. The
 * levels are intended to have the same semantics as Commons Logging or Log4j.
 */
public enum LogLevel {
    /**
     * Trace level: indicates very low-level method input/output debugging
     */
    TRACE(0),

    /**
     * Debug level: indicates a medium-level debugging message
     */
    DEBUG(1),

    /**
     * Info level: indicates an ordinary log message providing information
     */
    INFO(2),

    /**
     * Warn level: indicates a potential problem
     */
    WARN(3),

    /**
     * Error level: indicates a severe problem, possibly one interrupting the process
     */
    ERROR(4);

    private final int ordinalValue;

    LogLevel(int ordinalValue) {
        this.ordinalValue = ordinalValue;
    }

    /**
     * Determines whether this log level is at least as severe as the other
     * @param other the other log level to compare against
     * @return true if this level is at least as severe as the other level
     */
    public boolean atLeast(LogLevel other) {
        return this.ordinalValue >= other.ordinalValue;
    }

    public int getOrdinalValue() {
        return ordinalValue;
    }
}
