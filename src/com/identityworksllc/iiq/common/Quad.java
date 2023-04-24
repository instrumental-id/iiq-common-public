package com.identityworksllc.iiq.common;

import java.io.Serializable;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Extends Triple by adding one more item, a group of four items
 *
 * @param <A> The first item type
 * @param <B> The second item type
 * @param <C> The third item type
 * @param <D> The fourth item type
 */
public class Quad<A, B, C, D> implements Serializable {
    /**
     * Constructs a new quad tuple of the three items given
     *
     * @param first The first time
     * @param second The second item
     * @param third The third item
     * @param fourth The fourth item type
     *
     * @return A typed Quad containing those three items
     */
    public static <X, Y, Z, Q> Quad<X, Y, Z, Q> of(X first, Y second, Z third, Q fourth) {
        return new Quad<>(first, second, third, fourth);
    }

    /**
     * The first item in the quad
     */
    private final A first;
    /**
     * The fourth item in the quad
     */
    private final D fourth;
    /**
     * The second item in the quad
     */
    private final B second;
    /**
     * The third item in the quad
     */
    private final C third;

    /**
     * Creates a new Quad of the four given items
     */
    private Quad(A a, B b, C c, D d) {
        this.first = a;
        this.second = b;
        this.third = c;
        this.fourth = d;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Quad<?, ?, ?, ?> quad = (Quad<?, ?, ?, ?>) o;
        return Objects.equals(first, quad.first) && Objects.equals(second, quad.second) && Objects.equals(third, quad.third) && Objects.equals(fourth, quad.fourth);
    }

    /**
     * Gets the first item in the quad
     * @return The first item
     */
    public A getFirst() {
        return first;
    }

    /**
     * Gets the fourth item in the quad
     * @return The fourth item
     */
    public D getFourth() {
        return fourth;
    }

    /**
     * Gets the second item in the quad
     * @return The second item
     */
    public B getSecond() {
        return second;
    }

    /**
     * Gets the third item in the quad
     * @return The third item
     */
    public C getThird() {
        return third;
    }

    @Override
    public int hashCode() {
        return Objects.hash(first, second, third, fourth);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Quad.class.getSimpleName() + "[", "]")
                .add("first=" + first)
                .add("second=" + second)
                .add("third=" + third)
                .add("fourth=" + fourth)
                .toString();
    }
}
