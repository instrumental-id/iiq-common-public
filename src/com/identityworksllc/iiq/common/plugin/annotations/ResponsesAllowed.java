package com.identityworksllc.iiq.common.plugin.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface ResponsesAllowed {

    Class<?>[] value();
}
