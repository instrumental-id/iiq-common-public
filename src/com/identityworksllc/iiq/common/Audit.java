package com.identityworksllc.iiq.common;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.identityworksllc.iiq.common.logging.SLogger;
import com.identityworksllc.iiq.common.plugin.CommonPluginUtils;
import sailpoint.object.Attributes;
import sailpoint.object.AuditEvent;
import sailpoint.object.Identity;
import sailpoint.object.ProvisioningPlan;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.web.UserContext;

import javax.servlet.http.HttpServletRequest;

/**
 * Wraps an audit event in a try-with-resources block, allowing for easy creation
 * and saving of audit events. The audit event will be automatically persisted at
 * the end of the block unless {@link #cancel()} is invoked beforehand.
 *
 * The event will be saved in a private transaction to ensure that it is still
 * persisted even if the main operation is rolled back due to other errors.
 *
 * The {@link #close()} method will catch and log any exceptions that occur
 * during the persistence process so that they don't interfere with the main
 * flow of the application.
 *
 * The following fields can only be set once: action.
 *
 * For example:
 *
 * ```
 * try (Audit audit = new Audit("replaceUsername", "some_user", "support_person")) {
 *     audit.setString1(oldUsername);
 *     audit.setAttributeName("username");
 *     audit.setAttributeValue(newUsername);
 * }
 * ```
 *
 * @see AuditEvent
 */
public class Audit implements AutoCloseable {

    /**
     * The logger for this class
     */
    private static final SLogger log = new SLogger(Audit.class);

    /**
     * The custom attribute name for provisioning plans
     */
    public static final String ATTR_PLAN = "plan";

    /**
     * The wrapped AuditEvent that is populated and eventually saved to the database
     */
    private final AuditEvent ae;

    /**
     * Indicates that the audit event should not be saved when the block is exited, probably due to an error
     */
    private final AtomicBoolean canceled;

    /**
     * A thread-safe set of field names that have already been set on the AuditEvent, to enforce single-set semantics on certain fields like "action"
     */
    private final ConcurrentHashMap<String, Boolean> fieldsSet;

    /**
     * Constructs a new Audit object with the specified action. The server host will be automatically
     * set to the value of {@link Util#getHostName()}.
     *
     * @param action the value of 'action'
     */
    public Audit(String action) {
        this(action, null, null);
    }

    /**
     * Constructs a new Audit object with the specified action and target. The server host will
     * be automatically set to the value of {@link Util#getHostName()}.
     *
     * @param action the value of 'action'
     * @param target the value of 'target', often the noun going with the verb in 'action'
     */
    public Audit(String action, String target) {
        this(action, target, null);
    }

    /**
     * Constructs a new Audit object with the specified action, target, and source. The server host will
     * be automatically set to the value of {@link Util#getHostName()}.
     *
     * @param action the value of 'action'
     * @param target the value of 'target', often the noun going with the verb in 'action'
     * @param source the value of 'source', often an Identity name representing the initiator of the action
     */
    public Audit(String action, String target, String source) {
        AuditEvent auditEvent = new AuditEvent();
        auditEvent.setAction(action);
        auditEvent.setTarget(target);
        auditEvent.setSource(source);
        auditEvent.setServerHost(Util.getHostName());

        this.canceled = new AtomicBoolean(false);
        this.ae = auditEvent;
        this.fieldsSet = new ConcurrentHashMap<>();

        if (Util.isNotNullOrEmpty(action)) {
            this.fieldsSet.put("action", true);
        }
    }

    /**
     * Constructor that accepts an existing AuditEvent. If the provided AuditEvent is null, a new one will be created.
     * @param ae the AuditEvent to wrap, or null to create a new one
     */
    public Audit(AuditEvent ae) {
        this.ae = ae != null ? ae : new AuditEvent();
        this.canceled = new AtomicBoolean(false);
        this.fieldsSet = new ConcurrentHashMap<>();

        if (Util.isNotNullOrEmpty(this.ae.getAction())) {
            this.fieldsSet.put("action", true);
        }
    }

    /**
     * Marks the audit event as canceled, which will prevent it from being saved when the block is exited.
     * This should be called if the operation is rejected or otherwise short-circuited, to avoid saving
     * incomplete or inaccurate audit data.
     */
    public void cancel() {
        this.canceled.set(true);
    }

    /**
     * Saves the audit event to the database if it has not been canceled. This method will catch and log any exceptions
     * that occur during the save process, so that they don't interfere with the main flow of the application. The audit
     * event is saved in a private transaction to ensure it is persisted even if the main operation rolls back.
     */
    public void close() {
        if (!this.canceled.get()) {
            try {
                Utilities.withPrivateContext((ctx) -> {
                    ctx.saveObject(ae);
                    ctx.commitTransaction();
                });
            } catch(GeneralException e) {
                Map<String, Object> logMap = toLoggableMap();

                // We cannot guarantee that the toString() method will produce a safe-to-log
                // string containing no secrets, so we don't use it or any of the Attributes.

                log.error("Failed to save audit event", e);
                log.warn("Audit event that failed to save: {0}", logMap);
            }
        }
    }

    /**
     * Gets the name of the account affected by this audited action, if applicable
     * @return the name of the account affected by this audited action, or null if not applicable
     */
    public String getAccountName() {
        return ae.getAccountName();
    }

    /**
     * Gets the name of the audit action. This field is mandatory before saving and can only be set
     * once. Ideally, you should set it via the constructor.
     *
     * @return the name of the audit action
     */
    public String getAction() {
        return ae.getAction();
    }

    /**
     * Gets the name of the application associated with this audited action, if applicable
     * @return the name of the application associated with this audited action, or null if not applicable
     */
    public String getApplication() {
        return ae.getApplication();
    }

    /**
     * Reads the value of a custom attribute on the AuditEvent
     * @param name The name of the attribute to read
     * @return the value of the specified attribute, or null if it doesn't exist
     */
    public Object getAttribute(String name) {
        return ae.getAttribute(name);
    }

    /**
     * Gets the name of the attribute being affected by this audited action, if applicable
     * @return the name of the attribute being affected by this audited action, or null if not applicable
     */
    public String getAttributeName() {
        return ae.getAttributeName();
    }

    /**
     * Gets the value of the attribute (probably a new value) being affected by this audited action, if applicable
     * @return the value of the attribute being affected by this audited action, or null if not applicable
     */
    public String getAttributeValue() {
        return ae.getAttributeValue();
    }

    /**
     * Gets the map of custom attributes on the AuditEvent
     * @return the map of custom attributes on the AuditEvent
     */
    public Attributes<String, Object> getAttributes() {
        return ae.getAttributes();
    }

    /**
     * Returns the underlying AuditEvent wrapped by this helper. You should avoid using this if possible.
     * @return the underlying AuditEvent wrapped by this helper
     */
    public AuditEvent getAuditEvent() {
        return this.ae;
    }

    /**
     * Gets the host or IP address of the client that initiated this audited action
     * @return the host or IP address of the client, or null if not set
     */
    public String getClientHost() {
        return ae.getClientHost();
    }

    /**
     * Gets the 'instance' of an application being affected by this audited action, if applicable
     * @return the 'instance' of an application being affected by this audited action, or null if not applicable
     */
    public String getInstance() {
        return ae.getInstance();
    }

    /**
     * Gets the interface over which the audited action occurred (e.g., UI, API, Workflow, etc)
     * @return the interface over which the audited action occurred, or null if not applicable
     */
    public String getInterface() {
        return ae.getInterface();
    }

    /**
     * Gets the host on which this audited action took place, typically the value of {@link Util#getHostName()}.
     * @return the host on which this audited action took place, or null if not set
     */
    public String getServerHost() {
        return ae.getServerHost();
    }

    /**
     * Gets the source of the audited action, often an Identity name
     * @return the source of the audited action, or null if not set
     */
    public String getSource() {
        return ae.getSource();
    }

    /**
     * Fetches the value of a custom attribute as a string from the underlying AuditEvent
     * @param name The name of the custom attribute to fetch
     * @return the value of the specified custom attribute as a string
     */
    public String getString(String name) {
        return ae.getString(name);
    }

    /**
     * Gets the value of the 'string1' field on the AuditEvent, which is a generic field that can be used for
     * any purpose. The semantics of this field are not defined by IIQ and should be determined by convention within
     * your organization.
     *
     * @return The value of 'string1' on the AuditEvent, or null if not set
     */
    public String getString1() {
        return ae.getString1();
    }

    /**
     * Gets the value of the 'string2' field on the AuditEvent, which is a generic field that can be used for
     * any purpose. The semantics of this field are not defined by IIQ and should be determined by convention within
     * your organization.
     *
     * @return The value of 'string2' on the AuditEvent, or null if not set
     */
    public String getString2() {
        return ae.getString2();
    }

    /**
     * Gets the value of the 'string3' field on the AuditEvent, which is a generic field that can be used for
     * any purpose. The semantics of this field are not defined by IIQ and should be determined by convention within
     * your organization.
     *
     * @return The value of 'string3' on the AuditEvent, or null if not set
     */
    public String getString3() {
        return ae.getString3();
    }

    /**
     * Gets the value of the 'string4' field on the AuditEvent, which is a generic field that can be used for
     * any purpose. The semantics of this field are not defined by IIQ and should be determined by convention within
     * your organization.
     *
     * @return The value of 'string4' on the AuditEvent, or null if not set
     */
    public String getString4() {
        return ae.getString4();
    }

    /**
     * Gets the target of the audited action. Typically, this is the noun going with the verb in the 'action'
     * field. If this is a reference to an Identity, it ought to be an Identity name.
     *
     * It is rare for this field to NOT be set.
     *
     * @return the target of the audited action, or null if not set
     */
    public String getTarget() {
        return ae.getTarget();
    }

    /**
     * Gets the external tracking ID for the action (e.g., a ServiceNow ticket via an integration)
     *
     * @return the external tracking ID for the action, or null if not set
     */
    public String getTrackingId() {
        return ae.getTrackingId();
    }

    /**
     * Sets the name of the account affected by this audited action
     * @param accountName The name of the account to set
     */
    public void setAccountName(String accountName) {
        ae.setAccountName(accountName);
    }

    /**
     * Sets the name of the audit action. This field is mandatory before saving and can only be set
     * once. Ideally, you should set it via the constructor.
     *
     * @param action the name of the audit action to set
     * @throws IllegalStateException if the 'action' field has already been set on this AuditEvent, to enforce single-set semantics
     */
    public void setAction(String action) {
        if (fieldsSet.putIfAbsent("action", true) != null) {
            throw new IllegalStateException("The 'action' field can only be set once");
        }
        ae.setAction(action);
    }

    /**
     * Sets the name of the application associated with this audited action
     * @param app The name of the application to set
     */
    public void setApplication(String app) {
        ae.setApplication(app);
    }

    /**
     * Sets a custom attribute on the audit event. Attribute names are arbitrary strings, like
     * any other SailPoint {@link Attributes} usage. The value must be an object that IIQ can
     * serialize to XML.
     *
     * @param name The name of the attribute to set
     * @param value The value of the attribute to set
     */
    public void setAttribute(String name, Object value) {
        ae.setAttribute(name, value);
    }

    /**
     * Sets the name of the attribute being affected by this audited action
     * @param attribute The name of the attribute being affected by this audited action
     */
    public void setAttributeName(String attribute) {
        ae.setAttributeName(attribute);
    }

    /**
     * Sets the value of the attribute (probably a new value) being affected by this audited action
     * @param value The value of the attribute being affected by this audited action
     */
    public void setAttributeValue(String value) {
        ae.setAttributeValue(value);
    }

    /**
     * Replaces the AuditEvent's attributes with the given value
     * @param attr The attributes to set on the AuditEvent. If null, the attributes will be cleared from the event.
     */
    public void setAttributes(Attributes<String, Object> attr) {
        if (attr != null) {
            ae.setAttributes(new Attributes<>(attr));
        } else {
            ae.setAttributes(null);
        }
    }

    /**
     * Extracts the client host or IP address from the servlet request and logs it
     * @param request The HttpServletRequest to extract the client host from. If null, the client host will not be set.
     */
    public void setClientHost(HttpServletRequest request) {
        if (request != null) {
            String clientHost = CommonPluginUtils.getClientIP(request).orElse(null);
            setClientHost(clientHost);
        }
    }

    /**
     * Sets the host or IP address of the client that initiated this audited action
     * @param host The host or IP address to set
     */
    public void setClientHost(String host) {
        ae.setClientHost(host);
    }

    /**
     * Sets the 'instance' of an application being affected by this audited action, if applicable
     * @param instance The instance of the application to set
     */
    public void setInstance(String instance) {
        ae.setInstance(instance);
    }

    /**
     * Sets the interface over which this audited action occurred (e.g., UI, API, Workflow, etc)
     * @param s The interface to set
     */
    public void setInterface(String s) {
        ae.setInterface(s);
    }

    /**
     * Sets the provisioning plan associated with this audited action. The 'logging plan' will be
     * calculated to ensure that secret or sensitive data is not included. The plan will always be
     * set in the custom attribute 'plan'.
     *
     * @param plan The provisioning plan to set on the audit event
     */
    public void setProvisioningPlan(ProvisioningPlan plan) {
        if (plan != null) {
            ProvisioningPlan loggingPlan = ProvisioningPlan.getLoggingPlan(plan);
            ae.setAttribute(ATTR_PLAN, loggingPlan);
        }
    }

    /**
     * Sets the host on which this audited action took place
     * @param host The host to set
     */
    public void setServerHost(String host) {
        ae.setServerHost(host);
    }

    /**
     * Sets the source of the audited action, often an Identity name, based on the provided UserContext.
     * If the userContext is null, the source will be cleared.
     *
     * @param userContext The UserContext from which to extract the logged-in user's name to set as the source
     * @throws GeneralException if an error occurs while accessing the UserContext
     */
    public void setSource(UserContext userContext) throws GeneralException {
        if (userContext != null) {
            setSource(userContext.getLoggedInUserName());
        } else {
            setSource((String) null);
        }
    }

    /**
     * Sets the source of the audited action, often an Identity name
     * @param source The source to set
     */
    public void setSource(String source) {
        ae.setSource(source);
    }

    /**
     * Sets the value of the 'string1' field on the AuditEvent, which is a generic field that can be used for
     * any purpose. The semantics of this field are not defined by IIQ and should be determined by convention within
     * your organization.
     *
     * @param string The value to set for 'string1'
     */
    public void setString1(String string) {
        ae.setString1(string);
    }

    /**
     * Sets the value of the 'string2' field on the AuditEvent, which is a generic field that can be used for
     * any purpose. The semantics of this field are not defined by IIQ and should be determined by convention within
     * your organization.
     *
     * @param string The value to set for 'string2'
     */
    public void setString2(String string) {
        ae.setString2(string);
    }

    /**
     * Sets the value of the 'string3' field on the AuditEvent, which is a generic field that can be used for
     * any purpose. The semantics of this field are not defined by IIQ and should be determined by convention within
     * your organization.
     *
     * @param string The value to set for 'string3'
     */
    public void setString3(String string) {
        ae.setString3(string);
    }

    /**
     * Sets the value of the 'string4' field on the AuditEvent, which is a generic field that can be used for
     * any purpose. The semantics of this field are not defined by IIQ and should be determined by convention within
     * your organization.
     *
     * @param string The value to set for 'string4'
     */
    public void setString4(String string) {
        ae.setString4(string);
    }

    /**
     * Sets the target of the audited action based on the provided Identity. If the input
     * is null, the target will be cleared.
     *
     * @param target The Identity whose name will be set as the target of the audited action
     */
    public void setTarget(Identity target) {
        if (target != null) {
            setTarget(target.getName());
        } else {
            setTarget((String) null);
        }
    }

    /**
     * Sets the target of the audited action. Typically, this is the noun going with the verb in the 'action'
     * field. If this is a reference to an Identity, it ought to be an Identity name.
     *
     * @param s The target to set
     */
    public void setTarget(String s) {
        ae.setTarget(s);
    }

    /**
     * Sets the external tracking ID for the action (e.g., a ServiceNow ticket via an integration)
     *
     * @param trackingId The tracking ID to set
     */
    public void setTrackingId(String trackingId) {
        ae.setTrackingId(trackingId);
    }

    /**
     * Converts the most critical fields of the underlying AuditEvent to a {@link Map}, for logging if
     * persistence fails or for other purposes.
     *
     * @return a Map containing the most critical fields of the underlying AuditEvent, suitable for logging
     */
    public Map<String, Object> toLoggableMap() {
        Map<String, Object> logMap = new HashMap<>();
        logMap.put("action", ae.getAction());
        logMap.put("target", ae.getTarget());
        logMap.put("source", ae.getSource());
        logMap.put("string1", ae.getString1());
        logMap.put("string2", ae.getString2());
        logMap.put("string3", ae.getString3());
        logMap.put("string4", ae.getString4());
        logMap.put("clientHost", ae.getClientHost());
        logMap.put("serverHost", ae.getServerHost());
        logMap.put("account", ae.getAccountName());
        logMap.put("application", ae.getApplication());
        return logMap;
    }

    /**
     * Returns a string representation of this Audit object. This implementation delegates to the underlying
     * AuditEvent's toString() method.
     *
     * @return a string representation of this Audit object
     */
    @Override
    public String toString() {
        return ae.toString();
    }

    /**
     * Converts the underlying AuditEvent to an XML string, useful for debugging or other purposes.
     *
     * @return an XML string representation of the underlying AuditEvent
     * @throws GeneralException if an error occurs during XML conversion
     * @see AbstractXmlObject#toXml()
     */
    public String toXml() throws GeneralException {
        return ae.toXml();
    }

}
