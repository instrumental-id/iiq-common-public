package com.identityworksllc.iiq.common;

import java.util.Map;
import java.util.concurrent.ConcurrentMap;

/**
 * An interface extending {@link DelegatedMap} to implement {@link ConcurrentMap}
 * methods. All method calls will be delegated to the return value of {@link #getDelegate()}.
 *
 * @param <K>
 * @param <V>
 */
public interface DelegatedConcurrentMap<K, V> extends ConcurrentMap<K, V>, DelegatedMap<K, V> {
    /**
     * Gets the map to which all other {@link Map} calls are delegated
     * @return The Map to which all calls are delegated
     */
    @Override
    ConcurrentMap<K, V> getDelegate();

    default V putIfAbsent(K key, V value) {
        return getDelegate().putIfAbsent(key, value);
    }

    default boolean remove(Object key, Object value) {
        return getDelegate().remove(key, value);
    }

    default boolean replace(K key, V oldValue, V newValue) {
        return getDelegate().replace(key, oldValue, newValue);
    }

    default V replace(K key, V value) {
        return getDelegate().replace(key, value);
    }
}
