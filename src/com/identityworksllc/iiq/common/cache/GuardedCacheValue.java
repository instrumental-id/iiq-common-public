package com.identityworksllc.iiq.common.cache;

import java.security.AccessControlException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;

/**
 * A cache value wrapper that allows access based on matching an
 * arbitrary guard value. If the guard value is not matched, the
 * {@link #getValue(Object)} method returns an empty Optional.
 *
 * This can be used, for example, to detect changes to permissions
 * or logged in users and clear the cache accordingly.
 *
 * @param <ValueType> The type of the thing being stored here
 * @param <GuardType> The type of the guard value
 */
public class GuardedCacheValue<ValueType, GuardType> {

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
     *
     * @param guardTest The guard test value
     * @return An optional containing the stored value, if the guard value input matches, or else an empty optional object
     * @throws AccessControlException if the test token doesn't match
     */
    public Optional<ValueType> getValue(GuardType guardTest) {
        if (this.matcher.test(guardTest, guardToken)) {
            return Optional.of(value);
        } else {
            return Optional.empty();
        }
    }
}
