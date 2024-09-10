package com.identityworksllc.iiq.common.service;

import sailpoint.api.SailPointContext;
import sailpoint.tools.GeneralException;

/**
 * Functional interface to invoke the implementation of your service after doing
 * pre-work. This is intended for use with {@link BaseCommonService}, as the
 * value of {@link BaseCommonService#invoker}.
 *
 * In your subclass of {@link BaseCommonService}, you may implement your behavior
 * by either passing custom behavior as 'invoker' or by overriding 'implementation'.
 */
@FunctionalInterface
public interface ServiceImplementationInvoker {
    /**
     * Invokes the service implementation after doing whatever pre-work is required.
     *
     * @param context The IIQ context
     */
    void invokeImplementation(SailPointContext context) throws GeneralException;
}
