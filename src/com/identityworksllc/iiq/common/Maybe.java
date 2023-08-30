package com.identityworksllc.iiq.common;

import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A functional "monad" that contains either a non-null value of the given type
 * or an exception, but never both. The idea here is:
 *
 * objectList
 *  .stream()
 *  .map(obj -> Maybe.wrap(String.class, some::functionThatCanFail))
 *  .filter(Maybe.fnHasValue())
 *  .forEach(items);
 *
 * @param <T> The type that this object might contain
 */
public final class Maybe<T> {
    /**
     * A consumer extension that handles the Maybe concept. If the Maybe has a
     * value, it will be passed to the wrapped Consumer, and if it does not,
     * no action will be taken.
     * @param <T> The type contained within the Maybe, maybe.
     */
    public static final class MaybeConsumer<T> implements Consumer<Maybe<T>> {
        public static <T> MaybeConsumer<T> from(Consumer<T> wrappedConsumer) {
            return new MaybeConsumer<>(wrappedConsumer);
        }
        private final Consumer<T> wrapped;

        private MaybeConsumer(Consumer<T> wrappedConsumer) {
            if (wrappedConsumer == null) {
                throw new IllegalArgumentException("Consumer passed to MaybeConsumer must not be null");
            }
            this.wrapped = wrappedConsumer;
        }

        /**
         * Performs this operation on the given argument.
         *
         * @param tMaybe the input argument
         */
        @Override
        public void accept(Maybe<T> tMaybe) {
            if (tMaybe.hasValue()) {
                wrapped.accept(tMaybe.get());
            }
        }
    }

    /**
     * Creates a Predicate that returns true if the Maybe object has an error
     * @param <F> The arbitrary input type
     * @return The predicate
     */
    public static <F> Predicate<Maybe<F>> fnHasError() {
        return Maybe::hasError;
    }

    /**
     * Creates a Predicate that returns true if the Maybe object has a value
     * @param <F> The arbitrary input type
     * @return The predicate
     */
    public static <F> Predicate<Maybe<F>> fnHasValue() {
        return Maybe::hasValue;
    }

    /**
     * Returns a Maybe object containing the given value
     * @param value The value
     * @param <A> The type of the value
     * @return A Maybe object containing the given value
     */
    public static <A> Maybe<A> of(A value) {
        return new Maybe<>(value, null);
    }

    /**
     * Returns a Maybe that is either a value or an error, depending on the outcome of
     * the supplier in question.
     *
     * @param aClass The class expected, used solely to distinguish this method from the other of() implementations
     * @param supplier The supplier of a value to wrap in a Maybe
     * @return A Maybe repesenting the outcome of the Supplier's action
     * @param <A> The content type of the Maybe, if it has a value
     */
    public static <A> Maybe<A> of(Class<A> aClass, Functions.SupplierWithError<A> supplier) {
        try {
            return Maybe.of(supplier.getWithError());
        } catch(Throwable t) {
            return Maybe.of(t);
        }
    }

    /**
     * Returns a Maybe object containing an exception. The class parameter is only used for casting the output type.
     * @param value The exception
     * @param otherwiseExpectedType The parameterized type that we would be expecting if this was not an exception
     * @param <A> The parameterized type that we would be expecting if this was not an exception
     * @return a Maybe object containing the exception
     */
    public static <A> Maybe<A> of(Throwable value, Class<A> otherwiseExpectedType) {
        return new Maybe<>(null, value);
    }

    /**
     * Chains a Maybe object by passing along the Throwable from an existing Maybe into the next. This is for use with streams.
     * @param value An existing Maybe in error state
     * @param otherwiseExpectedType The parameterized type that we would be expecting if this was not an exception
     * @param <A> The parameterized type that we would be expecting if this was not an exception
     * @return a Maybe object containing the exception
     */
    public static <A> Maybe<A> of(Maybe<?> value, Class<A> otherwiseExpectedType) {
        if (!value.hasError()) {
            throw new IllegalArgumentException("The chained 'Maybe' implementation can only be used for errors");
        }
        return new Maybe<>(null, value.contents.getRight());
    }

    /**
     * Chains a Maybe object by passing along the Throwable from an existing Maybe into the next. This is for use with streams.
     * @param value An existing Maybe in error state
     * @param <A> The parameterized type that we would be expecting if this was not an exception
     * @return a Maybe object containing the exception
     */
    public static <A> Maybe<A> of(Maybe<?> value) {
        if (!value.hasError()) {
            throw new IllegalArgumentException("The chained 'Maybe' implementation can only be used for errors");
        }
        return new Maybe<>(null, value.contents.getRight());
    }

    /**
     * Creates a new maybe from the given throwable with an inferred type by context
     * @param e The throwable
     * @param <R> The inferred type
     * @return A chained Maybe
     */
    @SuppressWarnings("unchecked")
    public static <R> Maybe<R> of(Throwable e) {
        return (Maybe<R>)new Maybe<>(null, e);
    }

    /**
     * Returns a function wrapping the input function. The wrapper will resolve the
     * output of the input function by invoking {@link com.identityworksllc.iiq.common.Functions.FunctionWithError#applyWithError(I)}
     * and wrapping the output (whether a value or an exception) in a Maybe.
     *
     * @param aClass The output class expected
     * @param func The function that will be wrapped in a Maybe producer
     * @return A Maybe repesenting the outcome of the Supplier's action
     * @param <I> The input type to the function
     * @param <O> The content type of the Maybe, assuming it was to have a value
     */
    public static <I, O> Function<I, Maybe<O>> wrap(Class<O> aClass, Functions.FunctionWithError<I, O> func) {
        return (x) -> {
            try {
                return Maybe.of(func.applyWithError(x));
            } catch (Throwable t) {
                return Maybe.of(t);
            }
        };
    }

    /**
     * The contents of the Maybe object, which contain either a T or a Throwable
     */
    private final Either<T, Throwable> contents;

    @SuppressWarnings("unchecked")
    private Maybe(T left, Throwable right) {
        if (left != null) {
            contents = (Either<T, Throwable>) Either.left(left);
        } else {
            contents = (Either<T, Throwable>) Either.right(right);
        }
    }

    /**
     * Gets the value or throws the exception wrapped in an ExecutionException
     * @return The value if it exists
     * @throws IllegalStateException If this Maybe was an exception instead
     */
    public T get() throws IllegalStateException {
        if (contents.hasRight()) {
            throw new IllegalStateException(contents.getRight());
        }
        return contents.getLeft();
    }

    /**
     * Returns the error if there is one, or else throws a {@link java.util.NoSuchElementException}.
     * @return The error if one exists
     * @throws java.util.NoSuchElementException if the error doesn't exist
     */
    public Throwable getError() throws NoSuchElementException {
        return contents.getRight();
    }

    /**
     * If this Maybe has an error and not a value
     * @return True if this Maybe has an error
     */
    public boolean hasError() {
        return contents.hasRight();
    }

    /**
     * If this Maybe has a value and not an error
     * @return True if this Maybe does not have an error
     */
    public boolean hasValue() {
        return contents.hasLeft();
    }

    /**
     * Chains a Maybe object by invoking the given function on it.
     *
     * There are three possible outcomes:
     *
     * 1. This object already has an error ({@link Maybe#hasError()} returns true), in which case this method will return a new Maybe with that error.
     *
     * 2. Applying the function to this Maybe's value results in an exception, in which case this method will return a new Maybe with that exception.
     *
     * 3. Applying the function is successful and produces an object of type 'B', in which case this method returns a new Maybe containing that object.
     *
     * @param downstream The mapping function to apply to this Maybe
     * @param <B> The output type
     * @return a Maybe object containing the exception
     */
    public <B> Maybe<B> map(Functions.FunctionWithError<T, B> downstream) {
        if (this.hasError()) {
            return Maybe.of(this.getError());
        }

        T input = this.get();

        try {
            B output = downstream.applyWithError(input);
            return Maybe.of(output);
        } catch(Throwable e) {
            return Maybe.of(e);
        }
    }
}
