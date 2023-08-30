package com.identityworksllc.iiq.common.plugin.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that the API method ought to authorize the user only if they
 * are authorized by all of the {@link AuthorizedBy} sub-elements.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface AuthorizeAll {
    /**
     * The list of authorizations
     * @return The list of authorizations
     */
    AuthorizedBy[] value();
}
