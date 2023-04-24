package com.identityworksllc.iiq.common.auth;

import sailpoint.api.SailPointContext;
import sailpoint.object.Capability;
import sailpoint.object.Identity;
import sailpoint.rest.plugin.BasePluginResource;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.PathSegment;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Creates a fake plugin context for use outside of a plugin.
 */
public class DummyPluginResource extends BasePluginResource {
    /**
     * The context
     */
    private final SailPointContext context;

    /**
     * The Identity to use as the logged in user for this fake context
     */
    private final Identity loggedInUser;

    /**
     * The plugin name
     */
    private final String pluginName;

    /**
     * Constructs a new DummyPluginResource with the given auth data
     *
     * @param context The SailPointContext to return from {@link BasePluginResource#getContext()}
     * @param loggedInUser The Identity to return from various getLoggedIn... methods
     * @param pluginName The name of the plugin
     */
    public DummyPluginResource(SailPointContext context, Identity loggedInUser, String pluginName) {
        this.context = context;
        this.loggedInUser = loggedInUser;
        this.pluginName = pluginName;
        this.uriInfo = new DummyUriInfo();
    }

    /**
     * @see BasePluginResource#getContext()
     */
    @Override
    public SailPointContext getContext() {
        return context;
    }

    /**
     * @see BasePluginResource#getLoggedInUser()
     */
    @Override
    public Identity getLoggedInUser() throws GeneralException {
        return loggedInUser;
    }

    /**
     * @see BasePluginResource#getLoggedInUserCapabilities()
     */
    @Override
    public List<Capability> getLoggedInUserCapabilities() {
        List<Capability> capabilities = new ArrayList<>();
        try {
            capabilities.addAll(getLoggedInUser().getCapabilityManager().getEffectiveCapabilities());
        } catch(GeneralException e) {
            /* Ignore this */
        }
        return capabilities;
    }

    /**
     * @see BasePluginResource#getLoggedInUserName()
     */
    @Override
    public String getLoggedInUserName() throws GeneralException {
        return getLoggedInUser().getName();
    }

    /**
     * @see BasePluginResource#getLoggedInUserRights()
     */
    @Override
    public Collection<String> getLoggedInUserRights() {
        List<String> rights = new ArrayList<>();
        try {
            rights.addAll(getLoggedInUser().getCapabilityManager().getEffectiveFlattenedRights());
        } catch(GeneralException e) {
            /* Ignore this */
        }
        return rights;
    }

    /**
     * @see BasePluginResource#getPluginName()
     */
    @Override
    public String getPluginName() {
        return pluginName;
    }

    /**
     * @see BasePluginResource#getSettingBool(String)
     */
    @Override
    public boolean getSettingBool(String settingName) {
        if (Util.isNotNullOrEmpty(getPluginName())) {
            return super.getSettingBool(settingName);
        } else {
            return false;
        }
    }

    /**
     * @see BasePluginResource#getSettingInt(String)
     */
    @Override
    public int getSettingInt(String settingName) {
        if (Util.isNotNullOrEmpty(getPluginName())) {
            return super.getSettingInt(settingName);
        } else {
            return 0;
        }
    }

    /**
     * @see BasePluginResource#getSettingString(String)
     */
    @Override
    public String getSettingString(String settingName) {
        if (Util.isNotNullOrEmpty(getPluginName())) {
            return super.getSettingString(settingName);
        } else {
            return null;
        }
    }
}
