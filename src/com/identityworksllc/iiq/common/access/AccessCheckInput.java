package com.identityworksllc.iiq.common.access;

import com.identityworksllc.iiq.common.CommonSecurityConfig;
import sailpoint.object.Identity;
import sailpoint.rest.plugin.BasePluginResource;
import sailpoint.tools.GeneralException;
import sailpoint.web.UserContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Access check input
 */
public final class AccessCheckInput {
    /**
     * Configuration
     */
    private CommonSecurityConfig configuration;

    /**
     * The plugin resource
     */
    private UserContext userContext;

    /**
     * The state from this access check
     */
    private Map<String, Object> state;

    /**
     * The target Identity being checked (may be null)
     */
    private Identity target;

    /**
     * The name of the thing being checked
     */
    private String thingName;

    /**
     * Constructs a basic access check input
     */
    public AccessCheckInput() {

    }

    /**
     * Copy constructor allowing override of an input
     *
     * @param parent The parent config
     * @param config The 'child' config to replace with
     */
    public AccessCheckInput(AccessCheckInput parent, CommonSecurityConfig config) {
        this(parent.userContext, parent.target, parent.thingName, config, parent.state);
    }

    /**
     * Access check input taking a plugin or target
     *
     * @param userContext    The user context (likely a BasePluginResource)
     * @param config         The config
     */
    public AccessCheckInput(UserContext userContext, CommonSecurityConfig config) {
        this(userContext, null, AccessCheck.ANONYMOUS_THING, config, null);
    }
    /**
     * Access check input taking a plugin or target
     *
     * @param userContext    The user context (likely a BasePluginResource)
     * @param target         The target
     * @param config         The config
     */
    public AccessCheckInput(UserContext userContext, Identity target, CommonSecurityConfig config) {
        this(userContext, target, AccessCheck.ANONYMOUS_THING, config, null);
    }

    /**
     * Access check input taking a plugin or target
     *
     * @param userContext    The user context (likely a BasePluginResource)
     * @param target         The target
     * @param thingName      The thing name
     * @param config         The config
     */
    public AccessCheckInput(UserContext userContext, Identity target, String thingName, CommonSecurityConfig config) {
        this(userContext, target, thingName, config, null);
    }

    /**
     * Access check input taking a plugin or target
     *
     * @param userContext    The user context (likely a BasePluginResource)
     * @param target         The target
     * @param thingName      The thing name
     * @param config         The config
     * @param state          Any persistent state in the access checks
     */
    public AccessCheckInput(UserContext userContext, Identity target, String thingName, CommonSecurityConfig config, Map<String, Object> state) {
        this.userContext = userContext;
        this.target = target;
        this.configuration = config;
        if (thingName == null || thingName.isEmpty()) {
            this.thingName = AccessCheck.ANONYMOUS_THING;
        } else {
            this.thingName = thingName;
        }
        this.state = (state != null) ? state : new HashMap<>();
    }

    /**
     * Gets the configuration object
     * @return The common security configuration object
     */
    public CommonSecurityConfig getConfiguration() {
        return configuration;
    }

    /**
     * @deprecated Use {@link #getUserContext()} instead
     * @return The configured plugin resource / user context
     */
    @Deprecated
    public UserContext getPluginResource() {
        return userContext;
    }

    public UserContext getUserContext() {
        return userContext;
    }

    public Map<String, Object> getState() {
        return state;
    }

    public Identity getTarget() throws GeneralException {
        if (this.target != null) {
            return target;
        } else {
            return userContext.getLoggedInUser();
        }
    }

    public String getThingName() {
        return thingName;
    }

    public AccessCheckInput setConfiguration(Map<String, Object> configuration) throws GeneralException {
        this.configuration = CommonSecurityConfig.decode(configuration);
        return this;
    }


    public AccessCheckInput setConfiguration(CommonSecurityConfig configuration) {
        this.configuration = configuration;
        return this;
    }

    @Deprecated
    public AccessCheckInput setPluginResource(BasePluginResource pluginResource) {
        return setUserContext(pluginResource);
    }

    public AccessCheckInput setUserContext(UserContext userContext) {
        this.userContext = userContext;
        return this;
    }

    public AccessCheckInput setState(Map<String, Object> state) {
        this.state = state;
        return this;
    }

    public AccessCheckInput setTarget(Identity target) {
        this.target = target;
        return this;
    }

    public AccessCheckInput setThingName(String thingName) {
        this.thingName = thingName;
        return this;
    }
}
