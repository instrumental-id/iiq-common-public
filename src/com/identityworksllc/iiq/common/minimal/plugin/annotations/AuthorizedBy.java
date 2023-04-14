package com.identityworksllc.iiq.common.minimal.plugin.annotations;

import sailpoint.authorization.Authorizer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface AuthorizedBy {
    String attribute() default "";

    String attributeValue() default "";

    String[] attributeValueIn() default {};

    Class<? extends Authorizer> authorizerClass() default Authorizer.class;

    String authorizerRule() default "";

    String[] capabilitiesList() default {};

    String capability() default "";

    String population() default "";

    String right() default "";

    String[] rightsList() default {};

    boolean systemAdmin() default false;
}
