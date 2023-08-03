package com.identityworksllc.iiq.common.plugin.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The annotation specifying what VO objects can be validly returned from a
 * REST API endpoint method, apart from the defaults.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface ResponsesAllowed {

    /**
     * The list of classes that the REST API endpoint supports. All other output
     * types will result in a logged warning and a null response.
     * @return The list of classes
     */
    Class<?>[] value();
}
