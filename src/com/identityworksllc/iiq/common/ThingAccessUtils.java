package com.identityworksllc.iiq.common;

import com.identityworksllc.iiq.common.access.AccessCheck;
import com.identityworksllc.iiq.common.access.AccessCheckInput;
import com.identityworksllc.iiq.common.auth.DummyPluginResource;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.SailPointContext;
import sailpoint.object.*;
import sailpoint.rest.plugin.BasePluginResource;
import sailpoint.tools.GeneralException;
import sailpoint.web.UserContext;

import java.util.Map;

/**
 * Implements the "Common Security" protocol described in the documentation. This
 * allows more detailed authorization to check access to various objects within IIQ.
 *
 * There are two users involved in thing access: an subject Identity and a target
 * Identity. The subject is the one doing the thing while the target is the one
 * the thing is being done to. Some actions may be 'self' actions, where both the
 * subject and the target are the same. Other actions don't have a 'target' concept
 * and are treated as 'self' actions.
 *
 * See the `COMMON-SECURITY.adoc` documentation.
 *
 * @see AccessCheck
 */
@SuppressWarnings("unused")
public final class ThingAccessUtils {

    /**
     * The logger
     */
    private static final Log log = LogFactory.getLog(ThingAccessUtils.class);

    /**
     * Returns true if the logged in user can access the item based on the Common Security configuration parameters.
     *
     * @param pluginContext The plugin context, which provides user details
     * @param configuration The configuration for the field or button or other object
     * @return True if the user has access to the thing based on the configuration
     * @throws GeneralException if any check failures occur (this should be interpreted as "no access")
     */
    public static boolean checkThingAccess(UserContext pluginContext, Map<String, Object> configuration) throws GeneralException {
        return checkThingAccess(pluginContext, null, AccessCheck.ANONYMOUS_THING, configuration);
    }

    /**
     * Returns true if the logged in user can access the item based on the Common Security configuration parameters.
     *
     * @param pluginContext The plugin context, which provides user details
     * @param configuration The configuration for the field or button or other object
     * @return True if the user has access to the thing based on the configuration
     * @throws GeneralException if any check failures occur (this should be interpreted as "no access")
     */
    public static boolean checkThingAccess(BasePluginResource pluginContext, Map<String, Object> configuration) throws GeneralException {
        return checkThingAccess(pluginContext, null, AccessCheck.ANONYMOUS_THING, configuration);
    }

    /**
     * Returns true if the logged in user can access the item based on the CommonSecurityConfig object
     *
     * @param pluginContext The plugin context, which provides user details
     * @param config the CommonSecurityConfig object
     * @return True if the user has access to the thing based on the configuration
     * @throws GeneralException if any check failures occur (this should be interpreted as "no access")
     */
    public static boolean checkThingAccess(UserContext pluginContext, CommonSecurityConfig config) throws GeneralException {
        return checkThingAccess(pluginContext, null, AccessCheck.ANONYMOUS_THING, config);
    }

    /**
     * Returns true if the logged in user can access the item based on the CommonSecurityConfig object
     *
     * @param pluginContext The plugin context, which provides user details
     * @param config the CommonSecurityConfig object
     * @return True if the user has access to the thing based on the configuration
     * @throws GeneralException if any check failures occur (this should be interpreted as "no access")
     */
    public static boolean checkThingAccess(BasePluginResource pluginContext, CommonSecurityConfig config) throws GeneralException {
        return checkThingAccess(pluginContext, null, AccessCheck.ANONYMOUS_THING, config);
    }

    /**
     * Returns true if the logged in user can access the item based on the common configuration parameters.
     *
     * @param pluginContext The login context, which provides user details
     * @param targetIdentity The target identity for the action (as opposed to the actor)
     * @param configuration The configuration for the field or button or other object
     * @return True if the user has access to the thing based on the configuration
     * @throws GeneralException if any check failures occur (this should be interpreted as "no access")
     */
    public static boolean checkThingAccess(UserContext pluginContext, Identity targetIdentity, Map<String, Object> configuration) throws GeneralException {
        return checkThingAccess(pluginContext, targetIdentity, AccessCheck.ANONYMOUS_THING, configuration);
    }

    /**
     * Returns true if the logged in user can access the item based on the common configuration parameters.
     *
     * @param pluginContext The plugin context, which provides user details
     * @param targetIdentity The target identity for the action (as opposed to the actor)
     * @param configuration The configuration for the field or button or other object
     * @return True if the user has access to the thing based on the configuration
     * @throws GeneralException if any check failures occur (this should be interpreted as "no access")
     */
    public static boolean checkThingAccess(BasePluginResource pluginContext, Identity targetIdentity, Map<String, Object> configuration) throws GeneralException {
        return checkThingAccess(pluginContext, targetIdentity, AccessCheck.ANONYMOUS_THING, configuration);
    }

    /**
     * Returns true if the logged in user can access the item based on the common configuration parameters.
     *
     * @param pluginContext The plugin context, which provides user details
     * @param targetIdentity The target identity for the action (as opposed to the actor)
     * @param configuration The configuration for the field or button or other object
     * @return True if the user has access to the thing based on the configuration
     * @throws GeneralException if any check failures occur (this should be interpreted as "no access")
     */
    public static boolean checkThingAccess(BasePluginResource pluginContext, Identity targetIdentity, String thingName, Map<String, Object> configuration) throws GeneralException {
        return checkThingAccess((UserContext) pluginContext, targetIdentity, AccessCheck.ANONYMOUS_THING, configuration);
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
    public static boolean checkThingAccess(UserContext pluginContext, Identity targetIdentity, String thingName, Map<String, Object> configuration) throws GeneralException {
        Identity currentUser = pluginContext.getLoggedInUser();
        Identity target = targetIdentity;
        if (target == null) {
            target = currentUser;
        }
        if (configuration == null || configuration.isEmpty()) {
            Configuration systemConfig = Configuration.getSystemConfig();
            boolean defaultDeny = systemConfig.getBoolean("IIQCommon.ThingAccessUtils.denyOnEmpty", false);
            if (defaultDeny) {
                log.debug("Configuration for " + thingName + " is empty; assuming that access is NOT allowed");
                return false;
            } else {
                log.debug("Configuration for " + thingName + " is empty; assuming that access is allowed");
                return true;
            }
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
    public static boolean checkThingAccess(UserContext pluginContext, Identity target, String thingName, CommonSecurityConfig config) throws GeneralException {
        AccessCheckInput input = new AccessCheckInput(pluginContext, target, thingName, config);

        return AccessCheck.accessCheck(input).isAllowed();
    }

    /**
     * An optional clear-cache method that can be used by plugin code
     */
    public static void clearCachedResults() {
        AccessCheck.clearCachedResults();
    }

    /**
     * Creates a fake plugin context for use with {@link ThingAccessUtils#checkThingAccess(UserContext, Identity, String, Map)} outside of a plugin. This constructs a new instance of a dummy BasePluginResource web service endpoint class.
     * @param context The SailPointContext to return from {@link BasePluginResource#getContext()}
     * @param loggedInUser The Identity to return from various getLoggedIn... methods
     * @param pluginName The name of the plugin to include in the fake plugin context
     * @return The fake plugin resource
     */
    public static BasePluginResource createFakePluginContext(final SailPointContext context, final Identity loggedInUser, String pluginName) {
        return new DummyPluginResource(context, loggedInUser, pluginName);
    }

}
