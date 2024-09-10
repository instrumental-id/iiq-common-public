package com.identityworksllc.iiq.common.cache;

import java.io.Serializable;

/**
 * A variant on {@link CacheMap} intended for use in plugin-heavy environments, when
 * you may want to clear a cache upon plugin installation or update. An entry will
 * be considered expired when {@link CacheEntry#isExpired()} returns true or else
 * when the plugin version has changed.
 *
 * All other behavior is identical.
 *
 * @param <K> The key type
 * @param <V> The value type
 */
public class VersionedCacheMap<K, V> extends CacheMap<K, V> implements Serializable {
    /**
     * Caches the value using a {@link VersionedCacheEntry}
     *
     * @param value The value to cache
     * @return The cache entry
     */
    @Override
    protected CacheEntry<V> cache(V value) {
        return new VersionedCacheEntry<>(value, getNewExpirationDate());
    }

    /**
     * @see CacheMap#putAllInternal(CacheMap)
     */
    @Override
    public void putAllInternal(CacheMap<? extends K, ? extends V> other) {
        for (K key : other.keySet()) {
            CacheEntry<? extends V> value = other.getInternalMap().get(key);
            if (!value.isExpired()) {
                VersionedCacheEntry<? extends V> newValue = VersionedCacheEntry.of(value);
                getInternalMap().put(key, newValue);
            }
        }
    }
}
