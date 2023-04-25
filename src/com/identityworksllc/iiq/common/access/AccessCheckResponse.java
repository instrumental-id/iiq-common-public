package com.identityworksllc.iiq.common.access;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdJdkSerializers;
import com.identityworksllc.iiq.common.Mappable;
import com.identityworksllc.iiq.common.vo.LogLevel;
import com.identityworksllc.iiq.common.vo.StampedMessage;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.tools.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The output of {@link com.identityworksllc.iiq.common.ThingAccessUtils#checkThingAccess(AccessCheckInput)}.
 */
@JsonAutoDetect
public class AccessCheckResponse implements Mappable {
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
            log.debug(reason);
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
}
