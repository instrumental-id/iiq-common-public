package com.identityworksllc.iiq.common;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.identityworksllc.iiq.common.vo.Outcome;
import com.identityworksllc.iiq.common.vo.OutcomeType;
import sailpoint.api.SailPointContext;
import sailpoint.object.TaskResult;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;

import java.util.StringJoiner;

/**
 * A data class for returning the outcome of the aggregation event
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class AggregationOutcome extends Outcome {
    /**
     * An optional error message (can be null);
     */
    private String errorMessage;

    /**
     * Aggregator TaskResults outputs
     */
    @JsonIgnore
    private String serializedTaskResult;

    /**
     * Default construct, to be used by Jackson to determine field defaults
     */
    @Deprecated
    public AggregationOutcome() {
        this(null, null);
    }

    /**
     * @param appl The application
     * @param ni   The native Identity
     */
    public AggregationOutcome(String appl, String ni) {
        this(appl, ni, null, null);
    }

    /**
     * @param appl The application
     * @param ni   The native Identity
     * @param o    The aggregation outcome
     */
    public AggregationOutcome(String appl, String ni, OutcomeType o) {
        this(appl, ni, o, null);
    }

    /**
     * @param appl         The application
     * @param ni           The native Identity
     * @param o            The aggregation outcome
     * @param errorMessage The error message, if any
     */
    public AggregationOutcome(String appl, String ni, OutcomeType o, String errorMessage) {
        setApplicationName(appl);
        setNativeIdentity(ni);
        if (o != null) {
            setStatus(o);
        }
        if (Util.isNotNullOrEmpty(errorMessage)) {
            addMessage(Message.error(errorMessage));
            this.errorMessage = errorMessage;
        }
    }

    /**
     * @return The error message
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Gets the serialized TaskResult object
     * @return The serialized TaskResult XML
     */
    @JsonIgnore
    public String getSerializedTaskResult() {
        return serializedTaskResult;
    }

    /**
     * Gets the aggregation outputs, if any
     * @param context The IIQ contxt
     * @return the aggregator's TaskResult object, possibly null
     */
    @JsonIgnore
    public TaskResult getTaskResult(SailPointContext context) throws GeneralException {
        if (Util.isNullOrEmpty(serializedTaskResult)) {
            return null;
        }
        return (TaskResult) AbstractXmlObject.parseXml(context, this.serializedTaskResult);
    }

    /**
     * @param taskResult The outputs from the aggregation job
     */
    public void setTaskResult(TaskResult taskResult) throws GeneralException {
        this.serializedTaskResult = taskResult.toXml();
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(", ", AggregationOutcome.class.getSimpleName() + "[", "]");
        if ((errorMessage) != null) {
            joiner.add("errorMessage='" + errorMessage + "'");
        }
        if ((serializedTaskResult) != null) {
            joiner.add("serializedTaskResult='" + serializedTaskResult + "'");
        }
        joiner.add(super.toString());
        return joiner.toString();
    }
}
