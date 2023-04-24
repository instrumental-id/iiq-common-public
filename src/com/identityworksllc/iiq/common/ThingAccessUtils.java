package com.identityworksllc.iiq.common;

import com.identityworksllc.iiq.common.access.AccessCheckInput;
import com.identityworksllc.iiq.common.access.AccessCheckResponse;
import com.identityworksllc.iiq.common.auth.DummyPluginResource;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.DynamicScopeMatchmaker;
import sailpoint.api.Matchmaker;
import sailpoint.api.SailPointContext;
import sailpoint.object.Capability;
import sailpoint.object.CustomGlobal;
import sailpoint.object.DynamicScope;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentitySelector;
import sailpoint.object.Script;
import sailpoint.rest.plugin.BasePluginResource;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implements the "Common Security" protocol that was originally part of the
 * UPE plugin. This allows more detailed authorization to check access to
 * various objects within IIQ.
 *
 * There are two users involved in thing access: an subject Identity and a target
 * Identity. The subject is the one doing the thing while the target is the one
 * the thing is being done to. Some actions may be 'self' actions, where both the
 * subject and the target are the same. Other actions don't have a 'target' concept
 * and are treated as 'self' actions.
 */
public class ThingAccessUtils {

    /**
     * The container object to hold the cached ThingAccessUtil results
     */
    private static final class SecurityResult {
        /**
         * The epoch millisecond timestamp when this object expires, one minute after creation
         */
        private final long expiration;

        /**
         * The actual cached result
         */
        private final AccessCheckResponse result;

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
        public AccessCheckResponse getResult() {
            return this.result;
        }

        /**
         * Returns true if the current epoch timestamp is later than the expiration date
         * @return True if expired
         */
        public boolean isExpired() {
            return System.currentTimeMillis() >= expiration;
        }
    }

    /**
     * The container object to identify the cached ThingAccessUtil inputs
     */
    private static final class SecurityCacheToken {
        /**
         * The CommonSecurityConfig object associated with the cached result
         */
        private final CommonSecurityConfig commonSecurityConfig;

        /**
         * The name of the source identity
         */
        private final String source;

        /**
         * The name of the target identity
         */
        private final String target;

        /**
         * The optional state map
         */
        private final Map<String, Object> state;

        /**
         * Constructs a new cache entry
         * @param csc The security config
         * @param source The source identity name
         * @param target The target identity name
         */
        public SecurityCacheToken(CommonSecurityConfig csc, String source, String target, Map<String, Object> state) {
            this.commonSecurityConfig = csc;
            this.target = target;
            this.source = source;
            this.state = new HashMap<>();

            if (state != null) {
                this.state.putAll(state);
            }
        }

        /**
         * Constructs a new cache entry based on the input
         * @param input The input object
         * @throws GeneralException the errors
         */
        public SecurityCacheToken(AccessCheckInput input) throws GeneralException {
            this(
                    input.getConfiguration(),
                    input.getPluginResource().getLoggedInUserName(),
                    (input.getTarget() == null || input.getTarget().getName() == null) ? "null" : input.getTarget().getName(),
                    input.getState()
            );
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SecurityCacheToken that = (SecurityCacheToken) o;
            return Objects.equals(commonSecurityConfig, that.commonSecurityConfig) && Objects.equals(target, that.target) && Objects.equals(source, that.source) && Objects.equals(state, that.state);
        }

        @Override
        public int hashCode() {
            return Objects.hash(commonSecurityConfig, target, source, state);
        }
    }

    private static final String CACHE_KEY = "idw.ThingAccessUtils.cache";

    /**
     * The logger
     */
    private static final Log log = LogFactory.getLog(ThingAccessUtils.class);

    /**
     * Returns true if the logged in user can access the item based on the Common Security configuration parameters.
     *
     * @param configuration The configuration for the field or button or other object
     * @return True if the user has access to the thing based on the configuration
     * @throws GeneralException if any check failures occur (this should be interpreted as "no access")
     */
    public static boolean checkThingAccess(BasePluginResource pluginContext, Map<String, Object> configuration) throws GeneralException {
        return checkThingAccess(pluginContext, null, "anonymous", configuration);
    }

    /**
     * Returns true if the logged in user can access the item based on the CommonSecurityConfig object
     *
     * @param config the CommonSecurityConfig object
     * @return True if the user has access to the thing based on the configuration
     * @throws GeneralException if any check failures occur (this should be interpreted as "no access")
     */
    public static boolean checkThingAccess(BasePluginResource pluginContext, CommonSecurityConfig config) throws GeneralException {
        return checkThingAccess(pluginContext, null, "anonymous", config);
    }

    /**
     * Returns true if the logged in user can access the item based on the common configuration parameters.
     *
     * @param configuration The configuration for the field or button or other object
     * @return True if the user has access to the thing based on the configuration
     * @throws GeneralException if any check failures occur (this should be interpreted as "no access")
     */
    public static boolean checkThingAccess(BasePluginResource pluginContext, Identity targetIdentity, Map<String, Object> configuration) throws GeneralException {
        return checkThingAccess(pluginContext, targetIdentity, "anonymous", configuration);
    }

    /**
     * Returns true if the logged in user can access the item based on the common configuration parameters.
     *
     * @param pluginContext A plugin REST API resource (or fake equivalent) used to get some details and settings. This must not be null.
     * @param targetIdentity The target identity
     * @param thingName The thing being checked
     * @param configuration The configuration for the field or button or other object
     * @return True if the user has access to the thing based on the configuration
     * @throws GeneralException if any check failures occur (this should be interpreted as "no access")
     */
    public static boolean checkThingAccess(BasePluginResource pluginContext, Identity targetIdentity, String thingName, Map<String, Object> configuration) throws GeneralException {
        Identity currentUser = pluginContext.getLoggedInUser();
        Identity target = targetIdentity;
        if (target == null) {
            target = currentUser;
        }
        if (configuration == null || configuration.isEmpty()) {
            log.debug("Configuration for " + thingName + " is empty; assuming that access is allowed");
            return true;
        }
        CommonSecurityConfig config = CommonSecurityConfig.decode(configuration);
        return checkThingAccess(pluginContext, target, thingName, config);
    }

    /**
     * Returns true if the logged in user can access the item based on the common configuration parameters.
     *
     * Results for the same CommonSecurityConfig, source, and target user will be cached for up to one minute
     * unless the CommonSecurityConfig object has noCache set to true.
     *
     * @param pluginContext A plugin REST API resource (or fake equivalent) used to get some details and settings. This must not be null.
     * @param target The target identity
     * @param thingName The thing being checked, entirely for logging purposes
     * @param config The configuration specifying security rights
     * @return True if the user has access to the thing based on the configuration
     * @throws GeneralException if any check failures occur (this should be interpreted as "no access")
     */
    public static boolean checkThingAccess(BasePluginResource pluginContext, Identity target, String thingName, CommonSecurityConfig config) throws GeneralException {
        AccessCheckInput input = new AccessCheckInput(pluginContext, target, thingName, config);

        return checkThingAccess(input).isAllowed();
    }

    /**
     * Returns an 'allowed' response if the logged in user can access the item based on the
     * common configuration parameters.
     *
     * Results for the same CommonSecurityConfig, source, and target user will be cached for up to one minute
     * unless the CommonSecurityConfig object has noCache set to true.
     *
     * @param input The input containing the configuration for the checkThingAccess utility
     * @return True if the user has access to the thing based on the configuration
     * @throws GeneralException if any check failures occur (this should be interpreted as "no access")
     */
    public static AccessCheckResponse checkThingAccess(AccessCheckInput input) throws GeneralException {
        if (input.getConfiguration() == null) {
            throw new IllegalArgumentException("An access check must contain a CommonSecurityConfig");
        }

        if (input.getPluginResource() == null) {
            throw new IllegalArgumentException("An access check must contain a plugin context for accessing the IIQ context and the logged in user");
        }

        AccessCheckResponse result;
        try {
            if (!input.getConfiguration().isNoCache()) {
                SecurityCacheToken cacheToken = new SecurityCacheToken(input);
                SecurityResult cachedResult = getCachedResult(cacheToken);
                if (cachedResult != null) {
                    return cachedResult.getResult();
                }
                result = checkThingAccessImpl(input, null);
                getCacheMap().put(cacheToken, new SecurityResult(result));
            } else {
                result = checkThingAccessImpl(input, null);
            }
        } catch(Exception e) {
            result = new AccessCheckResponse();
            result.denyMessage("Caught an exception evaluating criteria: " + e.getMessage());
            log.error("Caught an exception evaluating access criteria", e);
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
    private static AccessCheckResponse checkThingAccessImpl(final AccessCheckInput input, AccessCheckResponse result) throws GeneralException {
        if (result == null) {
            result = new AccessCheckResponse();
        }
        BasePluginResource pluginContext = input.getPluginResource();

        final Identity currentUser = pluginContext.getLoggedInUser();
        final Identity target = (input.getTarget() != null) ? input.getTarget() : currentUser;
        final String currentUserName = pluginContext.getLoggedInUserName();
        final CommonSecurityConfig config = input.getConfiguration();
        final String thingName = input.getThingName();

        if (config.isDisabled()) {
            result.denyMessage("Access denied to " + thingName + " because the configuration is marked disabled");
        }
        if (result.isAllowed() && Utilities.isNotEmpty(config.getOneOf())) {
            boolean anyMatch = false;
            for(CommonSecurityConfig sub : config.getOneOf()) {
                AccessCheckInput child = new AccessCheckInput(input, sub);
                AccessCheckResponse childResponse = checkThingAccessImpl(child, null);
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
                AccessCheckResponse childResponse = checkThingAccessImpl(child, null);
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
                AccessCheckResponse childResponse = checkThingAccessImpl(child, null);
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
            boolean isDisabled = pluginContext.getSettingBool(config.getSettingOffSwitch());
            if (isDisabled) {
                result.denyMessage("Access denied to " + thingName + " because the feature " + config.getSettingOffSwitch() + " is disabled in plugin settings");
            }
        }
        if (result.isAllowed() && config.getAccessCheckScript() != null) {
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

            Object output = pluginContext.getContext().runRule(config.getAccessCheckRule(), scriptArguments);
            // If the script returns a non-null value, it will be considered the authoritative
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
                        result.addMessage("Matching SPRight: " + right);
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
                    result.addMessage("Matching Capability: " + cap.getName());
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
                        result.addMessage("Matching excluded SPRight: " + right);
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
                    result.addMessage("Matching excluded Capability: " + cap.getName());
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
            }
        }
        if (result.isAllowed() && config.getAccessCheckSelector() != null) {
            IdentitySelector selector = config.getAccessCheckSelector();
            Matchmaker matchmaker = new Matchmaker(pluginContext.getContext());
            if (!matchmaker.isMatch(selector, currentUser)) {
                result.denyMessage("Access denied to " + thingName + " because subject user " + currentUserName + " does not match the access check selector");
            }
        }
        if (result.isAllowed() && Util.isNotNullOrEmpty(config.getMirrorQuicklinkPopulation())) {
            String quicklinkPopulation = config.getMirrorQuicklinkPopulation();
            if (Util.isNotNullOrEmpty(quicklinkPopulation)) {
                DynamicScopeMatchmaker dynamicScopeMatchmaker = new DynamicScopeMatchmaker(pluginContext.getContext());
                DynamicScope dynamicScope = pluginContext.getContext().getObject(DynamicScope.class, quicklinkPopulation);
                boolean matchesDynamicScope = dynamicScopeMatchmaker.isMatch(dynamicScope, currentUser);
                if (matchesDynamicScope) {
                    result.addMessage("Subject user matches DynamicScope " + quicklinkPopulation);
                    DynamicScope.PopulationRequestAuthority populationRequestAuthority = dynamicScope.getPopulationRequestAuthority();
                    if (populationRequestAuthority != null && !populationRequestAuthority.isAllowAll()) {
                        matchesDynamicScope = dynamicScopeMatchmaker.isMember(currentUser, target, populationRequestAuthority);
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
                        result.addMessage("Excluded right matched: " + right);
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
                        result.addMessage("Excluded capability matched: " + capability.getName());
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
        return result;
    }

    /**
     * An optional clear-cache method that can be used by plugin code
     */
    public static void clearCachedResults() {
        ConcurrentHashMap<SecurityCacheToken, SecurityResult> cacheMap = getCacheMap();
        cacheMap.clear();
    }

    /**
     * Creates a fake plugin context for use with {@link ThingAccessUtils#checkThingAccess(BasePluginResource, Identity, String, Map)} outside of a plugin. This constructs a new instance of a dummy BasePluginResource web service endpoint class.
     * @param context The SailPointContext to return from {@link BasePluginResource#getContext()}
     * @param loggedInUser The Identity to return from various getLoggedIn... methods
     * @return The fake plugin resource
     */
    public static BasePluginResource createFakePluginContext(final SailPointContext context, final Identity loggedInUser, String pluginName) {
        return new DummyPluginResource(context, loggedInUser, pluginName);
    }

    /**
     * Creates the cache map, which should be stored in CustomGlobal. If it does not exist,
     * we create and store a new one. Since this is just for efficiency, we don't really
     * care about synchronization.
     *
     * @return The cache map
     */
    private static ConcurrentHashMap<SecurityCacheToken, SecurityResult> getCacheMap() {
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<SecurityCacheToken, SecurityResult> cacheMap = (ConcurrentHashMap<SecurityCacheToken, SecurityResult>) CustomGlobal.get(CACHE_KEY);
        if (cacheMap == null) {
            cacheMap = new ConcurrentHashMap<>();
            CustomGlobal.put(CACHE_KEY, cacheMap);
        }
        return cacheMap;
    }

    /**
     * Gets the cached result for the given security context
     * @param securityContext The security context
     * @return The cached result, if one exists
     */
    private static SecurityResult getCachedResult(SecurityCacheToken securityContext) {
        ConcurrentHashMap<SecurityCacheToken, SecurityResult> cacheMap = getCacheMap();
        SecurityResult cachedResult = cacheMap.get(securityContext);
        if (cachedResult != null && !cachedResult.isExpired()) {
            return cachedResult;
        }
        return null;
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

}
