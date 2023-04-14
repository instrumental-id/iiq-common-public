package com.identityworksllc.iiq.common.minimal.plugin;

import sailpoint.authorization.UnauthorizedAccessException;
import sailpoint.tools.GeneralException;

/**
 * A functional interface to handle plugin authorization at a class level
 */
@FunctionalInterface
public interface PluginAuthorizationCheck {
    /**
     * Executes the authorization check, intended to throw a {@link UnauthorizedAccessException} if not allowed
     *
     * @throws UnauthorizedAccessException If anything fails
     * @throws GeneralException            if anything goes wrong
     */
    void checkAccess() throws GeneralException;
}
