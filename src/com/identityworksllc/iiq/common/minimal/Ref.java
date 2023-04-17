package com.identityworksllc.iiq.common.minimal;

import sailpoint.api.SailPointContext;
import sailpoint.object.QueryOptions;
import sailpoint.object.Reference;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Constructs IIQ Reference objects using a standardized API
 */
public final class Ref {
    /**
     * A constant that can be passed to context.search() to retrieve an object for use here
     */
    @SuppressWarnings("unused")
    public static final List<String> REFERENCE_PROPS = Arrays.asList("id");

    /**
     * Retrieves a dehydrated object given the type and ID
     * @param type The type of the object to create a reference to
     * @param id The object ID
     * @return A Reference to that object
     */
    public static Reference of(Class<? extends SailPointObject> type, String id) {
        return new Reference(type.getName(), Objects.requireNonNull(id));
    }

    /**
     * Retrieves a dehydrated object given the type and parameters
     * @param type The type of the object to create a reference to
     * @param parameters The result of a {@link SailPointContext#search(Class, QueryOptions, List)} call with the 'id' in the first position
     * @return A Reference to that object
     */
    public static Reference of(Class<? extends SailPointObject> type, Object[] parameters) {
        if (parameters == null || parameters.length < 1) {
            throw new IllegalArgumentException("The parameters must contain at least an object ID");
        }
        if (parameters[0] instanceof String) {
            return new Reference(type.getName(), (String)parameters[0]);
        } else {
            throw new IllegalArgumentException("The first item in the parameters must be a String object ID");
        }
    }

    /**
     * Retrieves a dehydrated reference from the object
     * @param object The object to dehydrate
     * @return The dehydrated reference
     */
    public static Reference of(SailPointObject object) {
        return new Reference(Objects.requireNonNull(object));
    }

    /**
     * Creates a type transforming function that will take any object that this class
     * recognizes and convert it into a Reference of the appropriate type.
     *
     * This could be used, for example, as the second parameter passed to TransformingIterator's
     * constructor.
     *
     * @param targetClass The target class to retrieve a reference for
     * @param <T> The type of the target class
     * @return a function converting the input to a reference
     */
    public static <T extends SailPointObject> Function<?, Reference> transformer(Class<T> targetClass) {
        return o -> {
            if (o instanceof String) {
                return Ref.of(targetClass, (String)o);
            } else if (o instanceof Object[]) {
                return Ref.of(targetClass, (Object[])o);
            } else if (o instanceof SailPointObject) {
                return Ref.of((SailPointObject) o);
            } else {
                throw new IllegalArgumentException("Invalid input type: " + Utilities.safeClassName(o));
            }
        };
    }

}
