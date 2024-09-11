package com.identityworksllc.iiq.common;

import sailpoint.api.SailPointContext;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

/**
 * An extension to the Map interface that adds a bunch of default typed getXXX
 * methods. All other methods, such as {@link Map#get(Object)} are to be implemented
 * by the actual concrete Map implementation.
 *
 * You can use {@link TypeFriendlyMap#fromMap(Map)} to retrieve a copy of your Map
 * implementing this interface.
 *
 * @param <K> The key type
 * @param <V> The value type
 */
@SuppressWarnings("unused")
public interface TypeFriendlyMap<K, V> extends Map<K, V> {
    /**
     * Decorates the input Map with the TypeFriendlyMap interface. This does not make
     * a copy of the input. All Map interface methods will be delegated to the decorated Map.
     *
     * @param input The input map to decorate
     * @param <K> The key type
     * @param <V> The value type
     * @return The decorated Map object
     */
    static <K, V> TypeFriendlyMap<K, V> fromMap(Map<K, V> input) {
        if (input == null) {
            return null;
        }
        if (input instanceof ConcurrentMap) {
            return new TypeFriendlyDelegatedConcurrentMap<>((ConcurrentMap<K, V>)input);
        } else {
            return new TypeFriendlyDelegatedMap<>(input);
        }
    }

    /**
     * Gets the given value cast to the given type. If the value is null, a null will
     * be returned. If the value cannot be cast to the given type, a ClassCastException
     * will be thrown.
     * @param key The key to get the value for
     * @param targetClass The target class to cast the value to
     * @param <T> The output type
     * @return The output object cast to the given class
     * @throws ClassCastException if the value is not compatible with the target class
     */
    default <T extends V> T getAs(K key, Class<T> targetClass) {
        V value = get(key);
        if (value == null) {
            return null;
        }
        return targetClass.cast(value);
    }

    default boolean getBoolean(K key) {
        return Util.otob(get(key));
    }

    default int getInt(K key) {
        return Util.otoi(get(key));
    }

    default long getLong(K key) {
        return Util.otolo(get(key));
    }

    /**
     * Gets the given value cast to the given SailPointObject type. If the stored value
     * is a String and not the target type, it will be assumed to be a name or ID and
     * the SailPointContext provided will be invoked to look it up.
     *
     * @param key The key to get the value for
     * @param targetClass The target class to cast the value to
     * @param <T> The SailPointObject type to query
     * @return The output object cast to the given class
     * @throws ClassCastException if the value is not compatible with the target class
     * @throws GeneralException if any errors occur reading the SPO from the context
     */
    default <T extends SailPointObject> T getSailPointObject(K key, SailPointContext context, Class<T> targetClass) throws GeneralException {
        V value = get(key);
        if (value == null) {
            return null;
        }
        if (targetClass.isAssignableFrom(value.getClass())) {
            return targetClass.cast(value);
        }
        if (value instanceof String) {
            return context.getObject(targetClass, (String)value);
        }
        throw new ClassCastException("Expected a value of type " + targetClass.getName() + " or a String, got " + value.getClass().getName());
    }

    default String getString(K key) {
        return Util.otos(get(key));
    }

    default List<String> getStringList(K key) {
        return Util.otol(get(key));
    }

}
