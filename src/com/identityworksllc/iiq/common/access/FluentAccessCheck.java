package com.identityworksllc.iiq.common.access;

import com.identityworksllc.iiq.common.CommonSecurityConfig;
import com.identityworksllc.iiq.common.auth.DummyAuthContext;
import com.identityworksllc.iiq.common.auth.DummyPluginResource;
import sailpoint.api.SailPointContext;
import sailpoint.object.Identity;
import sailpoint.tools.GeneralException;
import sailpoint.web.UserContext;

import java.util.Map;

public class FluentAccessCheck {
    private final AccessCheckInput input;

    public FluentAccessCheck() {
        this.input = new AccessCheckInput();
    }

    public AccessCheckResponse accessCheck() {
        return AccessCheck.accessCheck(this.input);
    }

    public FluentAccessCheck config(Map<String, Object> config) throws GeneralException {
        input.setConfiguration(config);
        return this;
    }

    public FluentAccessCheck config(CommonSecurityConfig config) {
        input.setConfiguration(config);
        return this;
    }

    public boolean isAllowed() {
        return accessCheck().isAllowed();
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

    public FluentAccessCheck subject(UserContext userContext) throws GeneralException {
        input.setUserContext(userContext);
        return this;
    }

    public FluentAccessCheck target(Identity target) {
        input.setTarget(target);
        return this;
    }
}
