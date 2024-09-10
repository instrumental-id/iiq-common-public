package com.identityworksllc.iiq.common.vo;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.Serializable;

/**
 * A container object holding a failure, usually used in a threaded context. This
 * indicates that the operation processing the given object of type `T` failed with
 * an exception of type `E`.
 *
 * The {@link ThrowableSerializer} is used to make these objects JSON-friendly,
 * but the output cannot be converted back to a Failure.
 *
 * If the object of type `T` is not null, it must be serializable by Jackson,
 * either by default, via annotations, or via some mix-in.
 *
 * @param <T> The type of the object that failed to be processed
 * @param <E> The error type
 */
public class Failure<T, E extends Exception> implements Serializable {
    private final E exception;

    private final T object;

    /**
     * Constructs a new Failure with an object but no exception
     * @param object The object
     */
    public Failure(T object) {
        this.object = object;
        this.exception = null;
    }

    /**
     * Constructs a new Failure with an exception but no object
     * @param exception the exception
     */
    public Failure(E exception) {
        this.object = null;
        this.exception = exception;
    }

    /**
     * Constructs a new failure with both an object and an exception
     * @param object the object
     * @param exception the exception
     */
    public Failure(T object, E exception) {
        this.object = object;
        this.exception = exception;
    }

    /**
     * Gets the stored exception if one exists
     * @return The stored exception
     */
    @JsonSerialize(using = ThrowableSerializer.class)
    @JsonProperty("exception")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public E getException() {
        return exception;
    }

    /**
     * Gets the stored object if one exists
     * @return The stored object
     */
    @JsonProperty("object")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public T getObject() {
        return object;
    }
}
