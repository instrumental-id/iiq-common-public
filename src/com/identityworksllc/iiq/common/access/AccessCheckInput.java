package com.identityworksllc.iiq.common.access;

import com.identityworksllc.iiq.common.CommonSecurityConfig;
import com.identityworksllc.iiq.common.ThingAccessUtils;
import sailpoint.object.Identity;
import sailpoint.rest.plugin.BasePluginResource;

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
    private BasePluginResource pluginResource;

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
        this.state = parent.state;
        this.pluginResource = parent.pluginResource;
        this.thingName = parent.thingName;
        this.target = parent.target;
        this.configuration = config;
    }

    /**
     * Access check input taking a plugin or target
     *
     * @param pluginResource The plugin resource
     * @param target         The target
     * @param config         The config
     */
    public AccessCheckInput(BasePluginResource pluginResource, Identity target, CommonSecurityConfig config) {
        this.pluginResource = pluginResource;
        this.target = target;
        this.configuration = config;
    }

    /**
     * Access check input taking a plugin or target
     *
     * @param pluginResource The plugin resource
     * @param target         The target
     * @param thingName      The thing name
     * @param config         The config
     */
    public AccessCheckInput(BasePluginResource pluginResource, Identity target, String thingName, CommonSecurityConfig config) {
        this.pluginResource = pluginResource;
        this.target = target;
        this.configuration = config;
        this.thingName = thingName;
    }

    public CommonSecurityConfig getConfiguration() {
        return configuration;
    }

    public BasePluginResource getPluginResource() {
        return pluginResource;
    }

    public Map<String, Object> getState() {
        return state;
    }

    public Identity getTarget() {
        return target;
    }

    public String getThingName() {
        return thingName;
    }

    public void setConfiguration(CommonSecurityConfig configuration) {
        this.configuration = configuration;
    }

    public void setPluginResource(BasePluginResource pluginResource) {
        this.pluginResource = pluginResource;
    }

    public void setState(Map<String, Object> state) {
        this.state = state;
    }

    public void setTarget(Identity target) {
        this.target = target;
    }

    public void setThingName(String thingName) {
        this.thingName = thingName;
    }
}
