package com.identityworksllc.iiq.common.cache;

/**
 * This interface represents a factory for objects of type T
 * @param <T> The type of the object to generate
 */
public interface CacheGenerator<T> {
	
	/**
	 * Gets a value (new or existing) that should be associated with the given key.
	 * @param key The key for which to retrieve a value.
	 * @return The resulting value
	 */
	T getValue(Object key);
	
}
