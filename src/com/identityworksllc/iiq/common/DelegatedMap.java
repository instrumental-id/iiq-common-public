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
    /**
     * Removes all mappings from the map
     * @see Map#clear()
     */
    default void clear() {
        getDelegate().clear();
    }

    /**
     * Returns true if this map contains a mapping for the specified key.
     *
     * @param key key whose presence in this map is to be tested
     * @return true if this map contains a mapping for the specified key
     * @see Map#containsKey(Object)
     */
    default boolean containsKey(Object key) {
        return getDelegate().containsKey(key);
    }

    /**
     * Returns true if this map maps one or more keys to the specified value.
     * @param value value whose presence in this map is to be tested
     * @return true if this map maps one or more keys to the specified value
     * @see Map#containsValue(Object)
     */
    default boolean containsValue(Object value) {
        return getDelegate().containsValue(value);
    }

    /**
     * Returns a {@link Set} view of the mappings contained in this map.
     * @return a set view of the mappings contained in this map
     * @see Map#entrySet()
     */
    default Set<Entry<K, V>> entrySet() {
        return getDelegate().entrySet();
    }

    /**
     * Gets the value to which the specified key is mapped.
     * The underlying Map will determine the behavior if there is no key.
     * @param key the key whose associated value is to be returned
     * @return the value to which the specified key is mapped, or null if this map contains no mapping for the key
     * @see Map#get(Object)
     */
    default V get(Object key) {
        return getDelegate().get(key);
    }

    /**
     * Gets the map to which all other {@link Map} calls are delegated
     * @return The Map to which all calls are delegated
     */
    Map<K, V> getDelegate();

    /**
     * Returns true if this map contains no key-value mappings.
     * @return true if this map contains no key-value mappings
     */
    default boolean isEmpty() {
        return getDelegate().isEmpty();
    }

    /**
     * Returns a {@link Set} view of the keys contained in this map. The behavior
     * of the {@link Set} is determined by the underlying Map.
     * @return a set view of the keys contained in this map
     * @see Map#keySet()
     */
    default Set<K> keySet() {
        return getDelegate().keySet();
    }

    /**
     *
     * @param key key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     * @return the previous value associated with key, or null if there was no mapping for key.
     */
    default V put(K key, V value) {
        return getDelegate().put(key, value);
    }

    /**
     * Copies all of the mappings from the specified map to this map.
     * @param m mappings to be stored in this map
     * @see Map#putAll(Map)
     */
    default void putAll(Map<? extends K, ? extends V> m) {
        getDelegate().putAll(m);
    }

    /**
     * Removes the mapping for a key from this map if it is present.
     * @param key key whose mapping is to be removed from the map
     * @return the previous value associated with key, or null if there was no mapping for key.
     */
    default V remove(Object key) {
        return getDelegate().remove(key);
    }

    /**
     * Returns the number of key-value mappings in this map.
     * @return the number of key-value mappings in this map
     * @see Map#size()
     */
    default int size() {
        return getDelegate().size();
    }

    /**
     * Returns a {@link Collection} view of the values contained in this map.
     * @return a collection view of the values contained in this map
     */
    default Collection<V> values() {
        return getDelegate().values();
    }

}
