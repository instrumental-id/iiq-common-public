package com.identityworksllc.iiq.common.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreType;

/**
 * Can be used via Jackson's {@link com.fasterxml.jackson.databind.ObjectMapper} to extend any
 * particular class, making it ignored. See this page for an example:
 *
 * https://www.baeldung.com/jackson-ignore-properties-on-serialization
 */
@JsonIgnoreType
public class MixinIgnoreType {
}
