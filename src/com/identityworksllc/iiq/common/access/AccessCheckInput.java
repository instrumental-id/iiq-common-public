package com.identityworksllc.iiq.common.access;

import com.identityworksllc.iiq.common.CommonSecurityConfig;
import sailpoint.object.Identity;
import sailpoint.rest.plugin.BasePluginResource;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
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
     * The debug flag
     */
    private boolean debug;

    /**
     * The state from this access check
     */
    private Map<String, Object> state;

    /**
     * The target Identity being checked (may be null)
     */
    private transient Identity target;

    /**
     * The target Identity name
     */
    private String targetName;

    /**
     * The target object type
     * TODO use this
     */
    private String targetType;

    /**
     * The name of the thing being checked
     */
    private String thingName;

    /**
     * The UserContext, specifying which user is the subject of the access check.
     * If this is also a {@link sailpoint.plugin.PluginContext}, as it would be if
     * it is a {@link BasePluginResource}, then it will be used for plugin-specific
     * checks.
     */
    private UserContext userContext;

    /**
     * Constructs a basic access check input
     */
    public AccessCheckInput() {
        this.thingName = AccessCheck.ANONYMOUS_THING;
        this.debug = false;
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
     * @param userContext    The user context (likely a {@link BasePluginResource} or {@link com.identityworksllc.iiq.common.auth.DummyAuthContext})
     * @param target         The target
     * @param thingName      The thing name
     * @param config         The config
     * @param state          Any persistent state in the access checks
     */
    public AccessCheckInput(UserContext userContext, Identity target, String thingName, CommonSecurityConfig config, Map<String, Object> state) {
        this.userContext = userContext;
        this.target = target;
        if (this.target != null) {
            this.targetName = target.getName();
        }
        this.configuration = config;
        if (thingName == null || thingName.isEmpty()) {
            this.thingName = AccessCheck.ANONYMOUS_THING;
        } else {
            this.thingName = thingName;
        }
        this.state = (state != null) ? state : new HashMap<>();
        this.debug = false;
    }

    /**
     * Gets the configuration object
     * @return The common security configuration object
     * @see CommonSecurityConfig
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

    /**
     * Gets the state {@link Map}
     * @return The state Map
     */
    public Map<String, Object> getState() {
        return state;
    }

    /**
     * Gets the stored target Identity if one exists. If one does not exist,
     * returns the subject Identity.
     *
     * @return The target Identity
     * @throws GeneralException if anything fails
     */
    public Identity getTarget() throws GeneralException {
        if (this.target != null) {
            return target;
        } else if (Util.isNotNullOrEmpty(targetName)) {
            this.target = userContext.getContext().getObject(Identity.class, targetName);
            return target;
        } else {
            return userContext.getLoggedInUser();
        }
    }

    /**
     * Gets the currently configured thing name
     * @return The configured thing name
     */
    public String getThingName() {
        return thingName;
    }

    /**
     * Gets the user context
     * @return The user context, containing the subject user
     */
    public UserContext getUserContext() {
        return userContext;
    }

    /**
     * Returns the value of the debug flag on this access check request
     * @return The debug flag
     */
    public boolean isDebug() {
        return debug;
    }

    /**
     * Puts a value into the access check state map
     * @param name The key
     * @param value The value
     * @return This object, for chaining
     */
    public AccessCheckInput putState(String name, Object value) {
        if (this.state == null) {
            this.state = new HashMap<>();
        }

        this.state.put(name, value);
        return this;
    }

    /**
     * Sets the common security configuration as a Map, which will be decoded.
     *
     * @see CommonSecurityConfig#decode(Map)
     * @see com.identityworksllc.iiq.common.ObjectMapper#decode(Map)
     * @param configuration The configuration to decode and store
     * @return This object, for chaining
     * @throws GeneralException if the configuration cannot be decoded
     */
    public AccessCheckInput setConfiguration(Map<String, Object> configuration) throws GeneralException {
        this.configuration = CommonSecurityConfig.decode(configuration);
        return this;
    }

    /**
     * Sets the common security configuration
     * @param configuration The common security configuration
     * @return This object, for chaining
     */
    public AccessCheckInput setConfiguration(CommonSecurityConfig configuration) {
        this.configuration = configuration;
        return this;
    }

    /**
     * Sets the debug flag on the access check
     * @param debug The debug flag to set
     */
    public AccessCheckInput setDebug(boolean debug) {
        this.debug = debug;
        return this;
    }

    /**
     * Sets the plugin resource, simply forwarding to {@link #setUserContext(UserContext)},
     * because {@link BasePluginResource} is an instance of {@link UserContext}.
     *
     * @param pluginResource The plugin resource to set
     * @return This object, for chaining
     * @throws GeneralException if a targetName has been set and loading the Identity fails
     */
    @Deprecated
    public AccessCheckInput setPluginResource(BasePluginResource pluginResource) throws GeneralException {
        return setUserContext(pluginResource);
    }

    /**
     * Sets the state map, which will be provided to any access check rules or
     * access check scripts.
     *
     * @param state The access check state to set; this map will be copied
     * @return This object, for chaining
     */
    public AccessCheckInput setState(Map<String, Object> state) {
        if (state == null) {
            this.state = new HashMap<>();
        } else {
            this.state = new HashMap<>(state);
        }
        return this;
    }

    /**
     * Sets the target Identity and target name
     *
     * @param target The target Identity
     * @return This object, for chaining
     */
    public AccessCheckInput setTarget(Identity target) {
        this.target = target;
        this.targetName = target.getName();
        return this;
    }

    /**
     * Sets the target name or ID. This will be resolved to an {@link Identity}
     * on the first call to {@link #getTarget()}.
     *
     * @param targetName The target's name or ID
     * @return This object, for chaining
     */
    public AccessCheckInput setTarget(String targetName){
        this.targetName = targetName;
        return this;
    }

    /**
     * Sets the thing name, for caching and display purposes
     * @param thingName The thing name
     * @return This object, for chaining
     */
    public AccessCheckInput setThingName(String thingName) {
        this.thingName = thingName;
        return this;
    }

    /**
     * Sets the user context, containing the 'subject' of the access check
     * @param userContext The context specifying the subject of the access check
     * @return This object, for chaining
     */
    public AccessCheckInput setUserContext(UserContext userContext) {
        this.userContext = userContext;
        return this;
    }

    /**
     * Validates the configuration before it executes
     * @throws AccessCheckException if validation fails
     */
    public void validate() throws AccessCheckException {
        if (this.userContext == null) {
            throw new AccessCheckException("UserContext is required");
        }
        if (this.configuration == null) {
            throw new AccessCheckException("Configuration is required");
        }
    }
}
