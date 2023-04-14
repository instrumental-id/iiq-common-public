package com.identityworksllc.iiq.common.minimal.vo;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.Serializable;

/**
 * A container object holding a failure, usually in a threaded context
 * @param <T> The type of the object that failed to be processed
 * @param <E> The error type
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class Failure<T, E extends Exception> implements Serializable {
    private final E exception;

    private final T object;

    public Failure(T object, E exception) {
        this.object = object;
        this.exception = exception;
    }

    @JsonSerialize(using = ThrowableSerializer.class)
    @JsonProperty("exception")
    public E getException() {
        return exception;
    }

    @JsonProperty("object")
    public T getObject() {
        return object;
    }
}
