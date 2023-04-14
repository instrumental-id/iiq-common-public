package com.identityworksllc.iiq.common.minimal;

import sailpoint.tools.GeneralException;

/**
 * Represents a function that takes four inputs and produces one output
 *
 * @param <A> The first parameter type
 * @param <B> The second parameter type
 * @param <C> The third parameter type
 * @param <D> The fourth parameter type
 * @param <R> The output type
 */
@FunctionalInterface
public interface QuadFunction<A, B, C, D, R> {
    /**
     * Applies the function to the given arguments
     */
    R apply(A a, B b, C c, D d) throws GeneralException;
}
