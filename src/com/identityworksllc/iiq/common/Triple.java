package com.identityworksllc.iiq.common;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Extends Pair by adding one more item
 *
 * @param <A> The first item type
 * @param <B> The second item type
 * @param <C> The third item type
 */
public class Triple<A, B, C> implements Serializable {
    /**
     * Constructs a new triple of the three items given
     *
     * @param first The first time
     * @param second The second item
     * @param third The third item
     *
     * @return A typed Triple containing those three items
     */
    public static <X, Y, Z> Triple<X, Y, Z> of(X first, Y second, Z third) {
        return new Triple<>(first, second, third);
    }

    /**
     * The first item in the triple
     */
    private final A first;

    /**
     * The second item in the triple
     */
    private final B second;

    /**
     * The third item in the triple
     */
    private final C third;

    /**
     * Creates a new Triple of the three given values
     */
    private Triple(A a, B b, C c) {
        this.first = a;
        this.second = b;
        this.third = c;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Triple<?, ?, ?> triple = (Triple<?, ?, ?>) o;
        return Objects.equals(first, triple.first) && Objects.equals(second, triple.second) && Objects.equals(third, triple.third);
    }

    /**
     * Gets the first item
     * @return The first item
     */
    public A getFirst() {
        return first;
    }

    /**
     * Gets the second item
     * @return The second item
     */
    public B getSecond() {
        return second;
    }

    /**
     * Gets the third item
     * @return The third item
     */
    public C getThird() {
        return third;
    }

    @Override
    public int hashCode() {
        return Objects.hash(first, second, third);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Triple.class.getSimpleName() + "[", "]")
                .add("first=" + first)
                .add("second=" + second)
                .add("third=" + third)
                .toString();
    }
}
