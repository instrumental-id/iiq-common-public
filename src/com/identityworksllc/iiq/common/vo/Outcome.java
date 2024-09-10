package com.identityworksllc.iiq.common.vo;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.base.Objects;
import org.apache.commons.lang3.builder.ToStringBuilder;
import sailpoint.tools.Message;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * A generic class to represent (and perhaps log) some operation outcome.
 * It contains fields for generic start/stop tracking, as well as fields
 * for a slew of output and logging indicators.
 *
 * This class is not intended to be used in any particular way. It is used
 * for various purposes throughout Instrumental ID's codebase.
 *
 * This class implements {@link AutoCloseable} so that it can be used in the
 * following sort of structure:
 *
 * ```
 * Outcome outcome;
 *
 * try(outcome = Outcome.start()) {
 *     // Do things, recording the outcome
 * }
 *
 * // Your outcome will have a proper start/stop time for that block here
 * ```
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Outcome implements AutoCloseable, Serializable {

    /**
     * Create and start a new Outcome object. This is intended to be used
     * in conjunction with the try-with-resources and AutoClosable function
     * to start/stop your Outcome along with its operation.
     * @return A started outcome
     */
    public static Outcome start() {
        Outcome outcome = new Outcome();
        outcome.startTimeMillis = outcome.created;
        return outcome;
    }

    /**
     * The name of an application associated with this outcome
     */
    private String applicationName;

    /**
     * The name of an attribute associated with this outcome
     */
    private String attribute;

    /**
     * The millisecond timestamp at which this object was created
     */
    private final long created;

    /**
     * The name of an identity associated with this outcome
     */
    private String identityName;

    /**
     * The list of timestampped messages logged for this outcome
     */
    private final List<StampedMessage> messages;

    /**
     * A native identity associated with this outcome
     */
    private String nativeIdentity;

    /**
     * An arbitrary object ID associated with this outcome
     */
    private String objectId;

    /**
     * An arbitrary object name associated with this outcome
     */
    private String objectName;

    /**
     * The type of the object ID or name above
     */
    private String objectType;

    /**
     * The provisioning transaction associated with this outcome
     */
    private String provisioningTransaction;

    /**
     * An indicator that something has been refreshed
     */
    private Boolean refreshed;

    /**
     * A response code, intended when this is used for reporting an HTTP response
     */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private Integer responseCode;

    /**
     * The start time in milliseconds
     */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private long startTimeMillis;

    /**
     * The status of this operation
     */
    private OutcomeType status;

    /**
     * The stop time in milliseconds
     */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private long stopTimeMillis;

    /**
     * Arbitrary text associated with this operation
     */
    private String text;

    /**
     * A flag indicating that something was updated
     */
    private Boolean updated;

    /**
     * Arbitrary value associated with this operation (presumably of the named attribute)
     */
    private String value;

    /**
     * Basic constructor, also used by Jackson to figure out what the 'default' for each
     * item in the class is.
     */
    public Outcome() {
        this.created = System.currentTimeMillis();
        this.stopTimeMillis = -1L;
        this.startTimeMillis = -1L;
        this.messages = new ArrayList<>();
    }

    /**
     * Adds a timestamped error to this outcome
     * @param input A string message to use with the error
     * @param err The error itself
     */
    public void addError(String input, Throwable err) {
        this.messages.add(new StampedMessage(input, err));
    }

    /**
     * Adds a timestamped IIQ message to this outcome
     * @param input The IIQ outcome
     */
    public void addMessage(Message input) {
        this.messages.add(new StampedMessage(input));
    }

    /**
     * Adds a timestamped log message of INFO level to this outcome
     * @param input The log message
     */
    public void addMessage(String input) {
        this.messages.add(new StampedMessage(input));
    }

    /**
     * If the outcome has not yet had its stop timestamp set, sets it to the current time
     */
    @Override
    public void close() {
        if (this.stopTimeMillis < 0) {
            this.stopTimeMillis = System.currentTimeMillis();
        }
    }

    public String getApplicationName() {
        return applicationName;
    }

    public String getAttribute() {
        return attribute;
    }

    public long getCreated() {
        return created;
    }

    public String getIdentityName() {
        return identityName;
    }

    public List<StampedMessage> getMessages() {
        return messages;
    }

    public String getNativeIdentity() {
        return nativeIdentity;
    }

    public String getObjectId() {
        return objectId;
    }

    public String getObjectName() {
        return objectName;
    }

    public String getObjectType() {
        return objectType;
    }

    public String getProvisioningTransaction() {
        return provisioningTransaction;
    }

    public int getResponseCode() {
        return responseCode != null ? responseCode : 0;
    }

    public long getStartTimeMillis() {
        return startTimeMillis;
    }

    /**
     * Gets the status, or calculates it based on other fields.
     *
     * If there is no status explicitly set and the stop time is not set, returns null.
     *
     * @return The status, or null if the outcome is not yet stopped
     */
    public OutcomeType getStatus() {
        if (status != null) {
            return status;
        } else if (this.stopTimeMillis < 0) {
            return null;
        } else {
            boolean hasError = this.messages.stream().anyMatch(msg -> msg.getLevel() == LogLevel.ERROR);
            if (hasError) {
                return OutcomeType.Failure;
            }
            boolean hasWarning = this.messages.stream().anyMatch(msg -> msg.getLevel() == LogLevel.WARN);
            if (hasWarning) {
                return OutcomeType.Warning;
            }
            return OutcomeType.Success;
        }
    }

    public long getStopTimeMillis() {
        return stopTimeMillis;
    }

    public String getText() {
        return text;
    }

    public String getValue() {
        return value;
    }

    public boolean isRefreshed() {
        return refreshed != null && refreshed;
    }

    public boolean isUpdated() {
        return updated != null && updated;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public void setAttribute(String attribute) {
        this.attribute = attribute;
    }

    public void setIdentityName(String identityName) {
        this.identityName = identityName;
    }

    public void setNativeIdentity(String nativeIdentity) {
        this.nativeIdentity = nativeIdentity;
    }

    public void setObjectId(String objectId) {
        this.objectId = objectId;
    }

    public void setObjectName(String objectName) {
        this.objectName = objectName;
    }

    public void setObjectType(String objectType) {
        this.objectType = objectType;
    }

    public void setProvisioningTransaction(String provisioningTransaction) {
        this.provisioningTransaction = provisioningTransaction;
    }

    public void setRefreshed(Boolean refreshed) {
        this.refreshed = refreshed;
    }

    public void setResponseCode(Integer responseCode) {
        this.responseCode = responseCode;
    }

    public void setStartTimeMillis(long startTimeMillis) {
        this.startTimeMillis = startTimeMillis;
    }

    public void setStatus(OutcomeType status) {
        this.status = status;
    }

    public void setStopTimeMillis(long stopTimeMillis) {
        this.stopTimeMillis = stopTimeMillis;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setUpdated(Boolean updated) {
        this.updated = updated;
    }

    public void setValue(String value) {
        this.value = value;
    }

    /**
     * Sets the status to Terminated and the stop time to the current timestamp
     */
    public void terminate() {
        this.status = OutcomeType.Terminated;
        this.stopTimeMillis = System.currentTimeMillis();
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(", ", Outcome.class.getSimpleName() + "[", "]");
        if ((applicationName) != null) {
            joiner.add("applicationName='" + applicationName + "'");
        }
        if ((attribute) != null) {
            joiner.add("attribute='" + attribute + "'");
        }
        joiner.add("created=" + created);
        if ((identityName) != null) {
            joiner.add("identityName='" + identityName + "'");
        }
        if ((messages) != null) {
            joiner.add("messages=" + messages);
        }
        if ((nativeIdentity) != null) {
            joiner.add("nativeIdentity='" + nativeIdentity + "'");
        }
        if ((objectId) != null) {
            joiner.add("objectId='" + objectId + "'");
        }
        if ((objectName) != null) {
            joiner.add("objectName='" + objectName + "'");
        }
        if ((objectType) != null) {
            joiner.add("objectType='" + objectType + "'");
        }
        if ((provisioningTransaction) != null) {
            joiner.add("provisioningTransaction='" + provisioningTransaction + "'");
        }
        joiner.add("refreshed=" + refreshed);
        if (responseCode != null && responseCode > 0) {
            joiner.add("responseCode=" + responseCode);
        }
        joiner.add("startTimeMillis=" + startTimeMillis);
        if ((status) != null) {
            joiner.add("status=" + status);
        }
        joiner.add("stopTimeMillis=" + stopTimeMillis);
        if ((text) != null) {
            joiner.add("text='" + text + "'");
        }
        joiner.add("updated=" + updated);
        if ((value) != null) {
            joiner.add("value='" + value + "'");
        }
        return joiner.toString();
    }
}
