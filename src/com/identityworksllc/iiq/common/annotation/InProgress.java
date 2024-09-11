package com.identityworksllc.iiq.common.annotation;

import java.lang.annotation.*;

/**
 * Indicates that a class or method is currently work-in-progress. Methods or
 * classes tagged with this annotation may change their behavior without
 * notice.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.CLASS)
@Inherited
public @interface InProgress {
}
