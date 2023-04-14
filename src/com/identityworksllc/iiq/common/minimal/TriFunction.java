package com.identityworksllc.iiq.common.minimal;

import sailpoint.tools.GeneralException;

/**
 * Represents a function that takes three inputs and produces one output
 * 
 * @param <A> The first parameter type
 * @param <B> The second parameter type
 * @param <C> The third parameter type
 * @param <R> The output type
 */
@FunctionalInterface
public interface TriFunction<A, B, C, R> {
    /**
     * Applies this function to the given arguments
     *
     * @see java.util.function.Function#apply(Object)
     */
    R apply(A a, B b, C c) throws GeneralException;
}
