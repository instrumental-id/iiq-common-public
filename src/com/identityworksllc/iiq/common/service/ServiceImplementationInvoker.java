package com.identityworksllc.iiq.common.service;

import sailpoint.api.SailPointContext;
import sailpoint.tools.GeneralException;

/**
 * Functional interface to invoke the implementation
 */
@FunctionalInterface
public interface ServiceImplementationInvoker {
    /**
     * Invokes the service implementation after doing whatever pre-work is required.
     * By default, this simply invokes this::implementation.
     */
    void invokeImplementation(SailPointContext context) throws GeneralException;
}
