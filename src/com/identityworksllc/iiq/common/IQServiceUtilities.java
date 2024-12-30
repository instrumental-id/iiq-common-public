package com.identityworksllc.iiq.common;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.connector.RPCService;
import sailpoint.object.*;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Utilities for interacting with the IQService, particularly for executing Powershell
 */
public class IQServiceUtilities {
    /**
     * A fluent builder for {@link RpcRequest} objects
     */
    @SuppressWarnings("javadoc")
    public static class RPCRequestBuilder {
        /**
         * The connection info
         */
        private Map<String, Object> connectionInfo;

        /**
         * The account request to pass to the Powershell script
         */
        private ProvisioningPlan.AccountRequest inputs;

        /**
         * The name of the RPC service to call, usually ScriptExecutor
         */
        private String rpcService;

        /**
         * The Rule to serialize and execute, often constructed ad hoc
         */
        private Rule rule;

        /**
         * The rule template to use if {@link #withCommands(String)} is used.
         */
        private String template;

        private RPCRequestBuilder() {
            this.rpcService = SCRIPT_EXECUTOR;
        }

        /**
         * Creates a new {@link RPCRequestBuilder} object
         * @return A new RPCRequestBuilder, ready for fluent building
         */
        public static RPCRequestBuilder builder() {
            return new RPCRequestBuilder();
        }

        /**
         * Builds a new RpcRequest using the inputs present, if enough are available.
         * If the inputs are not correct, throws an exception.
         *
         * @return the new RpcRequest
         * @throws GeneralException if input parameters are invalid or the creation of the request fails
         */
        public RpcRequest build() throws GeneralException {
            if (this.inputs == null) {
                throw new GeneralException("The builder was not supplied with input parameters or an AccountRequest");
            }
            if (this.rule == null) {
                throw new GeneralException("The builder was not supplied with a Rule to execute");
            }
            if (this.connectionInfo == null || this.connectionInfo.isEmpty()) {
                throw new GeneralException("The builder was not supplied with populated connection parameters");
            }
            if (!this.connectionInfo.containsKey(RPCService.IQSERVICE_CONFIGURATION) && !this.connectionInfo.containsKey(RPCService.CONFIG_IQSERVICE_HOST)) {
                throw new GeneralException("The connection info is not valid IQService connection info; must contain either " + RPCService.IQSERVICE_CONFIGURATION + " or " + RPCService.CONFIG_IQSERVICE_HOST);
            }
            return new RpcRequest(this.rpcService, RUN_AFTER_SCRIPT, createRPCRequestContent(this.rule, this.connectionInfo, this.inputs));
        }

        public RPCRequestBuilder withCommands(String commands, String template) throws GeneralException {
            return withTemplate(template).withRule(wrapPowershellCommands(commands, this.template));
        }

        public RPCRequestBuilder withCommands(String commands) throws GeneralException {
            return withRule(wrapPowershellCommands(commands, this.template));
        }

        public RPCRequestBuilder withConnectionInfo(Map<String, Object> connectionInfo) {
            this.connectionInfo = connectionInfo;
            return this;
        }

        public RPCRequestBuilder withConnectionInfo(Application application) {
            return withConnectionInfo(application.getAttributes());
        }

        public RPCRequestBuilder withInputs(ProvisioningPlan.AccountRequest inputs) {
            this.inputs = inputs;
            return this;
        }

        public RPCRequestBuilder withInputs(Map<String, Object> inputs) {
            return withInputs(createFakeAccountRequest(inputs));
        }

        public RPCRequestBuilder withRPCService(String serviceName) {
            this.rpcService = serviceName;
            return this;
        }

        public RPCRequestBuilder withRule(Rule rule) {
            this.rule = rule;
            return this;
        }

        public RPCRequestBuilder withTemplate(String template) {
            this.template = template;
            return this;
        }
    }
    /**
     * The 'hidden' input to disable hostname verification
     */
    public static final String CONFIG_DISABLE_HOSTNAME_VERIFICATION = "disableHostnameVerification";
    /**
     * The output variable returned by the default template
     */
    public static final String DEFAULT_TEMPLATE_OUTPUT = "output";
    /**
     * The input argument containing the Application's attributes
     */
    public static final String IQSERVICE_FIELD_APPLICATION = "Application";
    /**
     * The input argument containing an {@link sailpoint.object.ProvisioningPlan.AccountRequest}
     */
    public static final String IQSERVICE_FIELD_REQUEST = "Request";
    /**
     * The input argument containing the actual Powershell script to run
     */
    public static final String IQSERVICE_FIELD_RULE = "postScript";
    /**
     * The default Powershell script type
     */
    public static final String RUN_AFTER_SCRIPT = "runAfterScript";
    /**
     * The default RPC Service type, execution of a Powershell script
     */
    public static final String SCRIPT_EXECUTOR = "ScriptExecutor";

    /**
     * The path to the standard powershell template, which should be included with this library
     */
    public static final String STANDARD_POWERSHELL_TEMPLATE = "/powershell.template.ps1";
    /**
     * The token in the wrapper script to replace with the user commands
     */
    public static final String TOKEN_USER_COMMAND = "%%USER_COMMAND%%";
    /**
     * Logger
     */
    private static final Log log = LogFactory.getLog(IQServiceUtilities.class);
    /**
     * The cached static result of the {@link #supportsTLS()} check
     */
    private static final AtomicBoolean supportsTLS;

    static {
        boolean result = false;
        try {
            Constructor<RPCService> ignored = RPCService.class.getConstructor(String.class, Integer.TYPE, Boolean.TYPE, Boolean.TYPE);
            result = true;
        } catch(Exception e) {
            /* Ignore */
        }
        supportsTLS = new AtomicBoolean(result);
    }

    /**
     * Checks the {@link RpcResponse} for error messages. If there are any, it throws an exception with the messages.
     * If the RpcResponse contains any messages, they will be logged as warnings.
     *
     * The default logger will be used. If you wish to provide your own logger, use the two-argument
     * {@link #checkRpcFailure(RpcResponse, Log)}.
     *
     * @param response the response to check
     * @return the same response
     * @throws GeneralException if there were any errors
     */
    protected static RpcResponse checkRpcFailure(RpcResponse response) throws GeneralException {
        return checkRpcFailure(response, log);
    }

    /**
     * Checks the RpcResponse for error messages. If there are any, it throws an exception with the messages.
     * If the RpcResponse contains any messages, they will be logged as warnings.
     *
     * @param response the response to check
     * @param yourLogger The logger ot which the errors should be logged
     * @return the same response
     * @throws GeneralException if there were any errors
     */
    public static RpcResponse checkRpcFailure(RpcResponse response, Log yourLogger) throws GeneralException {
        if (response == null) {
            return null;
        }
        if (response.getErrors() != null && !response.getErrors().isEmpty()) {
            if (yourLogger != null) {
                yourLogger.error("Received errors from the IQService: " + response.getErrors());
            } else {
                log.error("Received errors from the IQService: " + response.getErrors());
            }
            throw new GeneralException("Errors from IQService: " + response.getErrors().toString());
        }
        if (response.getMessages() != null) {
            for(String message : response.getMessages()) {
                yourLogger.warn("Received a message from the IQService: " + message);
            }
        }
        return response;
    }

    /**
     * Constructs a fake account request to use for a call to IIQ
     *
     * @param parameters Any parameters to send to the IIQ call
     * @return The account request
     */
    public static ProvisioningPlan.AccountRequest createFakeAccountRequest(Map<String, Object> parameters) {
        ProvisioningPlan.AccountRequest accountRequest = new ProvisioningPlan.AccountRequest();
        accountRequest.setApplication("PSUTIL");
        accountRequest.setNativeIdentity("*FAKE*");
        accountRequest.setOperation(ProvisioningPlan.AccountRequest.Operation.Modify);
        List<ProvisioningPlan.AttributeRequest> fakeAttributeRequests = new ArrayList<ProvisioningPlan.AttributeRequest>();
        ProvisioningPlan.AttributeRequest attributeRequest = new ProvisioningPlan.AttributeRequest();
        attributeRequest.setName("CmdletResponse");
        attributeRequest.setOperation(ProvisioningPlan.Operation.Add);
        attributeRequest.setValue("");
        fakeAttributeRequests.add(attributeRequest);
        if (parameters != null) {
            for (String key : parameters.keySet()) {
                ProvisioningPlan.AttributeRequest fakeAttribute = new ProvisioningPlan.AttributeRequest();
                fakeAttribute.setOperation(ProvisioningPlan.Operation.Add);
                fakeAttribute.setName(key);
                fakeAttribute.setValue(parameters.get(key));
                fakeAttributeRequests.add(fakeAttribute);
            }
        }
        accountRequest.setAttributeRequests(fakeAttributeRequests);

        if (log.isTraceEnabled()) {
            log.trace("For input arguments " + parameters + ", produced output " + Utilities.safeToXml(accountRequest));
        }

        return accountRequest;
    }

    /**
     * Creates a map to be passed to an RPCRequest.
     *
     * @param rule The rule to execute
     * @param connectionInfo The Application containing connection into
     * @param inputs The rule inputs
     * @return The populated Map
     */
    public static Map<String, Object> createRPCRequestContent(Rule rule, Application connectionInfo, Map<String, Object> inputs) {
        return createRPCRequestContent(rule, connectionInfo.getAttributes(), inputs);
    }

    /**
     * Creates a map to be passed to an RPCRequest.
     *
     * @param rule The rule to execute
     * @param connectionInfo The Map containing connection into
     * @param inputs The rule inputs
     * @return The populated Map
     */
    public static Map<String, Object> createRPCRequestContent(Rule rule, Map<String, Object> connectionInfo, ProvisioningPlan.AccountRequest inputs) {
        Map<String, Object> dataMap = new HashMap<>();

        dataMap.put(IQSERVICE_FIELD_RULE, rule);
        dataMap.put(IQSERVICE_FIELD_APPLICATION, connectionInfo);
        dataMap.put(IQSERVICE_FIELD_REQUEST, inputs);

        return dataMap;
    }

    /**
     * Creates a map to be passed to an RPCRequest.
     *
     * @param rule The rule to execute
     * @param connectionInfo The Map containing connection into
     * @param inputs The rule inputs
     * @return The populated Map
     */
    public static Map<String, Object> createRPCRequestContent(Rule rule, Map<String, Object> connectionInfo, Map<String, Object> inputs) {
        return createRPCRequestContent(rule, connectionInfo, createFakeAccountRequest(inputs));
    }

    /**
     * Creates a map to be passed to an RPCRequest.
     *
     * @param commands The Powershell commands to execute; will be translated via {@link #wrapPowershellCommands(String, String)}
     * @param connectionInfo The Application containing connection into
     * @param inputs The rule inputs
     * @return The populated Map
     * @throws GeneralException if constructing request input fails, usually because of failure to read the template
     */
    public static Map<String, Object> createRPCRequestContent(String commands, Application connectionInfo, Map<String, Object> inputs) throws GeneralException {
        return createRPCRequestContent(wrapPowershellCommands(commands, null), connectionInfo.getAttributes(), inputs);
    }

    /**
     * Constructs an RPCService from the connection info provided
     *
     * @param connectionInfo The connection info from an Application or other config
     * @return The resulting RPCService
     */
    public static RPCService createRPCService(Map<String, Object> connectionInfo) {
        RPCService service;

        Map<String, Object> iqServiceConfig = findIQServiceConfig(connectionInfo);
        boolean ignoreHostnameVerification = Util.otob(iqServiceConfig.get(CONFIG_DISABLE_HOSTNAME_VERIFICATION));
        boolean useTLS = Util.otob(iqServiceConfig.get(RPCService.CONFIG_IQSERVICE_TLS));

        String iqServiceHost = Util.otoa(iqServiceConfig.get(RPCService.CONFIG_IQSERVICE_HOST));
        int iqServicePort = Util.otoi(iqServiceConfig.get(RPCService.CONFIG_IQSERVICE_PORT));

        if (log.isDebugEnabled()) {
            log.debug("Creating new RPCService for host = " + iqServiceHost + ", port = " + iqServicePort);
        }

        // If you pass true, we're going to assume you know what you're talking about
        if (ignoreHostnameVerification || useTLS || supportsTLS()) {
            service = new RPCService(iqServiceHost, iqServicePort, false, useTLS, ignoreHostnameVerification);
            service.setConnectorServices(new sailpoint.connector.DefaultConnectorServices());
        } else {
            service = new RPCService(iqServiceHost, iqServicePort, false);
        }
        return service;
    }

    /**
     * Finds the IQService config. This is isolated here so that we can ignore warnings on
     * as small a bit of code as possible
     * @param connectionInfo The connection info from the application or other config
     * @return The IQService config, either the original, or extracted
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> findIQServiceConfig(Map<String, Object> connectionInfo) {
        Map<String, Object> iqServiceConfig = connectionInfo;
        if (connectionInfo.get(RPCService.IQSERVICE_CONFIGURATION) instanceof Map) {
            iqServiceConfig = (Map<String, Object>) connectionInfo.get(RPCService.IQSERVICE_CONFIGURATION);
        }
        return iqServiceConfig;
    }

    /**
     * Get standard template from the classpath
     * @return The standard template
     * @throws IOException If any errors occur opening the template stream
     * @throws GeneralException If any errors occur parsing the template stream
     */
    public static String getStandardTemplate() throws IOException, GeneralException {
        try(InputStream templateStream = IQServiceUtilities.class.getResourceAsStream(STANDARD_POWERSHELL_TEMPLATE)) {
            if (templateStream != null) {
                return Util.readInputStream(templateStream);
            }
        }
        return null;
    }

    /**
     * Returns true if the RPCService in this instance of IIQ supports TLS.
     *
     * The TLS four and five-argument constructors to IQService are available
     * in the following IIQ versions and higher:
     *
     *  - 8.0 GA
     *  - 7.3p3
     *  - 7.2p4
     *  - 7.1p7
     *
     * This result is cached.
     *
     * @return True if it supports TLS, false otherwise
     */
    public static boolean supportsTLS() {
        return supportsTLS.get();
    }

    /**
     * Wraps Powershell commands into a Rule, substituting it into a template wrapper.
     * This {@link Rule} should not be saved, but should be serialized directly with
     * the input to the RPCService.
     *
     * @param commands The commands to execute in Powershell
     * @param ruleTextTemplate The rule text template, or null to use the included default
     * @return The resulting Rule object
     * @throws GeneralException if constructing the new Rule fails
     */
    public static Rule wrapPowershellCommands(String commands, String ruleTextTemplate) throws GeneralException {
        String finalRuleText;
        try {
            String template = ruleTextTemplate;
            if (Util.isNullOrEmpty(template)) {
                template = getStandardTemplate();
            }
            if (Util.isNullOrEmpty(template)) {
                throw new IllegalArgumentException("No Powershell template is available");
            }
            finalRuleText = template.replace(TOKEN_USER_COMMAND, commands);
        } catch(IOException e) {
            throw new GeneralException("Could not read Powershell template", e);
        }
        if (Util.isNotNullOrEmpty(finalRuleText)) {
            Rule pretendPowershellRule = new Rule();
            pretendPowershellRule.setName("Powershell Rule Template Wrapper " + System.currentTimeMillis());
            pretendPowershellRule.setSource(finalRuleText);
            pretendPowershellRule.setAttribute("ObjectOrientedScript", "true");
            pretendPowershellRule.setAttribute("disabled", "false");
            pretendPowershellRule.setAttribute("extension", ".ps1");
            pretendPowershellRule.setAttribute("program", "powershell.exe");
            pretendPowershellRule.setAttribute("powershellTimeout", 10);

            if (log.isTraceEnabled()) {
                log.trace("Transformed commands into Rule: " + pretendPowershellRule.toXml());
            }

            return pretendPowershellRule;
        } else {
            throw new GeneralException(new IllegalStateException("The final generated Powershell rule text is null or empty; is the template on the class path?"));
        }
    }
}
