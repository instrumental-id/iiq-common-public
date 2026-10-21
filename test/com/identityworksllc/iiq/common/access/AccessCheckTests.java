package com.identityworksllc.iiq.common.access;

import com.identityworksllc.iiq.common.CommonSecurityConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import sailpoint.object.Capability;
import sailpoint.object.Configuration;
import sailpoint.object.Identity;
import sailpoint.server.Environment;
import sailpoint.web.UserContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AccessCheck} and {@link CommonSecurityConfig}
 */
public class AccessCheckTests {
    private static boolean check(UserContext userContext, CommonSecurityConfig config) {
        config.setNoCache(true);
        return AccessCheck.accessCheck(new AccessCheckInput(userContext, config)).isAllowed();
    }

    @BeforeAll
    public static void setUpBranding() {
        // Identity's static initializer needs a branding, which normally comes from iiq.properties
        if (System.getProperty("branding") == null) {
            System.setProperty("branding", "ags");
        }
    }

    private static UserContext userWith(List<String> rights, List<String> capabilityNames) throws Exception {
        Identity user = mock(Identity.class);
        when(user.getName()).thenReturn("subject");

        List<Capability> capabilities = new ArrayList<>();
        for (String name : capabilityNames) {
            Capability cap = mock(Capability.class);
            when(cap.getName()).thenReturn(name);
            capabilities.add(cap);
        }

        UserContext userContext = mock(UserContext.class);
        when(userContext.getLoggedInUser()).thenReturn(user);
        when(userContext.getLoggedInUserName()).thenReturn("subject");
        when(userContext.getLoggedInUserRights()).thenReturn(rights);
        when(userContext.getLoggedInUserCapabilities()).thenReturn(capabilities);
        return userContext;
    }

    private MockedStatic<Configuration> configurationStatic;
    private MockedStatic<Environment> environmentStatic;

    @Test
    public void excludedCapabilitiesAllowsUserWithoutExcludedCapability() throws Exception {
        CommonSecurityConfig config = new CommonSecurityConfig();
        config.setExcludedCapabilities(Collections.singletonList("BadCap"));

        assertTrue(check(userWith(Collections.emptyList(), Collections.singletonList("OtherCap")), config));
    }

    @Test
    public void excludedCapabilitiesDeniesUserWithExcludedCapability() throws Exception {
        CommonSecurityConfig config = new CommonSecurityConfig();
        config.setExcludedCapabilities(Collections.singletonList("BadCap"));

        assertFalse(check(userWith(Collections.emptyList(), Collections.singletonList("BadCap")), config));
    }

    @Test
    public void excludedCapabilitiesDoesNotDenyUserMatchingRequiredCapability() throws Exception {
        CommonSecurityConfig config = new CommonSecurityConfig();
        config.setRequiredCapabilities(Collections.singletonList("GoodCap"));
        config.setExcludedCapabilities(Collections.singletonList("BadCap"));

        assertTrue(check(userWith(Collections.emptyList(), Collections.singletonList("GoodCap")), config));
    }

    @Test
    public void excludedRightsAllowsUserWithoutExcludedRight() throws Exception {
        CommonSecurityConfig config = new CommonSecurityConfig();
        config.setExcludedRights(Collections.singletonList("BadRight"));

        assertTrue(check(userWith(Collections.singletonList("OtherRight"), Collections.emptyList()), config));
    }

    @Test
    public void excludedRightsDeniesUserWithExcludedRight() throws Exception {
        CommonSecurityConfig config = new CommonSecurityConfig();
        config.setExcludedRights(Collections.singletonList("BadRight"));

        assertFalse(check(userWith(Collections.singletonList("BadRight"), Collections.emptyList()), config));
    }

    @Test
    public void excludedRightsDoesNotDenyUserMatchingRequiredRight() throws Exception {
        CommonSecurityConfig config = new CommonSecurityConfig();
        config.setRequiredRights(Collections.singletonList("GoodRight"));
        config.setExcludedRights(Collections.singletonList("BadRight"));

        assertTrue(check(userWith(Collections.singletonList("GoodRight"), Collections.emptyList()), config));
    }

    @BeforeEach
    public void setUp() {
        configurationStatic = Mockito.mockStatic(Configuration.class);
        configurationStatic.when(Configuration::getSystemConfig).thenReturn(mock(Configuration.class));

        // No IIQ server is running, so report no Environment rather than starting one
        environmentStatic = Mockito.mockStatic(Environment.class);
        environmentStatic.when(Environment::getEnvironment).thenReturn(null);
    }

    @AfterEach
    public void tearDown() {
        if (environmentStatic != null) {
            environmentStatic.close();
        }
        if (configurationStatic != null) {
            configurationStatic.close();
        }
    }

    @Test
    public void toMapDiffersWhenOnlyMirrorRoleDiffers() {
        CommonSecurityConfig open = new CommonSecurityConfig();
        CommonSecurityConfig restricted = new CommonSecurityConfig();
        restricted.setMirrorRole("Some Role");

        assertNotEquals(open.toMap(), restricted.toMap());
    }

    @Test
    public void toMapDiffersWhenOnlyValidTargetFilterDiffers() {
        CommonSecurityConfig open = new CommonSecurityConfig();
        CommonSecurityConfig restricted = new CommonSecurityConfig();
        restricted.setValidTargetFilter("type == \"Employee\"");

        assertNotEquals(open.toMap(), restricted.toMap());
    }

    @Test
    public void toMapIncludesMirrorRoleAndValidTargetFilter() {
        CommonSecurityConfig config = new CommonSecurityConfig();
        config.setMirrorRole("Some Role");
        config.setValidTargetFilter("type == \"Employee\"");

        Map<String, Object> map = config.toMap();
        assertEquals("Some Role", map.get("mirrorRole"));
        assertEquals("type == \"Employee\"", map.get("validTargetFilter"));
    }
}
