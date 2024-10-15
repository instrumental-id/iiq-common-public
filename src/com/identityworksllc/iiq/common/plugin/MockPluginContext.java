package com.identityworksllc.iiq.common.plugin;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.SailPointContext;
import sailpoint.object.Capability;
import sailpoint.object.Identity;
import sailpoint.object.Plugin;
import sailpoint.rest.plugin.BasePluginResource;
import sailpoint.tools.GeneralException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * A mock plugin resource to use in any situation where an API requires one
 * but your code is not running in a plugin context. This must be associated with a
 * real plugin (by name).
 *
 * In particular, {@link com.identityworksllc.iiq.common.ThingAccessUtils} requires a
 * plugin context, which this can provide.
 *
 * @deprecated Use {@link com.identityworksllc.iiq.common.auth.DummyPluginResource}
 */
@Deprecated
public class MockPluginContext extends BasePluginResource {
    private static final Log log = LogFactory.getLog(MockPluginContext.class);

    private final SailPointContext context;
    private final Identity loggedInUser;
    private final String pluginName;

    /**
     * Constructor for the mock plugin context
     *
     * @param context      The Sailpoint context to use
     * @param loggedInUser The logged in user to simulate
     * @param pluginName   The plugin name, which must be real
     * @throws GeneralException if any failures occur finding the plugin
     */
    public MockPluginContext(SailPointContext context, Identity loggedInUser, String pluginName) throws GeneralException {
        this.context = context;
        this.loggedInUser = loggedInUser;
        this.pluginName = pluginName;

        super.setPreAuth(true);

        Plugin plugin = context.getObject(Plugin.class, pluginName);
        if (plugin == null) {
            log.warn("The plugin name specified, '" + pluginName + "', is not a valid plugin. The 'getSetting' methods will not work properly.");
        }
    }

    /**
     * Returns the context previously passed to the contructor
     *
     * @return The context
     */
    @Override
    public SailPointContext getContext() {
        return context;
    }

    @Override
    protected String[] getCredentials() {
        return new String[]{getLoggedInUser().getName(), getLoggedInUser().getPassword()};
    }

    /**
     * Returns the default system Locale
     *
     * @return The default system locale
     */
    @Override
    public Locale getLocale() {
        return Locale.getDefault();
    }

    @Override
    public Identity getLoggedInUser() {
        return loggedInUser;
    }

    @Override
    public List<Capability> getLoggedInUserCapabilities() {
        return new ArrayList<>(getLoggedInUser().getCapabilityManager().getEffectiveCapabilities());
    }

    @Override
    public String getLoggedInUserName() {
        return getLoggedInUser().getName();
    }

    @Override
    public Collection<String> getLoggedInUserRights() {
        return new ArrayList<>(getLoggedInUser().getCapabilityManager().getEffectiveFlattenedRights());
    }

    /**
     * Returns the name of the real plugin we are simulating
     *
     * @return The name of the plugin provided in the constructor
     */
    @Override
    public String getPluginName() {
        return pluginName;
    }

    /**
     * Not implemented
     *
     * @throws UnsupportedOperationException on any invocation
     */
    @Override
    public HttpServletRequest getRequest() {
        throw new UnsupportedOperationException();
    }

    /**
     * Not implemented
     *
     * @throws UnsupportedOperationException on any invocation
     */
    @Override
    public HttpSession getSession() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the default system time zone
     *
     * @return The default system time zone
     */
    @Override
    public TimeZone getUserTimeZone() {
        return TimeZone.getDefault();
    }
}
