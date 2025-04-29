package com.identityworksllc.iiq.common.access;

import com.identityworksllc.iiq.common.*;
import org.apache.commons.lang3.function.FailableConsumer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.DynamicScopeMatchmaker;
import sailpoint.api.Matchmaker;
import sailpoint.api.Meter;
import sailpoint.authorization.Authorizer;
import sailpoint.authorization.UnauthorizedAccessException;
import sailpoint.object.*;
import sailpoint.plugin.PluginBaseHelper;
import sailpoint.plugin.PluginContext;
import sailpoint.plugin.PluginsUtil;
import sailpoint.rest.plugin.BasePluginResource;
import sailpoint.server.Environment;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Static methods for implementing access checks. This is used directly by {@link ThingAccessUtils},
 * but allows migration to this better interface.
 *
 * @see ThingAccessUtils
 *
 * @author Devin Rosenbauer
 * @author Instrumental Identity
 */
public final class AccessCheck {
    /**
     * The container object to hold the cached ThingAccessUtil results
     */
    public static final class SecurityResult implements Supplier<Optional<AccessCheckResponse>> {
        /**
         * The epoch millisecond timestamp when this object expires, one minute after creation
         */
        private final long expiration;

        /**
         * The actual cached result
         */
        private final Object result;

        /**
         * Store the result with an expiration time
         * @param result The result to cache
         */
        public SecurityResult(AccessCheckResponse result) {
            this.result = result;
            this.expiration = System.currentTimeMillis() + (1000L * 60);
        }

        /**
         * Returns the cached result
         * @return The cached result
         */
        public Optional<AccessCheckResponse> get() {
            if (this.result instanceof AccessCheckResponse && !this.isExpired()) {
                return Optional.of((AccessCheckResponse) this.result);
            } else {
                return Optional.empty();
            }
        }

        /**
         * Returns true if the current epoch timestamp is later than the expiration date
         * @return True if expired
         */
        private boolean isExpired() {
            return System.currentTimeMillis() >= expiration;
        }
    }

    /**
     * The container object to identify the cached ThingAccessUtil inputs.
     *
     * NOTE: It is very important that this work properly across plugin
     * classloader contexts, even if the plugin has its own version of
     * ThingAccessUtils. The objects containing within this object are
     * therefore all base JDK types, like {@link String} and {@link Map}.
     */
    public static final class SecurityCacheToken {
        /**
         * The CommonSecurityConfig object associated with the cached result
         */
        private final Map<String, Object> commonSecurityConfig;

        /**
         * The version of the plugin cache to invalidate records whenever
         * a new plugin is installed. This will prevent wacky class cast
         * problems.
         */
        private final int pluginVersion;

        /**
         * The name of the source identity
         */
        private final String source;

        /**
         * The optional state map
         */
        private final Map<String, Object> state;

        /**
         * The name of the target identity
         */
        private final String target;

        /**
         * Constructs a new cache entry
         * @param csc The security config
         * @param source The source identity name
         * @param target The target identity name
         * @param state The state of the security operation
         */
        public SecurityCacheToken(CommonSecurityConfig csc, String source, String target, Map<String, Object> state) {
            this.commonSecurityConfig = csc.toMap();
            this.target = target;
            this.source = source;
            this.state = new HashMap<>();

            if (state != null) {
                this.state.putAll(state);
            }

            this.pluginVersion = Environment.getEnvironment().getPluginsCache().getVersion();
        }

        /**
         * Constructs a new cache entry based on the input
         * @param input The input object
         * @throws GeneralException the errors
         */
        public SecurityCacheToken(AccessCheckInput input) throws GeneralException {
            this(
                    input.getConfiguration(),
                    input.getUserContext().getLoggedInUserName(),
                    (input.getTarget() == null || input.getTarget().getName() == null) ? "null" : input.getTarget().getName(),
                    input.getState()
            );
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SecurityCacheToken that = (SecurityCacheToken) o;
            return this.pluginVersion == that.pluginVersion && Objects.equals(commonSecurityConfig, that.commonSecurityConfig) && Objects.equals(target, that.target) && Objects.equals(source, that.source) && Objects.equals(state, that.state);
        }

        @Override
        public int hashCode() {
            return Objects.hash(pluginVersion, commonSecurityConfig, target, source, state);
        }
    }
    /**
     * The access check name used for an anonymous input
     */
    public static final String ANONYMOUS_THING = "anonymous";
    /**
     * The cache key in CustomGlobal
     */
    private static final String CACHE_KEY = "idw.ThingAccessUtils.cache";

    /**
     * The logger
     */
    private static final Log log = LogFactory.getLog(AccessCheck.class);

    /**
     * Private constructor to prevent instantiation
     */
    private AccessCheck() {
        /* Utility class cannot be instantiated */
    }

    /**
     * Returns an 'allowed' response if the logged-in (subject) user can access the
     * item based on the common configuration parameters and target defined.
     *
     * Results for the same {@link CommonSecurityConfig}, source, and target user will be
     * cached for up to one minute unless the CommonSecurityConfig object has noCache set to true.
     *
     * @param input The input containing the configuration for the checkThingAccess utility
     * @return True if the user has access to the thing based on the configuration
     */
    public static AccessCheckResponse accessCheck(AccessCheckInput input) {
        if (input.getConfiguration() == null) {
            throw new IllegalArgumentException("An access check must contain a CommonSecurityConfig");
        }

        if (input.getUserContext() == null) {
            throw new IllegalArgumentException("An access check must specify a UserContext for accessing the IIQ context and the logged in user");
        }

        AccessCheckResponse result;
        Meter.enterByName("AccessCheck.accessCheck");
        try {
            if (!input.getConfiguration().isNoCache()) {
                SecurityCacheToken cacheToken = new SecurityCacheToken(input);
                Optional<AccessCheckResponse> cachedResult = getCachedResult(cacheToken);
                if (cachedResult.isPresent()) {
                    return cachedResult.get();
                }
                result = accessCheckImpl(input);
                getCacheMap().put(cacheToken, new SecurityResult(result));
            } else {
                result = accessCheckImpl(input);
            }
        } catch(Exception e) {
            result = new AccessCheckResponse();
            result.denyMessage("Caught an exception evaluating criteria: " + e.getMessage());
            log.error("Caught an exception evaluating access criteria to " + input.getThingName(), e);
        } finally {
            Meter.exitByName("AccessCheck.accessCheck");
        }
        return result;
    }

    /**
     * Returns an allowed response if the logged in user can access the item based on
     * the common configuration parameters.
     *
     * @param input The inputs to the access check
     * @return True if the user has access to the thing based on the configuration
     * @throws GeneralException if any check failures occur (this should be interpreted as "no access")
     */
    private static AccessCheckResponse accessCheckImpl(final AccessCheckInput input) throws GeneralException {
        AccessCheckResponse result = new AccessCheckResponse();

        UserContext pluginContext = input.getUserContext();

        final Identity currentUser = pluginContext.getLoggedInUser();
        final Identity target = (input.getTarget() != null) ? input.getTarget() : currentUser;
        final String currentUserName = pluginContext.getLoggedInUserName();
        final CommonSecurityConfig config = input.getConfiguration();
        final String thingName = input.getThingName();

        Configuration systemConfig = Configuration.getSystemConfig();
        boolean beanshellGetsPluginContext = systemConfig.getBoolean("IIQCommon.ThingAccessUtils.beanshellGetsPluginContext", false);

        if (log.isTraceEnabled()) {
            log.trace("START: Checking access for subject = " + currentUser.getName() + ", target = " + target.getName() + ", thing = " + thingName + ", config = " + config);
        }

        if (result.isAllowed() && config.isDisabled()) {
            result.denyMessage("Access denied to " + thingName + " because the configuration is marked disabled");
        }

        if (result.isAllowed()) {
            handleCustomAccessCheck(input, result);
        }

        if (result.isAllowed() && Utilities.isNotEmpty(config.getOneOf())) {
            boolean anyMatch = false;
            for(CommonSecurityConfig sub : config.getOneOf()) {
                AccessCheckInput child = new AccessCheckInput(input, sub);
                AccessCheckResponse childResponse = accessCheckImpl(child);
                if (childResponse.isAllowed()) {
                    anyMatch = true;
                    break;
                }
            }
            if (!anyMatch) {
                result.denyMessage("Access denied to " + thingName + " because none of the items in the 'oneOf' list resolved to true");
            }
        }
        if (result.isAllowed() && Utilities.isNotEmpty(config.getAllOf())) {
            boolean allMatch = true;
            for(CommonSecurityConfig sub : config.getAllOf()) {
                AccessCheckInput child = new AccessCheckInput(input, sub);
                AccessCheckResponse childResponse = accessCheckImpl(child);
                if (!childResponse.isAllowed()) {
                    allMatch = false;
                    break;
                }
            }
            if (!allMatch) {
                result.denyMessage("Access denied to " + thingName + " because at least one of the items in the 'allOf' list resolved to 'deny'");
            }
        }
        if (result.isAllowed() && Utilities.isNotEmpty(config.getNot())) {
            boolean anyMatch = false;
            for(CommonSecurityConfig sub : config.getNot()) {
                AccessCheckInput child = new AccessCheckInput(input, sub);
                AccessCheckResponse childResponse = accessCheckImpl(child);
                if (childResponse.isAllowed()) {
                    anyMatch = true;
                    break;
                }
            }
            if (anyMatch) {
                result.denyMessage("Access denied to " + thingName + " because at least one of the items in the 'not' list resolved to 'allow'");
            }
        }
        if (result.isAllowed() && Util.isNotNullOrEmpty(config.getSettingOffSwitch())) {
            String[] pieces = config.getSettingOffSwitch().split(":");
            boolean settingEnabled = false;

            if (pieces.length == 0) {
                throw new IllegalArgumentException("Unable to resolve settingOffSwitch");
            }
            if (pieces.length == 1) {
                String setting = config.getSettingOffSwitch().trim();
                if (pluginContext instanceof PluginContext) {
                    settingEnabled = ((PluginContext) pluginContext).getSettingBool(setting.trim());
                } else {
                    result.addMessage(Message.error("A 'settingOffSwitch' was not used in a plugin context, without specifying the plugin name"));
                    throw new IllegalStateException("A 'settingOffSwitch' must be used in a plugin context, or specify the plugin name before ':', such as 'MyPlugin:settingName'");
                }
            } else {
                String plugin = pieces[0].trim();
                String setting = pieces[1].trim();

                result.addMessage("Checking plugin " + plugin + ", setting " + setting);

                settingEnabled = PluginBaseHelper.getSettingBool(plugin, setting);
            }
            // If the setting is ON / TRUE, then the access is DENIED. This is flipping ON an OFF-SWITCH.
            // Yeah, I know...
            if (settingEnabled) {
                result.denyMessage("Access denied to " + thingName + " because the feature " + config.getSettingOffSwitch() + " is disabled in plugin settings");
            }
        }
        if (result.isAllowed() && config.getAccessCheckScript() != null && Util.isNotNullOrEmpty(config.getAccessCheckScript().getSource())) {
            Script script = Utilities.getAsScript(config.getAccessCheckScript());
            Map<String, Object> scriptArguments = new HashMap<>();
            scriptArguments.put("subject", currentUser);
            scriptArguments.put("target", target);
            scriptArguments.put("requester", currentUser);
            scriptArguments.put("identity", target);
            scriptArguments.put("identityName", target.getName());
            scriptArguments.put("manager", target.getManager());
            scriptArguments.put("context", pluginContext.getContext());
            scriptArguments.put("log", LogFactory.getLog(pluginContext.getClass()));
            scriptArguments.put("state", input.getState());
            if (beanshellGetsPluginContext && pluginContext instanceof BasePluginResource) {
                scriptArguments.put("pluginContext", pluginContext);
            } else {
                scriptArguments.put("pluginContext", null);
            }

            Object output = pluginContext.getContext().runScript(script, scriptArguments);
            // If the script returns a non-null value, it will be considered the authoritative
            // response. No further checks will be done. If the output is null, the access
            // checks will defer farther down.
            if (output != null) {
                boolean userAllowed = Util.otob(output);
                if (!userAllowed) {
                    result.denyMessage("Access denied to " + thingName + " because access check script returned false for subject user " + currentUserName);
                }
                return result;
            }
        }
        if (result.isAllowed() && config.getAccessCheckRule() != null) {
            Map<String, Object> scriptArguments = new HashMap<>();
            scriptArguments.put("subject", currentUser);
            scriptArguments.put("target", target);
            scriptArguments.put("requester", currentUser);
            scriptArguments.put("identity", target);
            scriptArguments.put("identityName", target.getName());
            scriptArguments.put("manager", target.getManager());
            scriptArguments.put("context", pluginContext.getContext());
            scriptArguments.put("log", LogFactory.getLog(pluginContext.getClass()));
            scriptArguments.put("state", input.getState());
            if (beanshellGetsPluginContext) {
                scriptArguments.put("pluginContext", pluginContext);
            } else {
                scriptArguments.put("pluginContext", null);
            }
            if (log.isTraceEnabled() || input.isDebug()) {
                String message = "Running access check rule " + config.getAccessCheckRule().getName() + " for subject = " + currentUserName + ", target = " + target.getName();
                result.addMessage(message);
                log.trace(message);
            }
            Object output = pluginContext.getContext().runRule(config.getAccessCheckRule(), scriptArguments);
            // If the rule returns a non-null value, it will be considered the authoritative
            // response. No further checks will be done. If the output is null, the access
            // checks will defer farther down.
            if (output != null) {
                boolean userAllowed = Util.otob(output);
                if (!userAllowed) {
                    result.denyMessage("Access denied to " + thingName + " because access check rule returned false for subject user " + currentUserName);
                }
                return result;
            }
        }
        if (result.isAllowed() && !Util.isEmpty(config.getRequiredRights())) {
            boolean userAllowed = false;
            List<String> rights = config.getRequiredRights();
            Collection<String> userRights = pluginContext.getLoggedInUserRights();
            if (userRights != null) {
                for(String right : Util.safeIterable(userRights)) {
                    if (rights.contains(right)) {
                        result.addMessage("Subject matched required SPRight: " + right);
                        userAllowed = true;
                        break;
                    }
                }
            }
            if (!userAllowed) {
                result.denyMessage("Access denied to " + thingName + " because subject user " + currentUserName + " does not match any of the required rights " + rights);
            }
        }
        if (result.isAllowed() && !Util.isEmpty(config.getRequiredCapabilities())) {
            boolean userAllowed = false;
            List<String> capabilities = config.getRequiredCapabilities();
            List<Capability> loggedInUserCapabilities = pluginContext.getLoggedInUserCapabilities();
            for(Capability cap : Util.safeIterable(loggedInUserCapabilities)) {
                if (capabilities.contains(cap.getName())) {
                    result.addMessage("Subject matched required capability: " + cap.getName());
                    userAllowed = true;
                    break;
                }
            }
            if (!userAllowed) {
                result.denyMessage("Access denied to " + thingName + " because subject user " + currentUserName + " does not match any of these required capabilities " + capabilities);
            }
        }
        if (result.isAllowed() && !Util.isEmpty(config.getExcludedRights())) {
            boolean userAllowed = true;
            List<String> rights = config.getRequiredRights();
            Collection<String> userRights = pluginContext.getLoggedInUserRights();
            if (userRights != null) {
                for(String right : Util.safeIterable(userRights)) {
                    if (rights.contains(right)) {
                        result.addMessage("Subject matched excluded SPRight: " + right);
                        userAllowed = false;
                        break;
                    }
                }
            }
            if (!userAllowed) {
                result.denyMessage("Access denied to " + thingName + " because subject user " + currentUserName + " matches one of these excluded SPRights: " + rights);
            }
        }
        if (result.isAllowed() && !Util.isEmpty(config.getExcludedCapabilities())) {
            boolean userAllowed = true;
            List<String> capabilities = config.getRequiredCapabilities();
            List<Capability> loggedInUserCapabilities = pluginContext.getLoggedInUserCapabilities();
            for(Capability cap : Util.safeIterable(loggedInUserCapabilities)) {
                if (capabilities.contains(cap.getName())) {
                    result.addMessage("Subject matched excluded Capability: " + cap.getName());
                    userAllowed = false;
                    break;
                }
            }
            if (!userAllowed) {
                result.denyMessage("Access denied to " + thingName + " because subject user " + currentUserName + " matches one of these excluded capabilities: " + capabilities);
            }
        }

        if (result.isAllowed() && !Util.isEmpty(config.getExcludedWorkgroups())) {
            List<String> workgroups = config.getExcludedWorkgroups();
            boolean matchesWorkgroup = matchesAnyWorkgroup(currentUser, workgroups);
            if (matchesWorkgroup) {
                result.denyMessage("Access denied to " + thingName + " because subject user " + currentUserName + " is a member of an excluded workgroup in " + workgroups);
            }
        }
        if (result.isAllowed() && !Util.isEmpty(config.getRequiredWorkgroups())) {
            List<String> workgroups = config.getRequiredWorkgroups();
            boolean userAllowed = matchesAnyWorkgroup(currentUser, workgroups);
            if (!userAllowed) {
                result.denyMessage("Access denied to " + thingName + " because subject user " + currentUserName + " does not match any of the required workgroups " + workgroups);
            }
        }
        if (result.isAllowed() && Util.isNotNullOrEmpty(config.getAccessCheckFilter())) {
            String filterString = config.getValidTargetFilter();
            Filter compiledFilter = Filter.compile(filterString);

            HybridObjectMatcher hom = new HybridObjectMatcher(pluginContext.getContext(), compiledFilter);

            if (!hom.matches(currentUser)) {
                result.denyMessage("Access denied to " + thingName + " because subject user " + currentUserName + " does not match the access check filter");
            } else {
                result.addMessage("Subject user matches filter: " + filterString);
            }
        }
        if (result.isAllowed() && config.getAccessCheckSelector() != null) {
            IdentitySelector selector = config.getAccessCheckSelector();
            Matchmaker matchmaker = new Matchmaker(pluginContext.getContext());
            if (!matchmaker.isMatch(selector, currentUser)) {
                result.denyMessage("Access denied to " + thingName + " because subject user " + currentUserName + " does not match the access check selector");
            } else {
                result.addMessage("Subject user matches selector: " + selector.toXml());
            }
        }
        if (result.isAllowed() && Util.isNotNullOrEmpty(config.getMirrorRole())) {
            String role = config.getMirrorRole();
            Bundle bundle = pluginContext.getContext().getObject(Bundle.class, role);
            if (bundle.getSelector() == null && !Util.isEmpty(bundle.getProfiles())) {
                if (log.isDebugEnabled()) {
                    log.debug("Running mirrorRole access check on an IT role; this may have performance concerns");
                }
            }
            MatchUtilities matchUtilities = new MatchUtilities(pluginContext.getContext());
            if (!matchUtilities.matches(currentUser, bundle)) {
                result.denyMessage("Access denied to " + thingName + " because subject user " + currentUserName + " does not match the selector or profile on role " + bundle.getName());
            } else {
                result.addMessage("Subject user matches role criteria: " + bundle.getName());
            }
        }
        if (result.isAllowed() && Util.isNotNullOrEmpty(config.getMirrorQuicklinkPopulation())) {
            String quicklinkPopulation = config.getMirrorQuicklinkPopulation();
            if (Util.isNotNullOrEmpty(quicklinkPopulation)) {
                DynamicScopeMatchmaker dynamicScopeMatchmaker = new DynamicScopeMatchmaker(pluginContext.getContext());
                DynamicScope dynamicScope = pluginContext.getContext().getObject(DynamicScope.class, quicklinkPopulation);
                boolean matchesDynamicScope = dynamicScopeMatchmaker.isMatch(dynamicScope, currentUser);
                if (matchesDynamicScope) {
                    result.addMessage("Subject user matches DynamicScope: " + quicklinkPopulation);
                    DynamicScope.PopulationRequestAuthority populationRequestAuthority = dynamicScope.getPopulationRequestAuthority();
                    if (populationRequestAuthority != null && !populationRequestAuthority.isAllowAll()) {
                        matchesDynamicScope = dynamicScopeMatchmaker.isMember(currentUser, target, populationRequestAuthority);
                        if (matchesDynamicScope) {
                            result.addMessage("Target user matches DynamicScope: " + quicklinkPopulation);
                        }
                    }
                }
                if (!matchesDynamicScope) {
                    result.denyMessage("Access denied to " + thingName + " because QuickLink population " + quicklinkPopulation + " does not match the subject and target");
                }
            }
        }
        if (result.isAllowed() && !Util.isEmpty(config.getValidTargetExcludedRights())) {
            boolean userAllowed = true;
            List<String> rights = config.getValidTargetExcludedRights();
            Collection<String> userRights = target.getCapabilityManager().getEffectiveFlattenedRights();
            if (userRights != null) {
                for(String right : Util.safeIterable(userRights)) {
                    if (rights.contains(right)) {
                        result.addMessage("Target matched excluded right: " + right);
                        userAllowed = false;
                        break;
                    }
                }
            }
            if (!userAllowed) {
                result.denyMessage("Access denied to " + thingName + " because target user " + target.getName() + " matches one or more of the excluded rights " + rights);
            }
        }
        if (result.isAllowed() && !Util.isEmpty(config.getValidTargetExcludedCapabilities())) {
            boolean userAllowed = true;
            List<String> rights = config.getValidTargetExcludedCapabilities();
            List<Capability> capabilities = target.getCapabilityManager().getEffectiveCapabilities();
            if (capabilities != null) {
                for(Capability capability : Util.safeIterable(capabilities)) {
                    if (rights.contains(capability.getName())) {
                        result.addMessage("Target matched excluded capability: " + capability.getName());
                        userAllowed = false;
                        break;
                    }
                }
            }
            if (!userAllowed) {
                result.denyMessage("Access denied to " + thingName + " because target user " + target.getName() + " matches one or more of the excluded capabilities " + rights);
            }
        }

        if (result.isAllowed() && Util.isNotNullOrEmpty(config.getInvalidTargetFilter())) {
            String filterString = config.getValidTargetFilter();
            Filter compiledFilter = Filter.compile(filterString);

            HybridObjectMatcher hom = new HybridObjectMatcher(pluginContext.getContext(), compiledFilter);

            if (hom.matches(target)) {
                result.denyMessage("Access denied to " + thingName + " because target user " + target.getName() + " matches the invalid target filter");
            }
        }
        if (result.isAllowed() && !Util.isEmpty(config.getValidTargetWorkgroups())) {
            List<String> workgroups = config.getValidTargetWorkgroups();
            boolean userAllowed = matchesAnyWorkgroup(target, workgroups);
            if (!userAllowed) {
                result.denyMessage("Access denied to " + thingName + " because target user " + target.getName() + " does not match any of the required workgroups " + workgroups);
            }
        }
        if (result.isAllowed() && !Util.isEmpty(config.getValidTargetCapabilities())) {
            boolean userAllowed = false;
            List<String> rights = config.getValidTargetCapabilities();
            List<Capability> capabilities = target.getCapabilityManager().getEffectiveCapabilities();
            if (capabilities != null) {
                for(Capability capability : Util.safeIterable(capabilities)) {
                    if (rights.contains(capability.getName())) {
                        result.addMessage("Target matched capability: " + capability.getName());
                        userAllowed = true;
                    }
                }
            }
            if (!userAllowed) {
                result.denyMessage("Access denied to " + thingName + " because target user " + target.getName() + " does not match one or more of the included capabilities " + rights);
            }
        }
        if (result.isAllowed() && config.getValidTargetSelector() != null) {
            IdentitySelector selector = config.getValidTargetSelector();
            Matchmaker matchmaker = new Matchmaker(pluginContext.getContext());
            if (!matchmaker.isMatch(selector, target)) {
                result.denyMessage("Access denied to " + thingName + " because target user " + target.getName() + " does not match the valid target selector");
            }
        }
        if (result.isAllowed() && Util.isNotNullOrEmpty(config.getValidTargetFilter())) {
            String filterString = config.getValidTargetFilter();
            Filter compiledFilter = Filter.compile(filterString);

            HybridObjectMatcher hom = new HybridObjectMatcher(pluginContext.getContext(), compiledFilter);

            if (!hom.matches(target)) {
                result.denyMessage("Access denied to " + thingName + " because target user " + target.getName() + " does not match the valid target filter");
            }
        }
        if (log.isTraceEnabled()) {
            String resultString = result.isAllowed() ? "ALLOWED access" : "DENIED access";
            log.trace("FINISH: " + resultString + " for subject = " + currentUser.getName() + ", target = " + target.getName() + ", thing = " + thingName + ", result = " + result);
        }
        return result;
    }

    /**
     * An optional clear-cache method that can be used by plugin code
     */
    public static void clearCachedResults() {
        ConcurrentHashMap<AccessCheck.SecurityCacheToken, AccessCheck.SecurityResult> cacheMap = getCacheMap();
        cacheMap.clear();
    }

    /**
     * Creates a native IIQ authorizer that performs a CommonSecurityConfig check
     * @param config The configuration
     * @return The authorizer
     */
    public static Authorizer createAuthorizer(CommonSecurityConfig config) {
        return userContext -> {
            AccessCheckInput input = new AccessCheckInput(userContext, config);
            AccessCheckResponse response = AccessCheck.accessCheck(input);
            if (!response.isAllowed()) {
                log.debug("Access denied with messages: " + response.getMessages());
                throw new UnauthorizedAccessException("Access denied");
            }
        };
    }

    /**
     * Creates the cache map, which should be stored in CustomGlobal. If it does not exist,
     * we create and store a new one. Since this is just for efficiency, we don't really
     * care about synchronization.
     *
     * A new cache will be created whenever a new plugin is installed, incrementing the
     * Environment's plugin version.
     *
     * @return The cache map
     */
    public static ConcurrentHashMap<SecurityCacheToken, SecurityResult> getCacheMap() {
        String versionedKey = CACHE_KEY + "." + Utilities.getPluginVersion();
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<SecurityCacheToken, SecurityResult> cacheMap = (ConcurrentHashMap<SecurityCacheToken, SecurityResult>) CustomGlobal.get(versionedKey);
        if (cacheMap == null) {
            cacheMap = new ConcurrentHashMap<>();
            CustomGlobal.put(versionedKey, cacheMap);
        }
        return cacheMap;
    }

    /**
     * Gets an optional cached result for the given cache token. An empty
     * optional will be returned if there is no cached entry for the given token
     * or if it has expired or if there is classloader weirdness.
     *
     * @param securityContext The security context
     * @return The cached result, if one exists
     */
    private static Optional<AccessCheckResponse> getCachedResult(SecurityCacheToken securityContext) {
        ConcurrentHashMap<SecurityCacheToken, SecurityResult> cacheMap = getCacheMap();
        Supplier<Optional<AccessCheckResponse>> cachedEntry = cacheMap.get(securityContext);
        if (cachedEntry == null) {
            return Optional.empty();
        } else {
            return cachedEntry.get();
        }
    }

    /**
     * Handles a custom access check by constructing the class and invoking it.
     *
     * @param input The input to the access check
     * @param result The output to be modified by the custom check
     * @throws GeneralException if anything fails
     */
    private static void handleCustomAccessCheck(AccessCheckInput input, AccessCheckResponse result) throws GeneralException {
        Metered.meter("AccessCheck.handleCustomAccessCheck", () -> {
            Configuration systemConfig = Configuration.getSystemConfig();

            // If a custom access check is defined, invoke it
            String customImplPlugin = systemConfig.getString("IIQCommon.ThingAccessUtils.customCheckPlugin");
            String customImpl = systemConfig.getString("IIQCommon.ThingAccessUtils.customCheckClass");
            if (Util.isNotNullOrEmpty(customImpl)) {
                UserContext pluginContext = input.getUserContext();

                final Identity currentUser = pluginContext.getLoggedInUser();
                final Identity target = (input.getTarget() != null) ? input.getTarget() : currentUser;
                final String currentUserName = pluginContext.getLoggedInUserName();
                final CommonSecurityConfig config = input.getConfiguration();
                final String thingName = input.getThingName();

                if (log.isTraceEnabled()) {
                    log.trace("START: Custom access check for subject = " + currentUser.getName() + ", target = " + target.getName() + ", thing = " + thingName + ", custom class = " + customImpl);
                }

                Map<String, Object> scriptArguments = new HashMap<>();
                scriptArguments.put("response", result);
                scriptArguments.put("input", input);
                scriptArguments.put("name", input.getThingName());
                scriptArguments.put("config", config.toMap());
                scriptArguments.put("subject", currentUser);
                scriptArguments.put("target", target);
                scriptArguments.put("requester", currentUser);
                scriptArguments.put("identity", target);
                scriptArguments.put("identityName", target.getName());
                scriptArguments.put("manager", target.getManager());
                scriptArguments.put("context", pluginContext.getContext());
                scriptArguments.put("log", LogFactory.getLog(pluginContext.getClass()));
                scriptArguments.put("state", input.getState());
                if (pluginContext instanceof BasePluginResource) {
                    scriptArguments.put("pluginContext", pluginContext);
                } else {
                    scriptArguments.put("pluginContext", null);
                }

                try {
                    FailableConsumer<Map<String, Object>, GeneralException> accessCheck;
                    if (Util.isNullOrEmpty(customImplPlugin)) {
                        @SuppressWarnings("unchecked")
                        FailableConsumer<Map<String, Object>, GeneralException> unchecked = (FailableConsumer<Map<String, Object>, GeneralException>) Class.forName(customImpl).getConstructor().newInstance();
                        accessCheck = unchecked;
                    } else {
                        accessCheck = PluginsUtil.instantiate(customImplPlugin, customImpl, Plugin.ClassExportType.UNCHECKED);
                    }
                    accessCheck.accept(scriptArguments);
                } catch(GeneralException e) {
                    throw e;
                } catch(Exception e) {
                    throw new GeneralException(e);
                }
            }
        });
    }

    /**
     * Returns true if the current user is a member of any of the given workgroups.
     * Note that this check is NOT recursive and does not check whether a workgroup
     * is a member of another workgroup.
     *
     * @param currentUser The user to check
     * @param workgroups The workgroups to check
     * @return true if the user is in the given workgroup
     */
    public static boolean matchesAnyWorkgroup(Identity currentUser, List<String> workgroups) {
        boolean matchesWorkgroup = false;
        List<Identity> userWorkgroups = currentUser.getWorkgroups();
        if (userWorkgroups != null) {
            for(Identity wg : userWorkgroups) {
                String wgName = wg.getName();
                if (workgroups.contains(wgName)) {
                    matchesWorkgroup = true;
                    break;
                }
            }
        }
        return matchesWorkgroup;
    }

    /**
     * Returns a new {@link FluentAccessCheck}, permitting a nice flow-y API for access checks.
     *
     * For example:
     *
     * ```
     *   AccessCheck
     *      .setup()
     *      .config(commonSecurityObject)
     *      .name("some name")
     *      .subject(pluginResource) // contains the logged-in username, so counts as a subject
     *      .target(targetIdentity)
     *      .isAllowed()
     * ```
     *
     * @return The fluent access check builder
     */
    public static FluentAccessCheck setup() {
        return new FluentAccessCheck();
    }
}
