package com.identityworksllc.iiq.common.context;

import java.util.function.Supplier;

/**
 * A ThreadLocal-like value that stores a scoped value for the current thread. This is
 * intended for use with the {@link ScopedValues} class, which provides the only method
 * for setting values in a SimpleScopedValue.
 *
 * You should create your own SimpleScopedValues instances to store whatever
 * application-specific context you need. You can also use our provided {@link ScopedValues#CONTEXT}
 * SimpleScopedValue to store an {@link ActionContext}, which can be used to store basic information
 * about the current action being performed.
 *
 * @param <T> the type of the value being stored in this SimpleScopedValue
 */
public class SimpleScopedValue<T> implements Supplier<T> {
    /**
     * A container for the value of this SimpleScopedValue in the current thread
     */
    private final ThreadLocal<T> value;

    /**
     * Constructs a new SimpleScopedValue with an empty ThreadLocal value. The initial value of
     * the ThreadLocal will be null until it is set using the {@link #set(Object)} method.
     */
    public SimpleScopedValue() {
        this.value = new ThreadLocal<>();
    }

    /**
     * Returns the current value of this SimpleScopedValue for the current thread. If the value has
     * not been set for the current thread, this method will return null.
     * @return the current value of this SimpleScopedValue for the current thread, or null if it has not been set
     */
    public T get() {
        return value.get();
    }

    /**
     * Sets the value of this SimpleScopedValue for the current thread. This should only be
     * called from the {@link ScopedValues#set(SimpleScopedValue, Object)} method to ensure
     * that the previous value is properly restored when the returned Closer is closed.
     *
     * This is package-private so that only ScopedValues can see it.
     *
     * @param value the value to set for this SimpleScopedValue for the current thread
     */
    /* package */ void set(T value) {
        this.value.set(value);
    }
}
