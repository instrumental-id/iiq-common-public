package com.identityworksllc.iiq.common.cache;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * A cache value wrapper that allows access based on matching an
 * arbitrary guard value. If the guard value is not matched, the
 * {@link #getValue(Object)} method returns an empty Optional.
 *
 * This can be used, for example, to detect changes to permissions
 * or logged in users and clear the cache accordingly.
 *
 * This is similar to {@link java.util.concurrent.atomic.AtomicStampedReference}, but can
 * use any value or behavior as the 'stamp'. With a guard type of Integer, and the
 * default matcher of Object::equals, this class would be identical to that.
 *
 * @param <ValueType> The type of the thing being stored here
 * @param <GuardType> The type of the guard value
 */
public class GuardedCacheValue<ValueType, GuardType> implements Function<GuardType, Optional<ValueType>> {

    /**
     * The value that must be matched for the value to return
     */
    private final GuardType guardToken;

    /**
     * The token value matcher
     */
    private final BiPredicate<GuardType, GuardType> matcher;

    /**
     * The actual value wrapped by this object
     */
    private final ValueType value;

    /**
     * Constructs a new cache value with the given token and value. The token
     * will be matched using Object.equals.
     *
     * @param value The value wrapped by this cache token
     * @param token The token that must be matched to retrieve the value
     */
    public GuardedCacheValue(ValueType value, GuardType token) {
        this(value, token, Object::equals);
    }

    /**
     * Constructs a new cache value with the given token, value, and token
     * matcher. At getValue time, the matcher will be used to decide whether
     * the input actually matches the token.
     *
     * @param value The value wrapped by this cache token
     * @param token The token that must be matched to retrieve the value
     * @param matcher The predicate that decides whether the input token matches
     */
    public GuardedCacheValue(ValueType value, GuardType token, BiPredicate<GuardType, GuardType> matcher) {
        this.value = Objects.requireNonNull(value);
        this.guardToken = Objects.requireNonNull(token);

        if (matcher == null) {
            this.matcher = Objects::equals;
        } else {
            this.matcher = matcher;
        }
    }

    /**
     * Functional version of {@link #getValue(Object)}.
     * @param guardTest the function argument
     * @return An optional containing the stored value, if the guard value input matches, or else an empty optional object
     */
    @Override
    public Optional<ValueType> apply(GuardType guardTest) {
        return getValue(guardTest);
    }

    /**
     *
     * @param guardTest The guard test value
     * @return An optional containing the stored value, if the guard value input matches, or else an empty optional object
     */
    public Optional<ValueType> getValue(GuardType guardTest) {
        if (this.matcher.test(guardTest, guardToken)) {
            return Optional.of(value);
        } else {
            return Optional.empty();
        }
    }
}
