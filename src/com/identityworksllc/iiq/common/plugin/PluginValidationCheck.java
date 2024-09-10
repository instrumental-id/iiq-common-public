package com.identityworksllc.iiq.common.plugin;

/**
 * A functional interface to handle plugin validation.
 *
 * @see BaseCommonPluginResource#validate(PluginValidationCheck)
 * @see BaseCommonPluginResource#validate(String, PluginValidationCheck)
 */
@FunctionalInterface
public interface PluginValidationCheck {
    /**
     * Executes some validation check, returning true if the check passes
     *
     * @return True if the check passes, false otherwise
     * @throws Exception if anything goes wrong (also considered a failure)
     */
    boolean test() throws Exception;
}
