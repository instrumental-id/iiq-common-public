package com.identityworksllc.iiq.common.vo;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.annotation.JsonAppend;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.tools.Message;
import sailpoint.tools.Util;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Implements a timestamped log message similar to what would be captured by a
 * logging framework. However, this one can be passed around as a VO for UI and
 * other purposes.
 *
 * It is serialized to JSON by {@link StampedMessageSerializer}.
 */
@JsonSerialize(using = StampedMessageSerializer.class)
@JsonClassDescription("A message associated with a timestamp, akin to a log message from any logger tool")
public class StampedMessage implements Serializable {

    /**
     * The log logging logger
     */
    private static final Log metaLog = LogFactory.getLog(StampedMessage.class);

    /**
     * The exception associated with this log message
     */
    @JsonPropertyDescription("The exception associated with this message, if any")
    private final Throwable exception;

    /**
     * The log level
     */
    @JsonPropertyDescription("The log level of the message")
    private final LogLevel level;

    /**
     * The string message
     */
    @JsonPropertyDescription("The message string")
    private final String message;

    /**
     * The name of the thread producing this message
     */
    @JsonPropertyDescription("The name of the thread producing this message")
    private final String thread;

    /**
     * The timestamp in milliseconds that this object was created
     */
    @JsonPropertyDescription("The message timestamp, in Unix epoch milliseconds")
    private final long timestamp;

    /**
     * Creates a basic log of the given level with the given string message
     * @param level The log level
     * @param message The message
     */
    public StampedMessage(LogLevel level, String message) {
        this(level, message, null);
    }

    /**
     * Full constructor taking a level, a message, and an exception. The log timestamp will be
     * the system timestamp when this constructor is invoked.
     *
     * @param level The log level
     * @param message The string message
     * @param exception The exception (or null)
     */
    public StampedMessage(LogLevel level, String message, Throwable exception) {
        this.timestamp = System.currentTimeMillis();
        this.thread = Thread.currentThread().getName();
        this.level = Objects.requireNonNull(level);
        this.message = message;
        this.exception = exception;
    }

    /**
     * Creates a log from a SailPoint Message object
     * @param message A Sailpoint message
     */
    public StampedMessage(Message message) {
        this(message, null);
    }

    /**
     * Creates a log from a SailPoint Message object and a throwable
     * @param message The message object
     * @param throwable The error object
     */
    public StampedMessage(Message message, Throwable throwable) {
        this(message.isError() ? LogLevel.ERROR : (message.isWarning() ? LogLevel.WARN : LogLevel.INFO), message.getMessage(), throwable);
    }

    /**
     * Creates a basic INFO level log with the given string message
     * @param message The string message
     */
    public StampedMessage(String message) {
        this(LogLevel.INFO, message, null);
    }

    /**
     * Creates a log of ERROR level with the given message and Throwable
     * @param message The message to log
     * @param throwable The throwable to log
     */
    public StampedMessage(String message, Throwable throwable) {
        this(LogLevel.ERROR, message, throwable);
    }

    /**
     * Creates a log of ERROR level with the given Throwable, using its getMessage() as the message
     * @param throwable The throwable to log
     */
    public StampedMessage(Throwable throwable) {
        this(LogLevel.ERROR, throwable.getMessage(), throwable);
    }

    /**
     * Gets the exception associated with this LogMessage
     * @return The exception, or null
     */
    public Throwable getException() {
        return exception;
    }

    /**
     * Gets the log level associated with this LogMessage
     * @return The log level, never null
     */
    public LogLevel getLevel() {
        return level;
    }

    /**
     * Gets the log message
     * @return The log message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Gets the millisecond timestamp when this object was created
     * @return The millisecond timestamp of creation
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Returns true if the log level of this message is exactly DEBUG
     */
    public boolean isDebug() {
        return Util.nullSafeEq(this.getLevel(), LogLevel.DEBUG);
    }

    /**
     * Returns true if the log level of this message is exactly ERROR
     */
    public boolean isError() {
        return Util.nullSafeEq(this.getLevel(), LogLevel.ERROR);
    }

    /**
     * Returns true if the log level of this message is exactly INFO
     */
    public boolean isInfo() {
        return Util.nullSafeEq(this.getLevel(), LogLevel.INFO);
    }

    /**
     * Returns true if the log level of this message is INFO, WARN, or ERROR
     */
    public boolean isInfoOrHigher() {
        return this.isInfo() || this.isWarningOrHigher();
    }

    /**
     * Returns true if the log level of this message is WARN
     */
    public boolean isWarning() {
        return Util.nullSafeEq(this.getLevel(), LogLevel.WARN);
    }

    /**
     * Returns true if the log level of this message is WARN or ERROR
     */
    public boolean isWarningOrHigher() {
        return this.isWarning() || this.isError();
    }

    /**
     * Transforms this object to a Map of strings, suitable for passing to a client as
     * JSON or XML. This map will contain the following keys:
     *
     *  - 'timestamp': The value of {@link #getTimestamp()} converted to a string
     *  - 'formattedDate': The value of {@link #getTimestamp()}, formatted using {@link DateTimeFormatter#ISO_INSTANT}
     *  - 'message': The message, exactly as passed to this class
     *  - 'level': The string name of the {@link LogLevel}, e.g., 'DEBUG'
     *  - 'exception': If present, the output of {@link Throwable#printStackTrace(PrintWriter)}
     *
     * The 'exception' key will be missing if this object does not return a value for {@link #getException()}.
     *
     * @return This object as a Map
     */
    public Map<String, String> toMap() {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT;
        Map<String, String> map = new HashMap<>();
        map.put("timestamp", String.valueOf(this.timestamp));
        map.put("formattedDate", formatter.format(new Date(timestamp).toInstant()));
        map.put("message", message);
        map.put("level", level.name());
        map.put("thread", thread);

        if (exception != null) {
            try (StringWriter target = new StringWriter()) {
                try (PrintWriter printWriter = new PrintWriter(target)) {
                    exception.printStackTrace(printWriter);
                }
                map.put("exception", target.toString());
            } catch(IOException e) {
                metaLog.error("Unable to print object of type " + exception.getClass(), e);
            }
        }

        return map;
    }

    /**
     * Transforms this object into a log4j-type log string.
     *
     * Example: `2024-04-15 14:42:31.112 [Thread-10] [DEBUG] Your message`
     */
    public String toString() {
        StringBuilder value = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        value.append(sdf.format(new Date(timestamp)));
        value.append(" [");
        value.append(this.thread);
        value.append("] ");
        value.append(String.format("%5s", level.toString()));
        value.append(" ");
        value.append(message);

        if (exception != null) {
            value.append("\n");
            try (StringWriter target = new StringWriter()) {
                try (PrintWriter printWriter = new PrintWriter(target)) {
                    exception.printStackTrace(printWriter);
                }
                value.append(target);
            } catch(IOException e) {
                metaLog.error("Unable to print object of type " + exception.getClass(), e);
            }
        }

        return value.toString();
    }
}
