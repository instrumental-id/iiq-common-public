package com.identityworksllc.iiq.common.vo;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A meta-annotation that can be used to flag a field as an IIQ Object. This
 * will cause it to be serialized by Jackson using {@link IIQObjectSerializer}
 * and de-serialized using {@link IIQObjectDeserializer}.
 */
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotationsInside
@JsonDeserialize(using = IIQObjectDeserializer.class)
@JsonSerialize(using = IIQObjectSerializer.class)
public @interface IIQObject {
}
