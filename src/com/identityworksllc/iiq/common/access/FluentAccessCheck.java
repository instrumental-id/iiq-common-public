package com.identityworksllc.iiq.common.access;

import com.identityworksllc.iiq.common.CommonSecurityConfig;
import com.identityworksllc.iiq.common.auth.DummyAuthContext;
import com.identityworksllc.iiq.common.auth.DummyPluginResource;
import sailpoint.api.SailPointContext;
import sailpoint.object.Identity;
import sailpoint.tools.GeneralException;
import sailpoint.web.UserContext;

import java.util.Map;

/**
 * Implements a fluent API for access checks.
 *
 * Use {@link AccessCheck#setup()} to begin one of these chains.
 *
 * @see AccessCheck#setup()
 */
public class FluentAccessCheck {
    /**
     * The mid-construction input object
     */
    private final AccessCheckInput input;

    /**
     * Constructs a new FluentAccessCheck object with an empty input config
     */
    public FluentAccessCheck() {
        this.input = new AccessCheckInput();
    }

    /**
     * Validates and executes the constructed {@link AccessCheck}, returning
     * the response.
     *
     * @return The response from {@link AccessCheck#accessCheck(AccessCheckInput)}.
     * @throws AccessCheckException if the validation or access check fails
     * @see AccessCheck#accessCheck(AccessCheckInput) 
     */
    public AccessCheckResponse execute() throws AccessCheckException {
        input.validate();
        return AccessCheck.accessCheck(this.input);
    }

    /**
     * Sets the security config for this access check
     * @param config The security config
     * @return This object, for chaining
     * @throws GeneralException if parsing the Map into a {@link CommonSecurityConfig} fails
     * @see CommonSecurityConfig#decode(Map) 
     */
    public FluentAccessCheck config(Map<String, Object> config) throws GeneralException {
        input.setConfiguration(config);
        return this;
    }

    /**
     * Sets the security config for this access check
     * @param config The security config
     * @return This object, for chaining
     */
    public FluentAccessCheck config(CommonSecurityConfig config) {
        input.setConfiguration(config);
        return this;
    }

    /**
     * Sets debug mode to true for this access check
     * @return This object, for chaining
     * @see AccessCheckInput#setDebug(boolean)
     */
    public FluentAccessCheck debug() {
        input.setDebug(true);
        return this;
    }

    /**
     * Returns true if this access check passes for the current configuration
     * @return True if this access check passes, false otherwise
     * @throws AccessCheckException if the configuration is not valid
     * @see AccessCheckResponse#isAllowed()
     */
    public boolean isAllowed() throws AccessCheckException {
        return execute().isAllowed();
    }

    public FluentAccessCheck name(String name) {
        input.setThingName(name);
        return this;
    }

    public FluentAccessCheck state(Map<String, Object> state) {
        input.setState(state);
        return this;
    }

    public FluentAccessCheck state(String name, Object value) {
        input.putState(name, value);
        return this;
    }

    public FluentAccessCheck subject(SailPointContext ctx, Identity subject, String pluginName) {
        input.setUserContext(new DummyPluginResource(ctx, subject, pluginName));
        return this;
    }

    public FluentAccessCheck subject(SailPointContext ctx, Identity subject) {
        input.setUserContext(new DummyAuthContext(ctx, subject.getName()));
        return this;
    }

    public FluentAccessCheck subject(UserContext userContext) {
        input.setUserContext(userContext);
        return this;
    }

    public FluentAccessCheck target(String targetName) {
        input.setTarget(targetName);
        return this;
    }

    public FluentAccessCheck target(Identity target) {
        input.setTarget(target);
        return this;
    }
}
