package com.identityworksllc.iiq.common.access;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdJdkSerializers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.identityworksllc.iiq.common.Mappable;
import com.identityworksllc.iiq.common.Utilities;
import com.identityworksllc.iiq.common.vo.LogLevel;
import com.identityworksllc.iiq.common.vo.StampedMessage;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.MessageAccumulator;
import sailpoint.tools.Message;
import sailpoint.tools.Util;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * The output of {@link AccessCheck#accessCheck(AccessCheckInput)}, containing the
 * results of the access check (allowed or not) and some metadata.
 *
 * This object can be safely serialized to JSON.
 */
@JsonAutoDetect
public class AccessCheckResponse implements Mappable, Consumer<Boolean>, BiConsumer<Boolean, String>, MessageAccumulator {
    /**
     * A logger used to record errors
     */
    private static final Log log = LogFactory.getLog(AccessCheckResponse.class);
    /**
     * Whether the access was allowed
     */
    @JsonSerialize(using = StdJdkSerializers.AtomicBooleanSerializer.class)
    private final AtomicBoolean allowed;
    /**
     * Any output messages from the access check
     */
    private final List<StampedMessage> messages;
    /**
     * The timestamp of the check
     */
    private final long timestamp;

    /**
     * Basic constructor used by actual users of this class
     */
    public AccessCheckResponse() {
        this.timestamp = System.currentTimeMillis();
        this.allowed = new AtomicBoolean(true);
        this.messages = new ArrayList<>();
    }

    /**
     * Jackson-specific constructor. Don't use this one unless you're a JSON library.
     *
     * @param allowed The value of the allowed flag
     * @param messages The messages (possibly null) to add to a new empty list
     * @param timestamp The timestamp from the JSON
     */
    @JsonCreator
    public AccessCheckResponse(@JsonProperty("allowed") boolean allowed, @JsonProperty("messages") List<StampedMessage> messages, @JsonProperty(value = "timestamp", defaultValue = "0") long timestamp) {
        this.allowed = new AtomicBoolean(allowed);
        this.messages = new ArrayList<>();

        if (messages != null) {
            this.messages.addAll(messages);
        }

        if (timestamp == 0) {
            this.timestamp = System.currentTimeMillis();
        } else {
            this.timestamp = timestamp;
        }
    }

    /**
     * Decodes an AccessCheckResponse from the given String, which should be a JSON
     * formatted value.
     *
     * @param input The input JSON
     * @return The decoded AccessCheckResponse
     * @throws IOException if JSON decoding fails
     */
    public static AccessCheckResponse decode(String input) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(new StringReader(input), AccessCheckResponse.class);
    }

    /**
     * Decodes an AccessCheckResponse from the given Map, which may have been generated using
     * this class's {@link #toMap()}. The Map may contain a long 'timestamp', a set of 'messages',
     * and an 'allowed' boolean.
     * @param input The Map input
     * @return The decoded AccessCheckResponse
     */
    public static AccessCheckResponse decode(Map<String, Object> input) {
        long timestamp = System.currentTimeMillis();
        if (input.get("timestamp") instanceof Long) {
            timestamp = (Long)input.get("timestamp");
        }

        List<StampedMessage> messages = new ArrayList<>();
        if (input.get("messages") instanceof List) {
            for(Object o : Util.asList(input.get("messages"))) {
                if (o instanceof StampedMessage) {
                    messages.add((StampedMessage)o);
                } else if (o instanceof Message) {
                    messages.add(new StampedMessage((Message)o));
                } else if (o instanceof String) {
                    messages.add(new StampedMessage((String)o));
                } else {
                    log.debug("Unrecognized object type in 'messages' List: " + Utilities.safeClassName(o));
                }
            }
        }

        boolean result = Util.otob(input.get("allowed"));

        return new AccessCheckResponse(result, messages, timestamp);
    }

    /**
     * A functional interface used to deny access to this thing, if your code
     * happens to be called from a strange context.
     *
     * @param status True if access is allowed, false otherwise
     */
    @Override
    public void accept(Boolean status) {
        if (status != null) {
            if (status) {
                this.allowed.set(true);
            } else {
                this.allowed.set(false);
            }
        }
    }

    /**
     * A functional interface used to deny access to this thing, with a message
     * @param status True if access is allowed, false otherwise
     * @param message A 'deny' message to be used in the deny case
     */
    @Override
    public void accept(Boolean status, String message) {
        if (status != null) {
            if (status) {
                this.allowed.set(true);
                addMessage(message);
            } else {
                denyMessage(message);
            }
        }
    }

    /**
     * Adds a message to the collection
     * @param message The message to add
     */
    public void addMessage(String message) {
        this.messages.add(new StampedMessage(message));
    }

    /**
     * Adds a message to the collection
     * @param message The message to add
     */
    public void addMessage(Message message) {
        this.messages.add(new StampedMessage(message));
    }

    /**
     * Denies access to the thing, setting the allowed flag to false
     */
    public void deny() {
        this.allowed.set(false);
    }

    /**
     * Denies access to the thing, additionally logging a message indicating the denial reason
     * @param reason the denial reason
     */
    public void denyMessage(String reason) {
        this.messages.add(new StampedMessage(LogLevel.WARN, reason));
        deny();
        if (log.isDebugEnabled()) {
            log.debug("Access denied: " + reason);
        }
    }

    /**
     * Gets the stored messages
     * @return The stored messages
     */
    public List<StampedMessage> getMessages() {
        return messages;
    }

    /**
     * Gets the timestamp
     * @return The timestamp
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Returns true if the access was allowed
     * @return True if access was allowed
     */
    public boolean isAllowed() {
        return this.allowed.get();
    }

    /**
     * Merges this response with another response. If either response indicates that
     * access is not allowed, then it will be set to false in this object. Also, messages
     * will be merged.
     *
     * @param other The other object to merge
     */
    public void merge(AccessCheckResponse other) {
        if (!other.allowed.get()) {
            deny();
        }

        messages.addAll(other.messages);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", AccessCheckResponse.class.getSimpleName() + "[", "]")
                .add("allowed=" + allowed)
                .add("messages=" + messages)
                .add("timestamp=" + timestamp)
                .toString();
    }
}
