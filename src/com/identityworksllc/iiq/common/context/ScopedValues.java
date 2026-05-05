package com.identityworksllc.iiq.common.context;

/**
 * A tragically simple wrapper similar to Java 25's ScopedValues, until IIQ can support
 * that version of Java. This class provides a simple way to manage scoped values using
 * ThreadLocal, allowing a rough approximation of the API.
 *
 * For example, you can set this at the entrypoint of a web service request and have
 * it available to other classes in the codebase during the request.
 *
 * You can make your own SimpleScopedValues instances for other types of context as needed,
 * but we provide a default CONTEXT value for an ActionContext, which can be used to store
 * basic information about the current action being performed.
 *
 * Whatever you put into a ScopedValue should be immutable.
 *
 * To use:
 *
 * ```
 * try (ScopedValues.Closer closer = ScopedValues.set(yourScopedValue, new YourContext("myAction"))) {
 *     // Within this block, yourScopedValue.get() will return the value we just set
 *     // When the block is exited, the previous value will be restored
 * }
 * ```
 *
 * Or in Beanshell:
 *
 * ```
 * ScopedValues.Closer closer = ScopedValues.set(yourScopedValue, new YourContext("myAction"));
 * try {
 *     // Within this block, yourScopedValue.get() will return the value we just set
 * } finally {
 *     // Restore the previous value even if an exception occurs
 *     closer.close();
 * }
 * ```
 *
 */
public class ScopedValues {
    /**
     * A {@link SimpleScopedValue} that holds an {@link ActionContext}. This can be used however
     * you need to use it within your specific installation of IIQ. However, you should ensure
     * that usage is consistent across your codebase.
     */
    public static final SimpleScopedValue<ActionContext> CONTEXT = new SimpleScopedValue<>();

    /**
     * Sets the value of a SimpleScopedValue and returns a Closer that will reset the value to
     * its previous state when closed. This should be used in a try-with-resources or a
     * try-finally block to ensure that the previous value is always restored, even if an exception occurs.
     *
     * @param scopedValue The SimpleScopedValue to set
     * @param value The value to set in the SimpleScopedValue
     * @return a Closer that will reset the SimpleScopedValue to its previous value when closed
     * @param <T> The type of the value being set in the SimpleScopedValue
     */
    public static <T> Closer set(SimpleScopedValue<T> scopedValue, T value) {
        if (scopedValue == null) {
            throw new IllegalArgumentException("Scoped value cannot be null");
        }
        var previousValue = scopedValue.get();
        scopedValue.set(value);

        return () -> scopedValue.set(previousValue);
    }

    /**
     * The functional interface returned from {@link #set(SimpleScopedValue, Object)} that
     * will reset the previous value of the {@link SimpleScopedValue} when {@link #close()}
     * is called.
     */
    @FunctionalInterface
    public interface Closer extends AutoCloseable {
        @Override
        void close();
    }
}
