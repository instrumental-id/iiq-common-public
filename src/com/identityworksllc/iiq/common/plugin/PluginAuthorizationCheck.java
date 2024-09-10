package com.identityworksllc.iiq.common.plugin;

import sailpoint.authorization.UnauthorizedAccessException;
import sailpoint.tools.GeneralException;

/**
 * A functional interface to handle plugin REST API authorization.
 *
 * You may invoke this in your class that extends {@link BaseCommonPluginResource}
 * by passing it to {@link BaseCommonPluginResource#setPluginAuthorizationCheck(PluginAuthorizationCheck)}
 * in the class's constructor. Any supplied {@link PluginAuthorizationCheck} will be invoked at the start of `handle()`.
 *
 * Your plugin class can also _implement_ this interface, with the same effect.
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
