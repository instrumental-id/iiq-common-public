package com.identityworksllc.iiq.common.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreType;

/**
 * Can be used via Jackson's {@link com.fasterxml.jackson.databind.ObjectMapper#addMixInAnnotations(Class, Class)}
 * to ignore any particular type.
 *
 * The following line would cause any values of type `String[]` to be ignored by the
 * object mapper.
 *
 * ```
 * mapper.addMixInAnnotations(String[].class, MixinIgnoreType.class);
 * ```
 *
 * See this page for an extended example:
 *
 * https://www.baeldung.com/jackson-ignore-properties-on-serialization
 */
@JsonIgnoreType
public class MixinIgnoreType {
}
