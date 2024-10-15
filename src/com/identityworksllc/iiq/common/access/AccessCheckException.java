package com.identityworksllc.iiq.common.access;

import sailpoint.tools.GeneralException;

/**
 * An exception specific to the {@link AccessCheck} tools
 */
public class AccessCheckException extends GeneralException {
    /**
     * Constructs a new AccessCheckException with no message or parent
     */
    public AccessCheckException() {
        super();
    }

    /**
     * Constructs a new AccessCheckMessage with the given message text
     * @param message The message text
     */
    public AccessCheckException(String message) {
        super(message);
    }

    /**
     * Constructs a new AccessCheckException with the given message and parent
     * @param message The message text
     * @param t The parent throwable
     */
    public AccessCheckException(String message, Throwable t) {
        super(message, t);
    }

    /**
     * Constructs a new AccessCheckException with the given parent
     * @param t The parent throwable
     */
    public AccessCheckException(Throwable t) {
        super(t);
    }
}
