package com.identityworksllc.iiq.common.plugin;

import com.identityworksllc.iiq.common.service.BaseCommonService;
import com.identityworksllc.iiq.common.service.BaseServiceImplementation;
import sailpoint.api.SailPointContext;
import sailpoint.plugin.PluginContext;

/**
 * Abstract super-class for base plugin services. This class provides some minimal
 * services, such as tracking execution counts, then delegates to the {@link #implementation(SailPointContext)} method.
 *
 * TODO remove this sometime in 2024
 *
 * @deprecated Extend {@link BaseCommonService} and implement {@link PluginContext} instead so I don't have to keep maintaining this code twice.
 */
@Deprecated
public abstract class BaseCommonPluginService extends BaseCommonService implements BaseServiceImplementation, PluginContext {

}
