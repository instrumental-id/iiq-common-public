package com.identityworksllc.iiq.common.vo;

import com.fasterxml.jackson.annotation.*;
import com.identityworksllc.iiq.common.logging.SLogger;
import sailpoint.object.Configuration;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Represents a value along with a list of stamped log messages, supporting various log levels.
 *
 * This class is designed to encapsulate a result value of type {@code T} and a list of {@link StampedMessage}
 * objects that record messages at different log levels (INFO, DEBUG, ERROR, TRACE, WARN). It provides methods
 * to add messages at each log level, check if a log level is enabled, and retrieve the value or messages.
 *
 * The log level can be set to control which messages are recorded. Messages are only added if the current
 * log level is at least as high as the level of the message being added.
 *
 * @param <T> The type of the result value
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ResultValue<T> implements Supplier<T> {
    /**
     * The current log level for this result. Controls which messages are recorded.
     * This field is transient and ignored by Jackson serialization.
     */
    @JsonIgnore
    private transient LogLevel logLevel;
    /**
     * The list of stamped messages associated with this result.
     */
    private final List<StampedMessage> messages;
    /**
     * The result value.
     */
    private T value;
    /**
     * Flag indicating whether the value has been set. The value can be set
     * to null, so this flag is used to track if it was explicitly set.
     */
    private boolean valueSet;

    /**
     * Default constructor. Initializes with an empty message list and INFO log level.
     */
    public ResultValue() {
        this.messages = new ArrayList<>();
        this.logLevel = LogLevel.INFO;
    }

    /**
     * Jackson constructor for deserialization.
     *
     * @param value    The result value
     * @param messages The list of stamped messages
     * @param valueSet Flag indicating if the value has been set
     */
    @JsonCreator
    public ResultValue(@JsonProperty("value") T value, @JsonProperty("messages") List<StampedMessage> messages, @JsonProperty("valueSet") boolean valueSet) {
        this.value = value;
        this.messages = messages != null ? new ArrayList<>(messages) : new ArrayList<>();
        this.logLevel = LogLevel.INFO;
        this.valueSet = valueSet;
    }

    /**
     * Adds a DEBUG level message using a supplier, if DEBUG is enabled.
     *
     * @param value The message supplier
     */
    public void debug(Supplier<String> value) {
        if (logLevel.atLeast(LogLevel.DEBUG)) {
            messages.add(new StampedMessage(LogLevel.DEBUG, value.get()));
        }
    }

    /**
     * Adds a DEBUG level message with formatting, if DEBUG is enabled.
     *
     * @param message The message format string
     * @param args    The arguments for formatting
     */
    public void debug(String message, Object... args) {
        debug(() -> {
            Object[] formattedArgs = SLogger.format(args);
            return String.format(message, formattedArgs);
        });
    }

    /**
     * Adds an ERROR level message using a supplier, if ERROR is enabled.
     *
     * @param value The message supplier
     */
    public void error(Supplier<String> value) {
        if (logLevel.atLeast(LogLevel.ERROR)) {
            messages.add(new StampedMessage(LogLevel.ERROR, value.get()));
        }
    }

    /**
     * Adds an ERROR level message with formatting, if ERROR is enabled.
     *
     * @param message The message format string
     * @param args    The arguments for formatting
     */
    public void error(String message, Object... args) {
        error(() -> {
            Object[] formattedArgs = SLogger.format(args);
            return String.format(message, formattedArgs);
        });
    }

    /**
     * Returns the result value.
     * @return The value
     */
    public T get() {
        return getValue();
    }

    /**
     * Returns the list of stamped messages.
     *
     * @return The list of messages
     */
    public List<StampedMessage> getMessages() {
        return messages;
    }

    /**
     * Returns the result value.
     *
     * @return The value
     */
    public T getValue() {
        return value;
    }

    /**
     * Returns true if there are any messages.
     *
     * @return True if messages exist, false otherwise
     */
    public boolean hasMessages() {
        return !messages.isEmpty();
    }

    /**
     * Adds an INFO level message using a supplier, if INFO is enabled.
     *
     * @param value The message supplier
     */
    public void info(Supplier<String> value) {
        if (logLevel.atLeast(LogLevel.INFO)) {
            messages.add(new StampedMessage(LogLevel.INFO, value.get()));
        }
    }

    /**
     * Adds an INFO level message with formatting, if INFO is enabled.
     *
     * @param message The message format string
     * @param args    The arguments for formatting
     */
    public void info(String message, Object... args) {
        info(() -> {
            Object[] formattedArgs = SLogger.format(args);
            return String.format(message, formattedArgs);
        });
    }

    /**
     * Returns true if DEBUG level messages are enabled.
     *
     * @return True if DEBUG is enabled
     */
    public boolean isDebugEnabled() {
        return logLevel.atLeast(LogLevel.DEBUG);
    }

    /**
     * Returns true if ERROR level messages are enabled.
     *
     * @return True if ERROR is enabled
     */
    public boolean isErrorEnabled() {
        return logLevel.atLeast(LogLevel.ERROR);
    }

    /**
     * Returns true if INFO level messages are enabled.
     *
     * @return True if INFO is enabled
     */
    public boolean isInfoEnabled() {
        return logLevel.atLeast(LogLevel.INFO);
    }

    /**
     * Returns true if TRACE level messages are enabled.
     *
     * @return True if TRACE is enabled
     */
    public boolean isTraceEnabled() {
        return logLevel.atLeast(LogLevel.TRACE);
    }

    /**
     * Returns true if the result value has been set. This allows us to distinguish
     * between a value that is unset and a value that is explicitly set to null.
     *
     * @return True if the value is set, false otherwise
     */
    public boolean isValueSet() {
        return valueSet;
    }

    /**
     * Returns true if WARN level messages are enabled.
     *
     * @return True if WARN is enabled
     */
    public boolean isWarnEnabled() {
        return logLevel.atLeast(LogLevel.WARN);
    }

    /**
     * Returns the result value, or the provided alternative if the value is not set.
     * @param other The alternative value to return if the result value is not set
     * @return The result value or the alternative
     */
    public T orElse(T other) {
        return valueSet ? value : other;
    }

    /**
     * Returns the result value, or throws the provided exception if the value is not set.
     * @param e The exception to throw if the result value is not set
     * @return The result value
     * @throws GeneralException if the result value is not set
     */
    public T orElseThrow(GeneralException e) throws GeneralException {
        if (valueSet) {
            return value;
        } else {
            throw e;
        }
    }

    /**
     * Sets the log level for this result.
     *
     * @param logLevel The log level to set
     */
    public final void setLogLevel(LogLevel logLevel) {
        this.logLevel = logLevel;
    }

    /**
     * Sets the log level based on a configuration key.
     * @param logLevelConfig The configuration key for the log level
     */
    public final void setLogLevel(String logLevelConfig) {
        Configuration systemConfig = Configuration.getSystemConfig();
        Map<String, Object> logLevels = Util.otom(systemConfig.get("IIQCommon.ResultValue.LogLevels"));

        if (logLevels != null && logLevels.containsKey(logLevelConfig)) {
            String levelStr = (String) logLevels.get(logLevelConfig);
            this.logLevel = LogLevel.valueOf(levelStr.toUpperCase());
        } else {
            this.logLevel = LogLevel.INFO;
        }
    }

    /**
     * Sets the result value.
     *
     * @param value The value to set
     */
    public final void setValue(T value) {
        this.value = value;
        this.valueSet = true;
    }

    /**
     * Adds a TRACE level message using a supplier, if TRACE is enabled.
     *
     * @param value The message supplier
     */
    public void trace(Supplier<String> value) {
        if (logLevel.atLeast(LogLevel.TRACE)) {
            messages.add(new StampedMessage(LogLevel.TRACE, value.get()));
        }
    }

    /**
     * Adds a TRACE level message with formatting, if TRACE is enabled.
     *
     * @param message The message format string
     * @param args    The arguments for formatting
     */
    public void trace(String message, Object... args) {
        trace(() -> {
            Object[] formattedArgs = SLogger.format(args);
            return String.format(message, formattedArgs);
        });
    }

    /**
     * Adds a WARN level message using a supplier, if WARN is enabled.
     *
     * @param value The message supplier
     */
    public void warn(Supplier<String> value) {
        if (logLevel.atLeast(LogLevel.WARN)) {
            messages.add(new StampedMessage(LogLevel.WARN, value.get()));
        }
    }

    /**
     * Adds a WARN level message with formatting, if WARN is enabled.
     *
     * @param message The message format string
     * @param args    The arguments for formatting
     */
    public void warn(String message, Object... args) {
        warn(() -> {
            Object[] formattedArgs = SLogger.format(args);
            return String.format(message, formattedArgs);
        });
    }
}
