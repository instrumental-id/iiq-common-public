package com.identityworksllc.iiq.common.annotation;

import java.lang.annotation.*;

/**
 * Indicates that a class or method is considered a core part of the library,
 * and will remain API-compatible. This is a promise that future versions
 * of the library will not break compatibility with this class or method.
 *
 * If an annotated class or method must be removed or changed in a way that breaks
 * compatibility, it will be deprecated for at least six months before being removed
 * or changed.
 *
 * Smaller parts of a class (such as individual methods or nested classes) may
 * be annotated with {@link Experimental} or {@link InProgress} to indicate that they
 * are not yet stable, even if the class as a whole is stable.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD})
@Inherited
public @interface CoreStable {
}
