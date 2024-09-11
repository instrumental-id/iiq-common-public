package com.identityworksllc.iiq.common;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * A Map interface that delegates all calls by default to the contained Map. This
 * is useful for situations where the implementation of the Map is the new behavior,
 * not the implementation of the individual methods.
 *
 * The delegate Map is whatever is returned from {@link #getDelegate()}.
 *
 * @param <K> The key type
 * @param <V> The value type
 */
public interface DelegatedMap<K, V> extends Map<K, V> {
    default void clear() {
        getDelegate().clear();
    }

    default boolean containsKey(Object key) {
        return getDelegate().containsKey(key);
    }

    default boolean containsValue(Object value) {
        return getDelegate().containsValue(value);
    }

    default Set<Entry<K, V>> entrySet() {
        return getDelegate().entrySet();
    }

    default V get(Object key) {
        return getDelegate().get(key);
    }

    /**
     * Gets the map to which all other {@link Map} calls are delegated
     * @return The Map to which all calls are delegated
     */
    Map<K, V> getDelegate();

    default boolean isEmpty() {
        return getDelegate().isEmpty();
    }

    default Set<K> keySet() {
        return getDelegate().keySet();
    }

    default V put(K key, V value) {
        return getDelegate().put(key, value);
    }

    default void putAll(Map<? extends K, ? extends V> m) {
        getDelegate().putAll(m);
    }

    default V remove(Object key) {
        return getDelegate().remove(key);
    }

    default int size() {
        return getDelegate().size();
    }

    default Collection<V> values() {
        return getDelegate().values();
    }

}
