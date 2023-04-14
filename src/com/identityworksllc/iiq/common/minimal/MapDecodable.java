package com.identityworksllc.iiq.common.minimal;

import java.util.Map;

/**
 * An interface that can be implemented by any object that would like to translate
 * itself from a Map input. This is intended for use by the ObjectMapper utility, but
 * may be used outside of that function.
 *
 * This is, roughly, the opposite of {@link Mappable}.
 */
@FunctionalInterface
public interface MapDecodable {
    void initializeFromMap(Map<String, Object> input) throws ObjectMapper.ObjectMapperException;
}
