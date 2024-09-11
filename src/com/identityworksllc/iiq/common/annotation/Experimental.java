package com.identityworksllc.iiq.common.annotation;

import java.lang.annotation.*;

/**
 * An annotation indicating that the given type is experimental. Classes annotated with
 * this may change without notice.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD})
@Inherited
public @interface Experimental {
}
