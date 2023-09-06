package com.identityworksllc.iiq.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.identityworksllc.iiq.common.vo.OutcomeType;
import sailpoint.api.Aggregator;
import sailpoint.api.Identitizer;
import sailpoint.api.RequestManager;
import sailpoint.api.SailPointContext;
import sailpoint.connector.Connector;
import sailpoint.connector.ConnectorException;
import sailpoint.connector.ConnectorFactory;
import sailpoint.object.*;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.request.AggregateRequestExecutor;
import sailpoint.server.Environment;
import sailpoint.service.ServiceHandler;
import sailpoint.tools.CloseableIterator;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLReferenceResolver;

import java.util.*;

/**
 * This class contains several utilities for dealing with accounts and applications
 */
@SuppressWarnings("unused")
public class AccountUtilities extends AbstractBaseUtility {

	/**
	 * The options class for {@link #aggregateAccount(AggregateOptions)}, allowing expansion
	 * of the inputs without having to break backwards compatibility.
	 */
	public static class AggregateOptions {
		/**
		 * The name of the account to pull via getObject()
		 */
		private String accountName;

		/**
		 * The options to pass to the Aggregator
		 */
		private Map<String, Object> aggregateOptions;

		/**
		 * The Application object, which should be attached to the current context
		 */
		private Application application;

		/**
		 * The application name
		 */
		private String applicationName;

		/**
		 * The Connector object
		 */
		private Connector connector;

		/**
		 * True if we should force aggregation even for connectors that don't explicitly support getObject()
		 */
		private boolean forceAggregate;

		/**
		 * True if we should consider the input to be incomplete
		 */
		private boolean incomplete;

		/**
		 * True if we should attempt to refresh the Identity afterwards
		 */
		private boolean refreshIdentity;

		/**
		 * Options to pass to the Identitizer
		 */
		private Map<String, Object> refreshOptions;

		/**
		 * The ResourceObject that will be aggregated
		 */
		private ResourceObject resourceObject;

		/**
		 * True if we should run the customization rules (should set if this is a Map input)
		 */
		private boolean runAppCustomization;

		/**
		 * No-args constructor allowing all options to be configured using setters
		 */
		public AggregateOptions() {
			this.aggregateOptions = new HashMap<>();
			this.refreshOptions = new HashMap<>();
		}

		/**
		 * Constructs an AggregateOptions for importing the given Map as though it was returned
		 * from the given application.
		 *
		 * @param applicationName The name of the Application
		 * @param inputs The input data
		 */
		public AggregateOptions(String applicationName, Map<String, Object> inputs) {
			this();
			this.applicationName = applicationName;

			ResourceObject resourceObject = new ResourceObject();
			resourceObject.setAttributes(new Attributes<>(inputs));

			this.resourceObject = resourceObject;
			this.runAppCustomization = true;
		}

		/**
		 * Constructs an AggregateOptions for importing the given Map as though it was returned
		 * from the given application.
		 *
		 * @param application The application
		 * @param inputs The input data
		 */
		public AggregateOptions(Application application, Map<String, Object> inputs) throws GeneralException {
			this();
			this.application = application;
			this.applicationName = application.getName();
			this.connector = ConnectorFactory.getConnector(application, null);

			ResourceObject resourceObject = new ResourceObject();
			resourceObject.setAttributes(new Attributes<>(inputs));

			this.resourceObject = resourceObject;
			this.runAppCustomization = true;
		}

		/**
		 * Constructs an AggregateOptions for importing the given Map as though it was returned
		 * from the given application.
		 *
		 * @param application The application
		 * @param inputs The input data
		 */
		public AggregateOptions(Application application, ResourceObject inputs) throws GeneralException {
			this();
			this.application = application;
			this.applicationName = application.getName();
			this.connector = ConnectorFactory.getConnector(application, null);
			this.resourceObject = inputs;
		}

		/**
		 * Adds a new aggregate option to the existing Map, creating the Map if it is
		 * null.
		 *
		 * @param option The option
		 * @param value The value
		 */
		public void addAggregateOption(String option, Object value) {
			if (this.aggregateOptions == null) {
				this.aggregateOptions = new HashMap<>();
			}

			this.aggregateOptions.put(option, value);
		}

		public String getAccountName() {
			return accountName;
		}

		public Map<String, Object> getAggregateOptions() {
			return aggregateOptions;
		}

		public Application getApplication() {
			return application;
		}

		/**
		 * Gets the application object if it is already set. Otherwise, loads it using the
		 * application name.
		 *
		 * @param context The context to use to load the application
		 * @return The Application object
		 * @throws GeneralException if the application does not exist
		 */
		public Application getApplication(SailPointContext context) throws GeneralException {
			if (this.application != null) {
				return this.application;
			} else {
				return context.getObject(Application.class, this.applicationName);
			}
		}

		public String getApplicationName() {
			return applicationName;
		}

		public Connector getConnector() {
			return connector;
		}

		public Map<String, Object> getRefreshOptions() {
			return refreshOptions;
		}

		public ResourceObject getResourceObject() {
			return resourceObject;
		}

		public boolean isForceAggregate() {
			return forceAggregate;
		}

		public boolean isIncomplete() {
			return incomplete;
		}

		public boolean isRefreshIdentity() {
			return refreshIdentity;
		}

		public boolean isRunAppCustomization() {
			return runAppCustomization;
		}

		public void setAccountName(String accountName) {
			this.accountName = accountName;
		}

		public void setAggregateOptions(Map<String, Object> aggregateOptions) {
			this.aggregateOptions = aggregateOptions;
		}

		public void setApplication(Application application) {
			this.application = application;
		}

		public void setApplicationName(String applicationName) {
			this.applicationName = applicationName;
		}

		public void setConnector(Connector connector) {
			this.connector = connector;
		}

		public void setCorrelateOnly(boolean flag) {
			this.addAggregateOption(Aggregator.ARG_CORRELATE_ONLY, flag);
		}

		public void setForceAggregate(boolean forceAggregate) {
			this.forceAggregate = forceAggregate;
		}

		public void setIncomplete(boolean incomplete) {
			this.incomplete = incomplete;
		}

		public void setRefreshIdentity(boolean refreshIdentity) {
			this.refreshIdentity = refreshIdentity;
		}

		public void setRefreshOptions(Map<String, Object> refreshOptions) {
			this.refreshOptions = refreshOptions;
		}

		public void setResourceObject(ResourceObject resourceObject) {
			this.resourceObject = resourceObject;
		}

		public void setRunAppCustomization(boolean runAppCustomization) {
			this.runAppCustomization = runAppCustomization;
		}

		public void setTrace(boolean flag) {
			this.addAggregateOption(Aggregator.ARG_TRACE, flag);
		}
	}

	/**
	 * The list of tokens that likely indicate a password type variable
	 */
	private static final List<String> likelyPasswordTokens = Arrays.asList("password", "unicodepwd", "secret", "private");

	/**
	 * Fixes the Identity of the given Resource Object
	 * @param resourceObject The ResourceObject input to modify
	 * @param application The Application that the ResourceObject belongs to
	 */
	public static void fixResourceObjectIdentity(ResourceObject resourceObject, Application application) {
		if (Util.isNullOrEmpty(resourceObject.getIdentity())) {
			String identityField = application.getAccountSchema().getIdentityAttribute();
			if (Util.isNotNullOrEmpty(identityField)) {
				String identityValue = resourceObject.getStringAttribute(identityField);
				if (Util.isNotNullOrEmpty(identityValue)) {
					resourceObject.setIdentity(identityValue);
				}
			}

			String displayField = application.getAccountSchema().getDisplayAttribute();
			if (Util.isNotNullOrEmpty(displayField)) {
				String displayValue = resourceObject.getStringAttribute(displayField);
				if (Util.isNotNullOrEmpty(displayValue)) {
					resourceObject.setDisplayName(displayValue);
				}
			}
		}
	}
	/**
	 * The internal provisioning utilities object
	 */
	private final ProvisioningUtilities provisioningUtilities;

	/**
	 * Constructor
	 *
	 * @param c The current SailPointContext
	 */
	public AccountUtilities(SailPointContext c) {
		this(c, new ProvisioningUtilities(c));
	}

	/**
	 * Constructor allowing you to pass a new ProvisioningUtilities
	 *
	 * @param c The context
	 * @param provisioningUtilities A pre-existing provisioning utilities
 	 */
	public AccountUtilities(SailPointContext c, ProvisioningUtilities provisioningUtilities) {
		super(c);

		this.provisioningUtilities = Objects.requireNonNull(provisioningUtilities);
	}

	/**
	 * Aggregates the account, given the options as a Map. The Map will be decoded
	 * into an {@link AggregateOptions} object.
	 *
	 * The return value will also be a Map.
	 *
	 * This simplified interface is intended for situations where this class is only
	 * available via reflection, such as a third-party plugin.
	 *
	 * @param optionsMap The options map
	 * @return The {@link AggregationOutcome}, serialized to a Map via Jackson
	 * @throws GeneralException on any errors
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public Map<String, Object> aggregateAccount(Map<String, Object> optionsMap) throws GeneralException {
		AggregateOptions options = new AggregateOptions();

		if (optionsMap.get("applicationName") instanceof String) {
			options.setApplicationName((String) optionsMap.get("applicationName"));
		} else if (optionsMap.get("application") instanceof Application) {
			options.setApplication((Application) optionsMap.get("application"));
		} else {
			throw new GeneralException("Your input map must include either 'applicationName' or 'application'");
		}

		boolean defaultRunCustomization = false;
		boolean needsNativeIdentity = true;
		if (optionsMap.get("resourceObject") != null) {
			Object ro = optionsMap.get("resourceObject");
			if (ro instanceof Map) {
				ResourceObject resourceObject = new ResourceObject();
				resourceObject.setAttributes(new Attributes((Map)ro));
				options.setResourceObject(resourceObject);
				needsNativeIdentity = false;
				defaultRunCustomization = true;
			} else if (ro instanceof ResourceObject) {
				options.setResourceObject((ResourceObject) ro);
				needsNativeIdentity = false;
				defaultRunCustomization = true;
			} else {
				throw new GeneralException("The input map has a 'resourceObject', but is neither a Map nor a ResourceObject");
			}
		}

		if (optionsMap.get("nativeIdentity") != null) {
			options.setAccountName((String) optionsMap.get("nativeIdentity"));
		} else if (optionsMap.get("accountName") != null) {
			options.setAccountName((String) optionsMap.get("accountName"));
		} else if (needsNativeIdentity) {
			throw new GeneralException("Your input map must include a 'resourceObject' and/or 'nativeIdentity' or 'accountName'");
		}

		if (optionsMap.containsKey("runAppCustomization")) {
			options.setRunAppCustomization(Utilities.isFlagSet(optionsMap.get("runAppCustomization")));
		} else {
			options.setRunAppCustomization(defaultRunCustomization);
		}

		options.setRunAppCustomization(Utilities.isFlagSet(optionsMap.get("incomplete")));

		if (optionsMap.get("aggregateOptions") instanceof Map) {
			options.setAggregateOptions((Map) optionsMap.get("aggregateOptions"));
		}

		AggregationOutcome outcome = aggregateAccount(options);

		try {
			ObjectMapper jacksonMapper = new ObjectMapper();

			return jacksonMapper.convertValue(outcome, Map.class);
		} catch(Exception e) {
			throw new GeneralException("Aggregation finished, but serializing the output to Map failed", e);
		}
	}

	/**
	 * Executes an aggregation according to the given options. This may be invoked directly or
	 * via one of the many overloaded shortcut methods.
	 *
	 * @param options The aggregation options
	 * @return An AggregationOutcome object, with various
	 * @throws GeneralException if any aggregation failures occur
	 */
	public AggregationOutcome aggregateAccount(AggregateOptions options) throws GeneralException {
		final Set<String> allowForce = new HashSet<>(Collections.singletonList("DelimitedFile"));

		Application appObject = options.getApplication(context);

		if (options.application == null) {
			options.application = appObject;
		}

		if (options.connector == null) {
			options.connector = ConnectorFactory.getConnector(appObject, null);
		}

		AggregationOutcome outcome = new AggregationOutcome(options.getApplicationName(), options.getAccountName());
		outcome.setStartTimeMillis(System.currentTimeMillis());

		// This will only be the case if we have not actually done the getObject() yet
		if (options.resourceObject == null) {
			try {
				for (Application.Feature feature : options.connector.getApplication().getFeatures()) {
					if (feature.equals(Application.Feature.NO_RANDOM_ACCESS)) {
						if (!(options.forceAggregate && allowForce.contains(appObject.getType()))) {
							outcome.setStatus(OutcomeType.Skipped);
							outcome.addMessage("Application " + appObject.getName() + " does not support random access");
							return outcome;
						}
					}
				}
				if (appObject.getType().equals("ServiceNow")) {
					options.resourceObject = doServiceNowConnectorHack("sys_id", options.accountName, appObject, false);
				} else {
					options.resourceObject = options.connector.getObject("account", options.accountName, null);
				}
				if (options.resourceObject == null) {
					outcome.setStatus(OutcomeType.Skipped);
					outcome.addMessage(Message.warn("getObject() returned null"));
					log.warn("getObject() for application = '" + options.application.getName() + "', native identity = '" + options.accountName + "' returned null");
					return outcome;
				}
			} catch (ConnectorException onfe) {
				throw new GeneralException(onfe);
			}
		}

		fixResourceObjectIdentity(options.resourceObject, options.application);

		// Normally, this is done as part of getObject() by the connector, so we only want to run it
		// in the case of a truly manual aggregation, i.e., constructing a fake ResourceObject to pass in
		if (options.runAppCustomization) {
			Rule customizationRule = appObject.getCustomizationRule();

			ResourceObject customizationOutput = options.resourceObject;

			if (customizationRule != null) {
				customizationOutput = runCustomizationRule(customizationRule, options, outcome);
			}

			// Abort if customization failed
			if (outcome.getStatus() == OutcomeType.Failure || outcome.getStatus() == OutcomeType.Warning) {
				log.warn("Application customization rule failed");
				return outcome;
			}

			if (customizationOutput == null) {
				outcome.setStatus(OutcomeType.Skipped);
				outcome.addMessage(Message.warn("Application customization rule returned null"));
				log.warn("Application customization rule for application = '" + options.application.getName() + "', native identity = '" + options.accountName + "' returned null");
				return outcome;
			}

			options.resourceObject = customizationOutput;

			fixResourceObjectIdentity(options.resourceObject, options.application);

			if (appObject.getAccountSchema() != null && appObject.getAccountSchema().getCustomizationRule() != null) {
				customizationOutput = runCustomizationRule(appObject.getAccountSchema().getCustomizationRule(), options, outcome);
			}

			// Abort if customization failed
			if (outcome.getStatus() == OutcomeType.Failure || outcome.getStatus() == OutcomeType.Warning) {
				log.warn("Application customization rule failed");
				return outcome;
			}

			if (customizationOutput == null) {
				outcome.setStatus(OutcomeType.Skipped);
				outcome.addMessage(Message.warn("Schema customization rule returned null"));
				log.warn("Schema customization rule for application = '" + options.application.getName() + "', native identity = '" + options.accountName + "' returned null");
				return outcome;
			}

			options.resourceObject = customizationOutput;

			fixResourceObjectIdentity(options.resourceObject, options.application);
		}

		if (options.incomplete) {
			options.resourceObject.setIncomplete(true);
		}

		Attributes<String, Object> argMap = new Attributes<>();
		argMap.put(Identitizer.ARG_PROMOTE_ATTRIBUTES, true);
		argMap.put(Aggregator.ARG_NO_OPTIMIZE_REAGGREGATION, true);
		argMap.put(Identitizer.ARG_ALWAYS_REFRESH_MANAGER, true);

		if (options.aggregateOptions != null) {
			argMap.putAll(options.aggregateOptions);
		}

		Aggregator agg = new Aggregator(context, argMap);
		agg.setMaxIdentities(1);
		TaskResult taskResult = agg.aggregate(appObject, options.resourceObject);

		if (null == taskResult) {
			throw new IllegalStateException("Aggregator.aggregate() returned null unexpectedly");
		}

		outcome.setTaskResult(taskResult);
		outcome.setNativeIdentity(options.resourceObject.getIdentity());
		outcome.setStatus(OutcomeType.Success);

		if (options.refreshIdentity) {
			QueryOptions qo = new QueryOptions();
			qo.addFilter(Filter.eq("application.name", options.application.getName()));
			qo.addFilter(Filter.eq("nativeIdentity", options.resourceObject.getIdentity()));

			List<Link> linkCandidates = context.getObjects(Link.class, qo);

			if (linkCandidates.size() > 1) {
				String warning = "Aggregation produced more than one Link with the same Native Identity: " + options.resourceObject.getIdentity();
				log.warn(warning);
				outcome.addMessage(Message.warn(warning));
			} else if (linkCandidates.size() == 1) {
				Link theLink = linkCandidates.get(0);
				Identity identity = theLink.getIdentity();
				if (identity != null) {
					BaseIdentityUtilities identityUtilities = new BaseIdentityUtilities(context);
					Attributes<String, Object> refreshOptions = identityUtilities.getDefaultRefreshOptions(false);
					if (!Util.isEmpty(options.refreshOptions)) {
						refreshOptions.putAll(options.refreshOptions);
					}
					identityUtilities.refresh(identity, refreshOptions);

					outcome.setIdentityName(identity.getName());
					outcome.setRefreshed(true);
				}
			} else {
				String warning = "After aggregation, no Link found with application = " + appObject.getName() + ", native identity = " + options.resourceObject.getIdentity();
				log.warn(warning);
				outcome.addMessage(Message.warn(warning));
			}
		}

		outcome.setStopTimeMillis(System.currentTimeMillis());

		return outcome;
	}

	/**
	 * @see #aggregateAccount(Application, Connector, ResourceObject, boolean, Map)
	 */
	public AggregationOutcome aggregateAccount(Application appObject, Connector appConnector, ResourceObject rObj, boolean refreshIdentity) throws GeneralException {
		return this.aggregateAccount(appObject, appConnector, rObj, refreshIdentity, new HashMap<>());
	}

	/**
	 * Aggregates the given {@link ResourceObject} into IIQ as though it was pulled in via an aggregation task
	 * @param appObject The application objet
	 * @param appConnector The connector object
	 * @param resource The ResourceObject, either pulled from the Connector or constructed
	 * @param refreshIdentity If true, refresh the Identity after aggregation
	 * @param aggregateArguments Any additional parameters to add to the aggregator
	 * @return The aggrgation outcomes
	 * @throws GeneralException if any IIQ failure occurs
	 */
	public AggregationOutcome aggregateAccount(Application appObject, Connector appConnector, ResourceObject resource, boolean refreshIdentity, Map<String, Object> aggregateArguments) throws GeneralException {
		AggregateOptions options = new AggregateOptions();
		options.application = appObject;
		options.applicationName = appObject.getName();
		options.connector = appConnector;
		options.resourceObject = resource;
		options.aggregateOptions = aggregateArguments;
		options.refreshIdentity = refreshIdentity;

		return aggregateAccount(options);
	}

	/**
	 * @see #aggregateAccount(String, Map, Map)
	 */
	public AggregationOutcome aggregateAccount(String application, Map<String, Object> resource) throws GeneralException {
		return aggregateAccount(application, resource, null);
	}

	/**
	 * Aggregates the given account information into IIQ, given the Map as the resource object data
	 * @param application The application name
	 * @param resource The data representing the account fields
	 * @throws GeneralException if any IIQ failure occurs
	 */
	public AggregationOutcome aggregateAccount(String application, Map<String, Object> resource, Map<String, Object> arguments) throws GeneralException {
		ResourceObject resourceObject = new ResourceObject();
		resourceObject.setAttributes(new Attributes<>(resource));
        Application appObject = context.getObjectByName(Application.class, application);
        if (appObject == null) {
        	throw new GeneralException("No such application: " + application);
		}

        // Fix the resource object Identity field
        fixResourceObjectIdentity(resourceObject, appObject);

        String appConnName = appObject.getConnector();
        Connector appConnector = ConnectorFactory.getConnector(appObject, null);
        if (null == appConnector) {
            throw new GeneralException("Failed to construct an instance of connector [" + appConnName + "]");
        }

		AggregateOptions options = new AggregateOptions();
		options.application = appObject;
		options.applicationName = appObject.getName();
		options.connector = appConnector;
		options.resourceObject = resourceObject;
		options.aggregateOptions = arguments;
		options.runAppCustomization = true;

		return aggregateAccount(options);
	}

	/**
	 * @see #aggregateAccount(String, String, boolean, Map)
	 */
	public AggregationOutcome aggregateAccount(String application, String id, boolean refreshIdentity) throws GeneralException {
		return aggregateAccount(application, id, refreshIdentity, false, new HashMap<>());
	}

	/**
	 * Aggregates the given account information into IIQ, given only a nativeIdentity. Additionally, optionally refresh the user.
	 *
	 * The Application in question must support the "random access" feature (i.e. it must *not* have the NO_RANDOM_ACCESS flag defined).
	 *
	 * @param application The application name to check
	 * @param id The native identity on the target system
	 * @param refreshIdentity If true, the identity will be refreshed after aggregation
	 * @param arguments Any optional arguments to pass to the Aggregator
	 * @throws GeneralException if any IIQ failure occurs
	 */
	public AggregationOutcome aggregateAccount(String application, String id, boolean refreshIdentity, Map<String, Object> arguments) throws GeneralException {
		return aggregateAccount(application, id, refreshIdentity, false, arguments);
	}

	/**
	 * @see #aggregateAccount(String, String, boolean, boolean, Map)
	 */
	public AggregationOutcome aggregateAccount(String application, String id, boolean refreshIdentity, boolean forceAggregate) throws GeneralException {
		return aggregateAccount(application, id, refreshIdentity, forceAggregate, new HashMap<>());
	}

	/**
	 * Aggregates the given account information into IIQ, given only a nativeIdentity. Additionally, optionally refresh the user.
	 *
	 * The Application in question must support the "random access" feature (i.e. it must *not* have the NO_RANDOM_ACCESS flag defined).
	 *
	 * @param application The application name to check
	 * @param id The native identity on the target system
	 * @param refreshIdentity If true, the identity will be refreshed after aggregation
	 * @param forceAggregate If true, we may override what Sailpoint tells us about the features of certain applications
	 * @param arguments Any optional arguments to pass to the Aggregator
	 * @throws GeneralException if any IIQ failure occurs
	 */
	public AggregationOutcome aggregateAccount(String application, String id, boolean refreshIdentity, boolean forceAggregate, Map<String, Object> arguments) throws GeneralException {
        Application appObject = context.getObjectByName(Application.class, application);
        if (appObject == null) {
        	throw new GeneralException("Invalid application name: " + application);
		}

        String appConnName = appObject.getConnector();
        Connector appConnector = ConnectorFactory.getConnector(appObject, null);
        if (null == appConnector) {
            throw new GeneralException("Failed to construct an instance of connector [" + appConnName + "]");
        }

        AggregateOptions options = new AggregateOptions();
        options.applicationName = appObject.getName();
        options.application = appObject;
        options.connector = appConnector;
        options.accountName = id;
        options.forceAggregate = forceAggregate;
        options.refreshIdentity = refreshIdentity;
        options.aggregateOptions = arguments;

        return aggregateAccount(options);
	}

	/**
	 * Aggregates the given account in the background via the Aggregate Request request
	 * type. Uses a slightly future event date to fire the request asynchronously.
	 *
	 * @param targetIdentity The target identity
	 * @param application The application from which the account is being aggregated
	 * @param ro The resource object to process asynchronously
	 * @throws GeneralException on failures
	 */
	public void backgroundAggregateAccount(Identity targetIdentity, Application application, ResourceObject ro) throws GeneralException {
		Map<String, Object> resourceObject = new HashMap<>(ro.getAttributes());
		Attributes<String, Object> requestParams = new Attributes<>();
		requestParams.put(AggregateRequestExecutor.ARG_RESOURCE_OBJECT, resourceObject);
		requestParams.put(AggregateRequestExecutor.ARG_IDENTITY_NAME, Objects.requireNonNull(targetIdentity).getName());
		requestParams.put(AggregateRequestExecutor.ARG_APP_NAME, Objects.requireNonNull(application).getName());

		Map<String, Object> aggregationOptions = new HashMap<>();
		aggregationOptions.put(ServiceHandler.ARG_AGGREGATE_NO_RANDOM_ACCESS, true);
		requestParams.put(AggregateRequestExecutor.ARG_AGG_OPTIONS, aggregationOptions);

		RequestDefinition requestDefinition = context.getObjectByName(RequestDefinition.class, AggregateRequestExecutor.DEF_NAME);

		Request request = new Request();
		request.setEventDate(new Date(System.currentTimeMillis() + 250));
		request.setDefinition(requestDefinition);
		request.setAttributes(requestParams);

		RequestManager.addRequest(context, request);
	}

	/**
	 * Creates the given account
	 * @param user The user to add the account to
	 * @param applicationName The application name
	 * @param map The account data
	 * @throws GeneralException If any failures occur
	 */
	public void createAccount(Identity user, String applicationName, Map<String, Object> map) throws GeneralException {
		ProvisioningPlan plan = new ProvisioningPlan();

		plan.setIdentity(user);

		AccountRequest accountRequest = new AccountRequest();
		accountRequest.setOperation(AccountRequest.Operation.Create);

		for(String key : map.keySet()) {
			String provisioningName = key;
			ProvisioningPlan.Operation operation = ProvisioningPlan.Operation.Set;
			if (key.contains(":")) {
				String[] components = key.split(":");
				operation = ProvisioningPlan.Operation.valueOf(components[0]);
				provisioningName = components[1];
			}
			AttributeRequest request = new AttributeRequest();
			request.setName(provisioningName);
			request.setOperation(operation);
			request.setValue(map.get(key));
			accountRequest.add(request);
		}

		plan.add(accountRequest);

		Map<String, Object> extraParameters = new HashMap<>();
		extraParameters.put("approvalScheme", "none");
		this.provisioningUtilities.doProvisioning(user.getName(), plan, false, extraParameters);
	}

	/**
	 * Disables the given account in the target system
	 * @param target The target to disable
	 * @throws GeneralException if any IIQ failure occurs
	 */
	public void disable(Link target) throws GeneralException {
		Objects.requireNonNull(target, "A non-null Link must be provided");
		new ProvisioningUtilities(context).disableAccount(target);
	}

	/**
	 * Retrieves a single record from a JDBC application, simulating a properly
	 * working getObject().
	 *
	 * The JDBC connector has a bug where the Connection object is not passed to
	 * a BuildMap rule following a getObject(). This method works around the bug
	 * by calling iterateObjects() instead after swapping out the getObjectSQL
	 * and SQL parameters.
	 *
	 * NOTE: This is no longer necessary as of 8.2, as this bug has been fixed.
	 *
	 * TODO this does NOT work where a stored procedure is used.
	 *
	 * @param application The application to swap SQL and getObjectSQL
	 * @param nativeIdentity The native identity to query
	 * @return The queried ResourceObject
	 * @throws GeneralException on failures to work with the Application
	 * @throws ConnectorException on failures to work with the Connector
	 */
	public ResourceObject doJDBCConnectorHack(Application application, String nativeIdentity) throws GeneralException, ConnectorException {
		// The JDBC connector has a weird bug in getObject where BuildMap rules
		// are not passed the Connection to the target system. We will offer the
		// option to use the "iterate" function for "get object" by faking out the
		// query. Connection works fine for iterate.
		ResourceObject resourceObject = null;
		Application cloned = (Application) application.deepCopy((XMLReferenceResolver) context);
		cloned.clearPersistentIdentity();
		String getObjectSQL = cloned.getAttributes().getString("getObjectSQL");
		if (Util.isNotNullOrEmpty(getObjectSQL)) {
			Map<String, Object> variables = new HashMap<>();
			variables.put("identity", nativeIdentity);
			getObjectSQL = Util.expandVariables(getObjectSQL, variables);
			cloned.getAttributes().put("SQL", getObjectSQL);

			Connector sqlConnector = ConnectorFactory.getConnector(cloned, null);
			CloseableIterator<ResourceObject> results = sqlConnector.iterateObjects("account", null, new HashMap<>());
			try {
				// This should produce only one result
				if (results.hasNext()) {
					resourceObject = results.next();
				}
			} finally {
				results.close();
			}
		}
		return resourceObject;
	}

	/**
	 * Retrieves a single account from the ServiceNow connector.
	 *
	 * The ServiceNow connector does not respect all of the connector options for
	 * single-account (getObject) aggregation. This means that you end up with a
	 * weird subset of fields. We need to do a "big" aggregation with the connector
	 * filtered to a single account.
	 *
	 * @param field The field to query
	 * @param id The value for that field (usually a sys_id)
	 * @param appObject The Application
	 * @param skipGroups If true, groups and roles will not be cached (or queried)
	 * @return The resulting ResourceObject from the query
	 * @throws GeneralException If any failures occur
	 * @throws ConnectorException If any connector failures occur
	 */
	public ResourceObject doServiceNowConnectorHack(String field, String id, Application appObject, boolean skipGroups) throws GeneralException, ConnectorException {
		ResourceObject rObj = null;
		Application cloned = (Application) appObject.deepCopy((XMLReferenceResolver) context);
		cloned.clearPersistentIdentity();
		if (skipGroups) {
			Schema schema = cloned.getSchema("account");
			schema.clearPersistentIdentity();
			schema.removeAttribute("groups");
			schema.removeAttribute("roles");
		}
		cloned.getAttributes().put("accountFilterAttribute", field + "=" + id);
		Connector snConnector = ConnectorFactory.getConnector(cloned, null);
		Filter userFilter = Filter.eq(field, id);
		CloseableIterator<ResourceObject> results = snConnector.iterateObjects("account", userFilter, new HashMap<>());
		try {
			// This should produce only one result
			if (results.hasNext()) {
				rObj = results.next();
				if (skipGroups) {
					// This means we only update attributes in the ResourceObject; we don't treat it as authoritative for all attributes
					rObj.setIncomplete(true);
				}
			}
		} finally {
			results.close();
		}
		return rObj;
	}

	/**
	 * Enables the given account in the target system
	 * @param target The target to enable
	 * @throws GeneralException if any IIQ failure occurs
	 */
	public void enable(Link target) throws GeneralException {
		Objects.requireNonNull(target, "A non-null Link must be provided");
		new ProvisioningUtilities(context).enableAccount(target);
	}

	/**
	 * Invokes the Identitizer to refresh the searchable Link attributes
	 * @param theLink The link to refresh
	 * @throws GeneralException if anything fails
	 */
	public void fixLinkSearchableAttributes(Link theLink) throws GeneralException {
		if (theLink == null) {
			throw new IllegalArgumentException("Must pass a non-null Link");
		}

		Identitizer identitizer = new Identitizer(context);
		identitizer.setPromoteAttributes(true);
		identitizer.refreshLink(theLink);
	}

	/**
	 * Gets the provisioning utilities object associated with this AccountUtilities
	 * for modification.
	 *
	 * @return The ProvisioningUtilities
	 */
	public ProvisioningUtilities getProvisioningUtilities() {
		return provisioningUtilities;
	}

	/**
	 * Mask any attributes flagged as secret attributes at the ProvisioningPlan level, and also
	 * any attributes that look like they might be secrets based on a set of likely substrings.
	 * The list of tokens to check heuristically is stored in {@link #likelyPasswordTokens}.
	 *
	 * @param attributes The attribute map to modify
	 */
	public void heuristicMaskSecretAttributes(Map<String, Object> attributes) {
		if (attributes == null) {
			return;
		}
        maskSecretAttributes(attributes);
        List<String> toMask = new ArrayList<>();
        for(String key : attributes.keySet()) {
        	for(String token : likelyPasswordTokens) {
        		if (key.toLowerCase().contains(token) && !key.toLowerCase().contains("expir")) {
        			toMask.add(key);
        		}
        	}
        }
        for(String key : toMask) {
        	attributes.put(key, "********");
        }
	}
	
	/**
	 * Returns true if the given entitlement is assigned by a role. This will
	 * first check the IdentityEntitlement metadata on the Identity and, failing
	 * that, laboriously search through assigned and detected role metadata.
	 *
	 * NOTE: Why not just use IdentityEntitlements? Because they're a delayed indicator.
	 * They are populated via specific refresh and aggregation flags and so may not
	 * be up to date when you need this result.
	 *
	 * @param context A Sailpoint context
	 * @param account The account to check
	 * @param attribute The account attribute to examine
	 * @param entitlementName The account attribute value to examine
	 * @return True if the entitlement is associated with an assigned role
	 * @throws GeneralException if any failures occur
	 */
	public boolean isAssignedByRole(SailPointContext context, Link account, String attribute, String entitlementName) throws GeneralException {
		boolean caseInsensitive = account.getApplication().isCaseInsensitive();
		Identity who = account.getIdentity();
		// Step 1: Find an IdentityEntitlement that matches
		QueryOptions qo = new QueryOptions();
		qo.add(Filter.eq("identity.id", who.getId()));
		qo.add(Filter.eq("name", attribute));
		qo.add(Filter.eq("application.id", account.getApplicationId()));
		if (caseInsensitive) {
			qo.add(Filter.ignoreCase(Filter.eq("value", entitlementName)));
		} else {
			qo.add(Filter.eq("value", entitlementName));
		}

		List<IdentityEntitlement> entitlements = context.getObjects(IdentityEntitlement.class, qo);
		for(IdentityEntitlement ie : Util.safeIterable(entitlements)) {
			if (ie.isGrantedByRole()) {
				return true;
			}
		}

		// Step 2: If we got here, the IdentityEntitlement may simply have not been
		// assigned yet. We need to go spelunking through the roles to find it.
		for(RoleAssignment assignment : Util.safeIterable(who.getRoleAssignments())) {
			if (assignment.isNegative()) {
				continue;
			}
			for(RoleTarget target : Util.safeIterable(assignment.getTargets())) {
				if (target.getApplicationName().equals(account.getApplicationName()) && target.getNativeIdentity().equals(account.getNativeIdentity())) {
					for (AccountItem item : Util.safeIterable(target.getItems())) {
						if (item.getName().equals(attribute)) {
							List<String> valueList = item.getValueList();
							if (valueList != null) {
								for(String v : valueList) {
									if (entitlementName.equals(v) || (caseInsensitive && entitlementName.equalsIgnoreCase(v))) {
										return true;
									}
								}
							} else if (item.getValue() != null) {
								Object v = item.getValue();
								if (entitlementName.equals(v) || (caseInsensitive && entitlementName.equalsIgnoreCase(String.valueOf(v)))) {
									return true;
								}
							}
						}
					}
				}
			}
		}
		for(RoleDetection detection : Util.safeIterable(who.getRoleDetections())) {
			if (!detection.hasAssignmentIds()) {
				continue;
			}
			for(RoleTarget target : Util.safeIterable(detection.getTargets())) {
				if (target.getApplicationName().equals(account.getApplicationName()) && target.getNativeIdentity().equals(account.getNativeIdentity())) {
					for (AccountItem item : Util.safeIterable(target.getItems())) {
						if (item.getName().equals(attribute)) {
							List<String> valueList = item.getValueList();
							if (valueList != null) {
								for(String v : valueList) {
									if (entitlementName.equals(v) || (caseInsensitive && entitlementName.equalsIgnoreCase(v))) {
										return true;
									}
								}
							} else if (item.getValue() != null) {
								Object v = item.getValue();
								if (entitlementName.equals(v) || (caseInsensitive && entitlementName.equalsIgnoreCase(String.valueOf(v)))) {
									return true;
								}
							}
						}
					}
				}
			}
		}
		return false;
	}
	
	/**
	 * Mask any attributes flagged as secret attributes at the ProvisioningPlan level
	 * @param attributes The attribute map to modify
	 */
	public void maskSecretAttributes(Map<String, Object> attributes) {
		if (attributes == null) {
			return;
		}
        List<String> secretAttributeNames = ProvisioningPlan.getSecretProvisionAttributeNames();
        for(String attr : secretAttributeNames) {
        	if (attributes.containsKey(attr)) {
        		attributes.put(attr, "********");
        	}
        }
	}

	/**
	 * Runs the customization rule given the aggregate options. This will be invoked by
	 * aggregateAccount at several different times.
	 *
	 * @param theRule The Customization rule to run
	 * @param options The aggregate options container
	 * @return The modified resource object
	 */
	private ResourceObject runCustomizationRule(Rule theRule, AggregateOptions options, AggregationOutcome outcome) {
		try {
			// Pass the mandatory arguments to the Customization rule for the app.
			Map<String, Object> ruleArgs = new HashMap<>();
			ruleArgs.put("context", context);
			ruleArgs.put("log", log);
			ruleArgs.put("object", options.resourceObject);
			ruleArgs.put("application", options.application);
			ruleArgs.put("connector", options.connector);
			ruleArgs.put("state", new HashMap<String, Object>());
			// Call the customization rule just like a normal aggregation would.
			Object output = context.runRule(theRule, ruleArgs, null);
			// Make sure we got a valid resourceObject back from the rule.
			if (output == null || output instanceof ResourceObject) {
				return (ResourceObject) output;
			}
		} catch (Exception e) {
			// Log and ignore
			log.error("Caught an error running Customization Rule " + theRule.getName(), e);
			outcome.addError("Caught an error running Customization Rule " + theRule.getName(), e);
			outcome.setStatus(OutcomeType.Failure);

			return null;
		}
		return options.resourceObject;
	}

}
