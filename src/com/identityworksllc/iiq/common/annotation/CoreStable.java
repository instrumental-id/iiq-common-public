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
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD})
@Inherited
public @interface CoreStable {
}
