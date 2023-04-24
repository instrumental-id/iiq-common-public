package com.identityworksllc.iiq.common;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * A common 'functional' class from languages like Scala. It will contain
 * either the left object or the right object, but never both. This can be used
 * in a variety of functional data flows.
 *
 * @param <L> The left object type
 * @param <R> The right object type
 */
public final class Either<L, R> {

    /**
     * Creates an Either object containing the given object on the left. The type of
     * the right object is undefined.
     *
     * @param l The non-null left object
     * @param <A> The left object type
     * @return The Either object
     */
    public static <A> Either<A, ?> left(A l) {
        if (l == null) {
            throw new IllegalArgumentException("Input to Either.left() must not be null");
        }
        return new Either<>(l, null);
    }

    /**
     * Creates an Either object containing the given object on the right. The type of
     * the left object is undefined.
     *
     * @param r The non-null right object
     * @param <B> The right object type
     * @return The Either object
     */
    public static <B> Either<?, B> right(B r) {
        if (r == null) {
            throw new IllegalArgumentException("Input to Either.right() must not be null");
        }
        return new Either<>(null, r);
    }

    private final L left;
    private final R right;

    protected Either(L inputLeft, R inputRight) {
        left = inputLeft;
        right = inputRight;
    }

    /**
     * @see Object#equals(Object)
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Either)) return false;
        Either<?, ?> either = (Either<?, ?>) o;
        return Objects.equals(left, either.left) &&
                Objects.equals(right, either.right);
    }

    /**
     * Returns the left object or throws a {@link NoSuchElementException} if this Either contains a right object
     * @return The left object
     * @throws NoSuchElementException if this is a right Either
     */
    public L getLeft() {
        if (left == null) {
            throw new NoSuchElementException("left");
        }
        return left;
    }

    /**
     * Returns the right object or throws a {@link NoSuchElementException} if this Either contains a left object
     * @return The right object
     * @throws NoSuchElementException if this is a left Either
     */
    public R getRight() {
        if (right == null) {
            throw new NoSuchElementException("right");
        }
        return right;
    }

    /**
     * @return True if this Either has a left object
     */
    public boolean hasLeft() {
        return left != null;
    }

    /**
     * @return True if this Either has a right object
     */
    public boolean hasRight() {
        return right != null;
    }

    /**
     * @see Object#hashCode()
     */
    @Override
    public int hashCode() {
        return Objects.hash(left, right);
    }

    /**
     * @see Object#toString()
     */
    @Override
    public String toString() {
        return new StringJoiner(", ", Either.class.getSimpleName() + "[", "]")
                .add("left=" + left)
                .add("right=" + right)
                .toString();
    }
}
