package com.identityworksllc.iiq.common.minimal;

import bsh.EvalError;
import bsh.This;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.IdentityService;
import sailpoint.api.IncrementalObjectIterator;
import sailpoint.api.IncrementalProjectionIterator;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.*;
import sailpoint.search.MapMatcher;
import sailpoint.tools.GeneralException;
import sailpoint.tools.MapUtil;
import sailpoint.tools.Pair;
import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.identityworksllc.iiq.common.minimal.Utilities.getProperty;

/**
 * This class implements a whole slew of java.util.function implementations, which
 * can be used as a hack to get streams working in Beanshell. This can simplify code
 * enormously.
 *
 * For example, you could do something like:
 *
 * <code>
 * someMethod(Object item) {
 *     // Beanshell function that does something interesting with the item
 * }
 *
 * // Creates a {@link Consumer} that invokes the specified Beanshell method
 * someMethodFunction = Functions.c(this, "someMethod");
 *
 * list.forEach(someMethodFunction);
 * </code>
 *
 */
@SuppressWarnings({"rawtypes", "unused"})
public class Functions {
    /**
     * A generic callback for doing connection things
     */
    @FunctionalInterface
    public interface ConnectionHandler {
        void accept(Connection connection) throws SQLException, GeneralException;
    }

    /**
     * A generic callback for doing connection things
     */
    @FunctionalInterface
    public interface RowHandler {
        void accept(ResultSet connection) throws SQLException, GeneralException;
    }

    /**
     * A generic callback implementation, essentially a runnable with an exception. This
     * class also implements ConsumerWithError, making it easier to use via Beanshell.
     */
    @FunctionalInterface
    public interface GenericCallback extends ConsumerWithError<SailPointContext> {
        @Override
        default void acceptWithError(SailPointContext context) throws GeneralException {
            run(context);
        }

        void run(SailPointContext context) throws GeneralException;
    }

    /**
     * An extension of Predicate that allows predicate code to throw
     * an exception. If used in a context not expecting this class,
     * the error will be caught, logged, and re-thrown.
     */
    public interface PredicateWithError<A> extends Predicate<A> {
        default PredicateWithError<A> and(Predicate<? super A> other) {
            return (a) -> {
                boolean result = testWithError(a);
                if (result) {
                    if (other instanceof PredicateWithError) {
                        @SuppressWarnings("unchecked")
                        PredicateWithError<? super A> errorHandler = (PredicateWithError<? super A>) other;
                        result = errorHandler.testWithError(a);
                    } else {
                        result = other.test(a);
                    }
                }
                return result;
            };
        }

        default PredicateWithError<A> negate() {
            return (a) -> !testWithError(a);
        }

        default PredicateWithError<A> or(Predicate<? super A> other) {
            return (a) -> {
                boolean result = testWithError(a);
                if (!result) {
                    if (other instanceof PredicateWithError) {
                        @SuppressWarnings("unchecked")
                        PredicateWithError<? super A> errorHandler = (PredicateWithError<? super A>) other;
                        result = errorHandler.testWithError(a);
                    } else {
                        result = other.test(a);
                    }
                }
                return result;
            };
        }

        @Override
        default boolean test(A a) {
            try {
                return testWithError(a);
            } catch (Throwable t) {
                log.error("Caught an error in a function");
                if (t instanceof RuntimeException) {
                    throw ((RuntimeException) t);
                }
                throw new IllegalStateException(t);
            }
        }

        boolean testWithError(A object) throws Throwable;
    }

    /**
     * An extension of BiFunction that allows functional code to throw
     * an exception. If used in a context not expecting this class,
     * the error will be caught, logged, and re-thrown.
     */
    public interface BiFunctionWithError<A, B, R> extends BiFunction<A, B, R> {
        @SuppressWarnings("unchecked")
        default <V> BiFunctionWithError<A, B, V> andThen(Function<? super R, ? extends V> after) {
            return (a, b) -> {
                R result = applyWithError(a, b);
                if (after instanceof FunctionWithError) {
                    return ((FunctionWithError<? super R, ? extends V>) after).applyWithError(result);
                }
                return after.apply(result);
            };
        }

        @Override
        default R apply(A a, B b) {
            try {
                return applyWithError(a, b);
            } catch (Throwable t) {
                log.error("Caught an error in a function");
                if (t instanceof RuntimeException) {
                    throw ((RuntimeException) t);
                }
                throw new IllegalStateException(t);
            }
        }

        R applyWithError(A obj1, B obj2) throws Throwable;
    }

    /**
     * An extension of Consumer that allows functional code to throw
     * an exception. If used in a context not expecting this class,
     * the error will be caught, logged, and re-thrown.
     */
    public interface ConsumerWithError<T> extends Consumer<T> {
        @Override
        default void accept(T a) {
            try {
                acceptWithError(a);
            } catch (Throwable t) {
                log.error("Caught an error in a function");
                if (t instanceof RuntimeException) {
                    throw ((RuntimeException) t);
                }
                throw new IllegalStateException(t);
            }
        }

        void acceptWithError(T t) throws Throwable;

        @Override
        default ConsumerWithError<T> andThen(Consumer<? super T> after) {
            return (a) -> {
                acceptWithError(a);
                if (after instanceof ConsumerWithError) {
                    @SuppressWarnings("unchecked")
                    ConsumerWithError<T> errorHandler = (ConsumerWithError<T>) after;
                    errorHandler.acceptWithError(a);
                } else {
                    after.accept(a);
                }
            };
        }
    }

    /**
     * An extension of Function that allows functional code to throw
     * an exception. If used in a context not expecting this class,
     * the error will be caught, logged, and re-thrown.
     */
    @SuppressWarnings("unchecked")
    public interface FunctionWithError<A, B> extends Function<A, B> {
        default <V> FunctionWithError<A, V> andThen(Function<? super B, ? extends V> after) {
            return o -> {
                B result = applyWithError(o);
                if (after instanceof FunctionWithError) {
                    return ((FunctionWithError<? super B, ? extends V>) after).applyWithError(result);
                }
                return after.apply(result);
            };
        }

        /**
         * Handles the case where this object is used in a regular Stream API, which
         * cannot handle errors. Logs the error and throws a runtime exception.
         * @param a The input object of type A
         * @return The output object of type B
         */
        @Override
        default B apply(A a) {
            try {
                return applyWithError(a);
            } catch (Throwable t) {
                log.error("Caught an error in a function");
                if (t instanceof RuntimeException) {
                    throw ((RuntimeException) t);
                }
                throw new IllegalStateException(t);
            }
        }

        /**
         * Implements a function transforming an object of type A to an object of
         * type B. This function can throw an arbitrary error.
         *
         * @param object The input object
         * @return The output object
         * @throws Throwable if any errors occur
         */
        B applyWithError(A object) throws Throwable;

        /**
         * Creates an error-friendly function composition that first translates from
         * input type V to intermediate type A, then translates from A to B.
         *
         * B = f2(f1(V))
         *
         * @param before The function to invoke before invoking this function
         * @param <V> The input type of the 'before' function
         * @return The composed FunctionWithError
         */
        @Override
        default <V> FunctionWithError<V, B> compose(Function<? super V, ? extends A> before) {
            if (before instanceof FunctionWithError) {
                return (v) -> applyWithError(((FunctionWithError<? super V, ? extends A>) before).applyWithError(v));
            } else {
                return (v) -> applyWithError(before.apply(v));
            }
        }

    }

    /**
     * An extension of Supplier that allows functional code to throw
     * an exception. If used in a context not expecting this class,
     * the error will be caught, logged, and re-thrown.
     */
    public interface SupplierWithError<T> extends Supplier<T> {
        @Override
        default T get() {
            try {
                return getWithError();
            } catch (Throwable t) {
                log.error("Caught an error in a function");
                if (t instanceof RuntimeException) {
                    throw ((RuntimeException) t);
                }
                throw new IllegalStateException(t);
            }
        }

        T getWithError() throws Throwable;
    }

    /**
     * A dual Consumer and BiConsumer that just does nothing, eating
     * the object
     */
    public static class NullConsumer implements Consumer<Object>, BiConsumer<Object, Object> {

        @Override
        public void accept(Object o, Object o2) {

        }

        @Override
        public void accept(Object o) {

        }
    }

    /**
     * Wrapper class so that Functions.otob() can be used as both a
     * function and a predicate
     */
    public static class OtobWrapper implements Function<Object, Boolean>, Predicate<Object> {

        private static final Object _lock = new Object();

        private static OtobWrapper _singleton;

        public static OtobWrapper get() {
            if (_singleton == null) {
                synchronized (_lock) {
                    if (_singleton == null) {
                        _singleton = new OtobWrapper();
                    }
                }
            }
            return _singleton;
        }

        private OtobWrapper() {
            /* do not instantiate */
        }

        @Override
        public Boolean apply(Object o) {
            return Util.otob(o);
        }

        @Override
        public boolean test(Object o) {
            return Util.otob(o);
        }
    }

    /**
     * Logger shared among various functions
     */
    private static final Log log = LogFactory.getLog(Functions.class);

    /**
     * A flatMap() function to extract the account requests from a plan
     */
    public static Function<ProvisioningPlan, Stream<ProvisioningPlan.AccountRequest>> accountRequests() {
        return plan -> plan == null ? Stream.empty() : Utilities.safeStream(plan.getAccountRequests());
    }

    /**
     * Returns a predicate that is always false
     * @param <T> The arbitrary type
     * @return The predicate
     */
    public static <T> Predicate<T> alwaysFalse() {
        return (t) -> false;
    }

    /**
     * Returns a predicate that is always true
     * @param <T> The arbitrary type
     * @return The predicate
     */
    public static <T> Predicate<T> alwaysTrue() {
        return (t) -> true;
    }

    public static <T> Consumer<T> appendTo(List<T> values) {
        return values::add;
    }

    public static <T> Consumer<T> appendTo(Set<T> values) {
        return values::add;
    }

    /**
     * Creates a Predicate that resolves to true if the given attribute on the argument
     * resolves to the given value.
     */
    public static Predicate<? extends SailPointObject> attributeEquals(final String attributeName, final Object testValue) {
        return spo -> {
            Attributes<String, Object> attributes = Utilities.getAttributes(spo);
            if (attributes != null) {
                Object val = attributes.get(attributeName);
                return Util.nullSafeEq(val, testValue);
            }
            return (testValue == null);
        };
    }

    /**
     * Creates a flatMap() stream to extract attribute requests from an account request
     */
    public static Function<ProvisioningPlan.AccountRequest, Stream<ProvisioningPlan.AttributeRequest>> attributeRequests() {
        return accountRequest -> accountRequest == null ? Stream.empty() : Utilities.safeStream(accountRequest.getAttributeRequests());
    }

    /**
     * Create a Predicate that resolves to true if the given attribute value is the same
     * (per Sameness) as the given test value.
     */
    public static Predicate<? extends SailPointObject> attributeSame(final String attributeName, final Object testValue) {
        return spo -> {
            Attributes<String, Object> attributes = Utilities.getAttributes(spo);
            if (attributes != null) {
                Object val = attributes.get(attributeName);
                return Sameness.isSame(val, testValue, false);
            }
            return (testValue == null);
        };
    }

    public static BiConsumer<?, ?> bc(bsh.This bshThis, String methodName) {
        return (a, b) -> {
            Object[] params = new Object[]{a, b};
            try {
                bshThis.invokeMethod(methodName, params);
            } catch (EvalError evalError) {
                throw new RuntimeException(evalError);
            }
        };
    }

    public static Class<?> box(Class<?> prim) {
        Objects.requireNonNull(prim, "The class to box must not be null");
        if (prim.equals(Long.TYPE)) {
            return Long.class;
        } else if (prim.equals(Integer.TYPE)) {
            return Integer.class;
        } else if (prim.equals(Short.TYPE)) {
            return Short.class;
        } else if (prim.equals(Character.TYPE)) {
            return Character.class;
        } else if (prim.equals(Byte.TYPE)) {
            return Byte.class;
        } else if (prim.equals(Boolean.TYPE)) {
            return Boolean.class;
        } else if (prim.equals(Float.TYPE)) {
            return Float.class;
        } else if (prim.equals(Double.TYPE)) {
            return Double.class;
        }
        throw new IllegalArgumentException("Unrecognized primitive type: " + prim.getName());
    }

    /**
     * Creates a Consumer that passes each input to the given beanshell method in the
     * given namespace.
     */
    public static ConsumerWithError<Object> c(bsh.This bshThis, String methodName) {
        return object -> {
            Object[] params = new Object[]{object};
            bshThis.invokeMethod(methodName, params);
        };
    }

    /**
     * Creates a Consumer to passes each input to a method on itself.
     */
    public static ConsumerWithError<Object> c(String methodName) {
        return object -> {
            Method method = object.getClass().getMethod(methodName);
            method.invoke(object);
        };
    }

    /**
     * Creates a Consumer to passes each input to a method on itself as the first
     * argument, followed by any of the optional inputs.
     */
    public static ConsumerWithError<Object> c(String methodName, Object... inputs) {
        return object -> {
            Method method = findMethod(object.getClass(), methodName, false, inputs);
            if (method != null) {
                method.invoke(object, inputs);
            }
        };
    }

    /**
     * Returns a function that will cast the input object to the given type
     * or throw a ClassCastException.
     */
    public static <T> Function<Object, T> cast(Class<T> target) {
        return target::cast;
    }

    /**
     * Returns a Comparator that extracts the given property (by path) from the two
     * input objects, then compares them. The property values must be Comparable to
     * each other.
     */
    @SuppressWarnings("rawtypes")
    public static Comparator<Object> comparator(String property) {
        return (a, b) -> {
            try {
                Object o1 = Utilities.getProperty(a, property);
                Object o2 = Utilities.getProperty(b, property);
                if (o1 instanceof Comparable && o2 instanceof Comparable) {
                    return Util.nullSafeCompareTo((Comparable) o1, (Comparable) o2);
                } else {
                    throw new IllegalArgumentException("Property values must both implement Comparable; these are " + safeType(o1) + " and " + safeType(o2));
                }
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
    }

    /**
     * Creates a Comparator that extracts the given property from the two input objects,
     * then passes them both to the given Beanshell method, which must return an appropriate
     * Comparator integer result.
     */
    public static Comparator<Object> comparator(String property, bsh.This bshThis, String methodName) {
        return (a, b) -> {
            try {
                Object o1 = PropertyUtils.getProperty(a, property);
                Object o2 = PropertyUtils.getProperty(b, property);
                Object[] params = new Object[]{o1, o2};
                return Util.otoi(bshThis.invokeMethod(methodName, params));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
    }

    /**
     * Creates a Comparator that directly passes the two input objects to the given
     * Beanshell method. The method must return an appropriate Comparator integer
     * result.
     */
    public static Comparator<Object> comparator(bsh.This bshThis, String methodName) {
        return (a, b) -> {
            Object[] params = new Object[]{a, b};
            try {
                return Util.otoi(bshThis.invokeMethod(methodName, params));
            } catch (EvalError evalError) {
                throw new IllegalStateException(evalError);
            }
        };
    }

    /**
     * Creates a Consumer that invokes the given static method on the given
     * class for each input object. (The "cs" stands for consumer static.)
     */
    public static ConsumerWithError<Object> cs(Class<?> sourceType, String methodName) {
        return object -> {
            Object[] params = new Object[]{object};
            Method method = findMethod(sourceType, methodName, true, params);
            if (method != null) {
                method.invoke(null, params);
            }
        };
    }

    /**
     * Creates a Consumer that invokes the given static method on the given
     * class for each input object, passing the object as the first method
     * parameter and the given param1 as the second. (The "cs" stands for
     * consumer static.)
     */
    public static ConsumerWithError<Object> cs(Class<?> sourceType, String methodName, Object param1) {
        return object -> {
            Object[] params = new Object[]{object, param1};
            Method method = findMethod(sourceType, methodName, true, params);
            if (method != null) {
                method.invoke(null, params);
            }
        };
    }

    /**
     * Creates a Consumer that invokes the given static method on the given
     * class for each input object, passing the object as the first method
     * parameter, the given param1 as the second, and the given param2 as the
     * third.
     */
    public static ConsumerWithError<Object> cs(Class<?> sourceType, String methodName, Object param1, Object param2) {
        return object -> {
            Object[] params = new Object[]{object, param1, param2};
            Method method = findMethod(sourceType, methodName, true, params);
            if (method != null) {
                method.invoke(null, params);
            }
        };
    }

    /**
     * Logs whatever is passed in at debug level, if the logger has debug enabled
     * @param <T> The type of the object
     * @return The consumer
     */
    public static <T> Consumer<T> debug() {
        return debug(log);
    }

    /**
     * Logs whatever is passed in at debug level, if the logger has debug enabled
     * @param logger The logger to which the value should be logged
     * @param <T> The type of the object
     * @return The consumer
     */
    public static <T> Consumer<T> debug(Log logger) {
        return obj -> {
            if (logger.isDebugEnabled()) {
                logger.debug(obj);
            }
        };
    }

    /**
     * Logs the input as XML, useful with peek() in a stream. Since no logger
     * is specified, uses this class's own logger.
     *
     * @param <T> the input type
     * @return The consumer
     */
    public static <T extends AbstractXmlObject> Consumer<T> debugXml() {
        return debugXml(log);
    }

    /**
     * Logs the input as XML, useful with peek() in a stream
     * @param logger The logger to which the object should be logged
     * @param <T> the input type
     * @return The consumer
     */
    public static <T extends AbstractXmlObject> Consumer<T> debugXml(Log logger) {
        return obj -> {
            try {
                if (logger.isDebugEnabled() && obj instanceof AbstractXmlObject) {
                    logger.debug(obj.toXml());
                }
            } catch (GeneralException e) {
                logger.error("Caught an error attempting to debug-log an object XML", e);
            }
        };
    }

    /**
     * Creates a Predicate that returns true if the input string ends with the given suffix
     * @param suffix The suffix
     * @return True if the input string ends with the suffix
     */
    public static Predicate<String> endsWith(String suffix) {
        return (s) -> s.endsWith(suffix);
    }

    /**
     * Returns a Predicate that returns true if the two values are null-safe equals.
     * Two null values will be considered equal.
     *
     * @param value The value to which each input should be compared
     * @param <T> The type of the input expected
     * @return The predicate
     */
    public static <T> Predicate<? extends T> eq(final T value) {
        return o -> Util.nullSafeEq(o, value, true);
    }

    /**
     * Returns a Predicate that returns true if the extracted value from the input is equal
     * (in a null-safe way) to the test value. If your test value is itself a Predicate,
     * it will be invoked to test the extracted value.
     *
     * @param valueExtractor A function to extract a value for comparison from the actual input object
     * @param testValue The text value
     */
    public static <K, T> Predicate<K> eq(Function<K, T> valueExtractor, Object testValue) {
        return (input) -> {
            T value = valueExtractor.apply(input);
            if (testValue instanceof Predicate) {
                return ((Predicate<T>) testValue).test(value);
            } else {
                return Util.nullSafeEq(value, testValue, true);
            }
        };
    }

    public static Predicate<String> eqIgnoreCase(final String value) {
        return o -> Util.nullSafeCaseInsensitiveEq(o, value);
    }

    /**
     * Type-free version of {@link Functions#f(String, Class)}
     */
    public static <K> Function<K, Object> f(String methodName) {
        return f(methodName, Object.class);
    }

    /**
     * Type-free version of {@link Functions#f(String, Class, Object...)}
     */
    public static <K> Function<K, Object> f(String methodName, Object... parameters) {
        return f(methodName, Object.class, parameters);
    }

    /**
     * Creates a Function that invokes the given method on the input object, and
     * returns the output of that method. The given parameters will be passed to
     * the method.
     *
     * NOTE: Beanshell will be confused if you don't pass at least one argument.
     *
     * @param methodName The method name to invoke
     * @param expectedType The expected type of the output
     * @param parameters The remaining parameters to pass to the method
     * @param <T> The value
     * @return The function
     */
    public static <K, T> Function<K, T> f(String methodName, Class<T> expectedType, Object... parameters) {
        return object -> {
            try {
                Class<?> cls = object.getClass();
                Method method = findMethod(cls, methodName, false, parameters);
                if (method != null) {
                    return expectedType.cast(method.invoke(object, parameters));
                }
            } catch (Exception e) {
                /* Ignore */
            }
            return null;
        };
    }

    /**
     * Creates a Function that invokes the named Beanshell method, passing the input
     * object as its parameter, and returning the method's return value.
     *
     * A simplified type-free version of {@link Functions#f(This, String, Class, Object...)}
     *
     * @param bshThis The object on which the method should be invoked
     * @param methodName The method name to invoke
     * @return The function
     */
    public static Function<Object, Object> f(bsh.This bshThis, String methodName) {
        return f(bshThis, methodName, Object.class);
    }

    /**
     * Creates a Function that invokes the named Beanshell method, passing the input
     * object as its parameter, and returning the method's return value.
     *
     * This is roughly equivalent to the class method syntax, ClassName::method.
     *
     * @param bshThis The object on which the method should be invoked
     * @param methodName The method name to invoke
     * @param expectedResult The expected type of the output
     * @param parameters The other parameters to pass to the method
     * @param <B> The type of the output
     * @return The function
     */
    public static <B> Function<Object, B> f(bsh.This bshThis, String methodName, Class<B> expectedResult, Object... parameters) {
        return object -> {
            try {
                List<Object> params = new ArrayList<>();
                params.add(object);
                if (parameters != null) {
                    params.addAll(Arrays.asList(parameters));
                }
                return expectedResult.cast(bshThis.invokeMethod(methodName, params.toArray()));
            } catch (Exception e) {
                // TODO Handle this with an Either
                throw new RuntimeException(e);
            }
        };
    }

    /**
     * Creates a Function that invokes the named method on the given target object,
     * passing the input item as its parameter.
     *
     * This is roughly equivalent to the instance method syntax, obj::method, and can
     * be used to do stuff equivalent to:
     *
     *  list.stream().filter(map::containsKey)...
     *
     * @param target The object on which the method should be invoked
     * @param methodName The method name to invoke
     * @param expectedType The expected type of the output
     * @param <T> The type of the output
     * @return The function
     */
    public static <T> Function<Object, T> f(Object target, String methodName, Class<T> expectedType) {
        if (target instanceof bsh.This) {
            // Beanshell is bad at calling this method; help it out
            return f((bsh.This) target, methodName, expectedType, new Object[0]);
        }
        return object -> {
            try {
                Object[] params = new Object[]{object};
                Class<?> sourceType = target.getClass();
                Method method = findMethod(sourceType, methodName, false, params);
                if (method != null) {
                    return expectedType.cast(method.invoke(target, object));
                } else {
                    log.warn("Could not find matching method " + methodName + " in target class " + target.getClass());
                }
            } catch (Exception e) {
                /* Ignore */
            }
            return null;
        };
    }

    /**
     * Creates a Function that invokes the given method on the input object, and
     * returns the output of that method. This is essentially equivalent to the
     * class method reference syntax Class::method.
     *
     * @param methodName The method name to invoke
     * @param expectedType The expected type of the output
     * @param <T> The value
     * @return The function
     */
    public static <K, T> Function<K, T> f(String methodName, Class<T> expectedType) {
        return object -> {
            try {
                Class<?> cls = object.getClass();
                Method method = cls.getMethod(methodName);
                return expectedType.cast(method.invoke(object));
            } catch (Exception e) {
                /* Ignore */
            }
            return null;
        };
    }

    /**
     * Finds the most specific accessible Method on the given Class that accepts
     * the parameters provided. If two methods are equally specific, which would
     * usually result in an "ambiguous method call" compiler error, an arbitrary
     * one will be returned.
     *
     * @param toSearch   The class to search for the method
     * @param name       The name of the method to locate
     * @param findStatic True if we should look at static methods, false if we should look at instance methods
     * @param params     The intended parameters to the method
     * @return The Method discovered, or null if none match
     */
    public static Method findMethod(Class<?> toSearch, String name, boolean findStatic, Object... params) {
        boolean hasNull = false;
        for(Object in : params) {
            if (in == null) {
                hasNull = true;
            }
        }
        List<Method> foundMethods = new ArrayList<>();
        method:
        for (Method m : toSearch.getMethods()) {
            if (Modifier.isStatic(m.getModifiers()) != findStatic) {
                continue;
            }
            if (m.getName().equals(name)) {
                Parameter[] paramTypes = m.getParameters();
                boolean isVarArgs = m.isVarArgs();
                // Quick and easy case
                if (params.length == 0 && paramTypes.length == 0) {
                    foundMethods.add(m);
                } else if (params.length == paramTypes.length) {
                    int index = 0;
                    for (Parameter p : paramTypes) {
                        Object param = params[index];
                        if (param == null && p.getType().isPrimitive()) {
                            // Can't pass a 'null' to a primitive input, will be an NPE
                            continue method;
                        } else if (param != null && !isAssignableFrom(p.getType(), params[index].getClass())) {
                            continue method;
                        }
                        index++;
                    }
                    foundMethods.add(m);
                } else if (params.length == paramTypes.length - 1 && isVarArgs) {
                    for (int index = 0; index < paramTypes.length - 1; index++) {
                        Parameter p = paramTypes[index];
                        if (params.length > index) {
                            Object param = params[index];
                            if (param == null && p.getType().isPrimitive()) {
                                // Can't pass a 'null' to a primitive input, will be an NPE
                                continue method;
                            } else if (param != null && !isAssignableFrom(p.getType(), params[index].getClass())) {
                                continue method;
                            }
                        }
                    }
                    foundMethods.add(m);
                }
            }
        }
        if (foundMethods.size() == 0) {
            return null;
        } else if (foundMethods.size() == 1) {
            return foundMethods.get(0);
        } else if (hasNull) {
            // We can't proceed here because we can't narrow down what the
            // caller meant by a null. The compiler could do it if we cast
            // the null explicitly, but we can't do it at runtime.
            return null;
        }

        Comparator<Method> methodComparator = (m1, m2) -> {
            int paramCount = m1.getParameterCount();
            int weight = 0;
            for (int p = 0; p < paramCount; p++) {
                Class<?> c1 = m1.getParameterTypes()[p];
                Class<?> c2 = m2.getParameterTypes()[p];

                if (!c1.equals(c2)) {
                    if (isAssignableFrom(c2, c1)) {
                        weight--;
                    } else {
                        weight++;
                    }
                }
            }
            if (weight == 0) {
                Class<?> r1 = m1.getReturnType();
                Class<?> r2 = m2.getReturnType();

                if (!r1.equals(r2)) {
                    if (isAssignableFrom(r2, r1)) {
                        weight--;
                    } else {
                        weight++;
                    }
                }
            }
            return weight;
        };

        foundMethods.sort(methodComparator);

        return foundMethods.get(0);
    }

    /**
     * Transforms a provisioning plan into a list of requests for the given application
     * @param application The application name
     * @return The list of account requests
     */
    public static Function<ProvisioningPlan, List<ProvisioningPlan.AccountRequest>> findRequests(String application) {
        return plan -> plan.getAccountRequests(application);
    }

    /**
     * Transforms a provisioning plan into a list of requests with the given operation
     * @param operation The operation to look for
     * @return The list of account requests
     */
    public static Function<ProvisioningPlan, List<ProvisioningPlan.AccountRequest>> findRequests(ProvisioningPlan.AccountRequest.Operation operation) {
        return plan -> Utilities.safeStream(plan.getAccountRequests()).filter(ar -> Util.nullSafeEq(ar.getOperation(), operation)).collect(Collectors.toList());
    }

    /**
     * Runs the given consumer for each item in the collection
     * @param values The values to iterate
     * @param consumer The consumer to apply to each
     * @param <T> The type of the collection
     */
    public static <T> void forEach(Collection<T> values, Consumer<T> consumer) {
        values.forEach(consumer);
    }

    /**
     * Runs the given consumer for each entry in the map
     * @param values The map of values
     * @param consumer The consumer of the map entries
     * @param <A> The key type
     * @param <B> The value type
     */
    public static <A, B> void forEach(Map<A, B> values, Consumer<Map.Entry<A, B>> consumer) {
        values.entrySet().forEach(consumer);
    }

    /**
     * Runs the given bi-consumer for all of the values in the map
     * @param values The values
     * @param consumer The bi-consumer to be passed the key and value
     * @param <A> The key type
     * @param <B> The value type
     */
    public static <A, B> void forEach(Map<A, B> values, BiConsumer<A, B> consumer) {
        values.forEach(consumer);
    }

    /**
     * Runs the given consumer for each remaining item returned by the Iterator
     * @param values The values to iterate
     * @param consumer The consumer to apply to each
     * @param <A> The type of the values
     */
    public static <A> void forEach(Iterator<A> values, Consumer<A> consumer) {
        values.forEachRemaining(consumer);
    }

    /**
     * Invokes the named static function on the given class, passing the input object to it.
     * The output will be cast to the expected type and returned.
     */
    public static <T> Function<Object, T> fs(Class<?> targetClass, String methodName, Class<T> expectedType) {
        return object -> {
            try {
                Class<?> cls = object.getClass();
                Method method = findMethod(targetClass, methodName, true, object);
                if (method == null) {
                    log.warn("Could not find matching static method " + methodName + " in target class " + targetClass);
                } else {
                    return expectedType.cast(method.invoke(null, object));
                }
            } catch (Exception e) {
                /* Ignore */
            }
            return null;
        };
    }

    /**
     * Functionally implements the {@link Utilities#getProperty(Object, String)} method
     * @param beanPath The path to the property to retrieve from the object
     */
    public static Function<Object, Object> get(String beanPath) {
        return get(Object.class, beanPath);
    }

    /**
     * Functionally implements the {@link Utilities#getProperty(Object, String)} method.
     * If the value is not of the expected type, returns null silently.
     *
     * @param beanPath The path to the property to retrieve from the object
     */
    public static <T> Function<Object, T> get(Class<T> expectedType, String beanPath) {
        return obj -> {
            try {
                return expectedType.cast(getProperty(obj, beanPath, true));
            } catch (Exception e) {
                return null;
            }
        };
    }

    /**
     * If the input object has Attributes, retrieves the attribute with the given name,
     * otherwise returns null.
     *
     * @param attributeName The attribute name to retrieve
     * @return The function
     */
    public static Function<? extends SailPointObject, Object> getAttribute(final String attributeName) {
        return spo -> {
            Attributes<String, Object> attributes = Utilities.getAttributes(spo);
            if (attributes != null) {
                return attributes.get(attributeName);
            }
            return null;
        };
    }

    /**
     * Extracts a boolean attribute from the input object, or false if the
     * attribute is not present.
     *
     * @param attributeName The attribute name
     * @return The function
     */
    public static Function<? extends SailPointObject, Boolean> getBooleanAttribute(final String attributeName) {
        return getBooleanAttribute(attributeName, false);
    }

    /**
     * Extracts a boolean attribute from the input object, or the default value if the
     * attribute is not present.
     *
     * @param attributeName The attribute name
     * @param defaultValue The default value
     * @return The function
     */
    public static Function<? extends SailPointObject, Boolean> getBooleanAttribute(final String attributeName, final boolean defaultValue) {
        return spo -> {
            Attributes<String, Object> attributes = Utilities.getAttributes(spo);
            if (attributes != null) {
                return attributes.getBoolean(attributeName, defaultValue);
            }
            return defaultValue;
        };
    }

    /**
     * Extracts the list of entitlements from the given Identity object, optionally
     * filtering them with the filter if provided. This is a direct query against the
     * database.
     *
     * @param context The context
     * @param optionalFilter An optional filter which will be used to decide which entitlements to return
     * @return The function
     */
    public static Function<Identity, List<IdentityEntitlement>> getEntitlements(SailPointContext context, Filter optionalFilter) {
        return identity -> {
            try {
                QueryOptions qo = new QueryOptions();
                qo.add(Filter.eq("identity.id", identity.getId()));
                if (optionalFilter != null) {
                    qo.add(optionalFilter);
                }
                List<IdentityEntitlement> entitlements = context.getObjects(IdentityEntitlement.class, qo);
                if (entitlements == null) {
                    entitlements = new ArrayList<>();
                }
                return entitlements;
            } catch (GeneralException e) {
                return new ArrayList<>();
            }
        };
    }

    /**
     * Gets all links associated with the given Identity. This will use IdentityService
     * to do a database query rather than pulling the links from the Identity object,
     * because the Identity object's list can be unreliable in long-running DB sessions.
     */
    public static Function<Identity, List<Link>> getLinks() {
        return (identity) -> {
            List<Link> output = new ArrayList<>();
            try {
                SailPointContext context = SailPointFactory.getCurrentContext();
                IdentityService service = new IdentityService(context);
                List<Link> links = service.getLinks(identity, 0, 0);
                if (links != null) {
                    output.addAll(links);
                }
            } catch (GeneralException e) {
                log.error("Caught an exception getting links for identity " + identity.getName() + " in a function");
            }
            return output;
        };
    }

    /**
     * Gets all links associated with the given Identity on the given Application(s).
     */
    public static Function<Identity, List<Link>> getLinks(final String applicationName, final String... moreApplicationNames) {
        if (applicationName == null) {
            throw new IllegalArgumentException("Application name must not be null");
        }

        final List<String> names = new ArrayList<>();
        names.add(applicationName);
        if (moreApplicationNames != null) {
            Collections.addAll(names, moreApplicationNames);
        }

        return (identity) -> {
            List<Link> output = new ArrayList<>();
            try {
                SailPointContext context = SailPointFactory.getCurrentContext();
                for (String name : names) {
                    Application application = context.getObjectByName(Application.class, name);
                    IdentityService service = new IdentityService(context);
                    List<Link> links = service.getLinks(identity, application);
                    if (links != null) {
                        output.addAll(links);
                    }
                }
            } catch (GeneralException e) {
                log.error("Caught an exception getting links for identity " + identity.getName() + " in a function");
            }
            return output;
        };
    }

    /**
     * Gets all links associated with the given Identity on the given Application
     */
    public static Function<Identity, List<Link>> getLinks(final Application application) {
        if (application == null) {
            throw new IllegalArgumentException("Application must not be null");
        }
        return getLinks(application.getName());
    }

    /**
     * Gets the given attribute as a string from the input object
     */
    public static Function<? extends SailPointObject, String> getStringAttribute(final String attributeName) {
        return spo -> {
            Attributes<String, Object> attributes = Utilities.getAttributes(spo);
            if (attributes != null) {
                return attributes.getString(attributeName);
            }
            return null;
        };
    }

    /**
     * Gets the given attribute as a string list from the input object
     */
    public static Function<? extends SailPointObject, List<String>> getStringListAttribute(final String attributeName) {
        return spo -> {
            Attributes<String, Object> attributes = Utilities.getAttributes(spo);
            if (attributes != null) {
                return attributes.getStringList(attributeName);
            }
            return null;
        };
    }

    public static Predicate<ProvisioningPlan.AccountRequest> hasAllAttributeRequest(final String... attributeName) {
        return hasAttributeRequest(true, attributeName);
    }

    public static Predicate<ProvisioningPlan.AccountRequest> hasAnyAttributeRequest(final String... attributeName) {
        return hasAttributeRequest(false, attributeName);
    }

    /**
     * Resolves to true if the input object has a non-null value for the given attribute
     */
    public static Predicate<? extends SailPointObject> hasAttribute(final String attributeName) {
        return spo -> {
            Attributes<String, Object> attributes = Utilities.getAttributes(spo);
            if (attributes != null) {
                return attributes.get(attributeName) != null;
            }
            return false;
        };
    }

    /**
     * Returns a predicate that resolves to true if the account request has the attribute(s)
     * in question, controlled by the 'useAnd' parameter.
     *
     * @param useAnd        If true, all names must match an AttributeRequest. If false, only one match will resolve in a true.
     * @param attributeName The list of attribute names
     */
    public static Predicate<ProvisioningPlan.AccountRequest> hasAttributeRequest(boolean useAnd, final String... attributeName) {
        List<String> attributeNames = new ArrayList<>();
        if (attributeName != null) {
            Collections.addAll(attributeNames, attributeName);
        }
        return spo -> {
            Set<String> fieldNames = new HashSet<>();
            for (ProvisioningPlan.AttributeRequest attributeRequest : Util.safeIterable(spo.getAttributeRequests())) {
                if (attributeNames.isEmpty() || attributeNames.contains(attributeRequest.getName())) {
                    if (useAnd) {
                        fieldNames.add(attributeRequest.getName());
                    } else {
                        return true;
                    }
                }
            }
            return useAnd && fieldNames.size() > attributeNames.size();
        };
    }

    /**
     * Resolves to the ID of the input SailPointObject
     */
    public static Function<? extends SailPointObject, String> id() {
        return SailPointObject::getId;
    }

    /**
     * Resolves to true if the input object is in the given list
     */
    public static Predicate<?> in(final List<String> list) {
        return o -> Util.nullSafeContains(list, o);
    }

    /**
     * Resolves to true if the input object is in the given set
     */
    public static Predicate<?> in(final Set<String> list) {
        return in(new ArrayList<>(list));
    }

    /**
     * Returns true if targetType is assignable from otherType, e.g. if the following code
     * would not fail to compile:
     *
     * OtherType ot = new OtherType();
     * TargetType tt = ot;
     *
     * This is also equivalent to 'b instanceof A' or 'otherType extends targetType'.
     *
     * Primitive types and their boxed equivalents have special handling.
     *
     * @param targetType The first (parent-ish) class
     * @param otherType  The second (child-ish) class
     * @return True if cls1 is assignable from cls2
     */
    public static boolean isAssignableFrom(Class<?> targetType, Class<?> otherType) {
        return Utilities.isAssignableFrom(targetType, otherType);
    }

    /**
     * A predicate that resolves to true if the boolean attribute of the input object
     * is true according to {@link Attributes#getBoolean(String)}.
     *
     * @param attributeName The attribute name to query
     * @return The predicate
     */
    public static Predicate<? extends SailPointObject> isBooleanAttribute(final String attributeName) {
        return isBooleanAttribute(attributeName, false);
    }

    /**
     * A predicate that resolves to true if the boolean attribute of the input object
     * is true according to {@link Attributes#getBoolean(String, boolean)}.
     *
     * @param attributeName The attribute name to query
     * @param defaultValue The default to use if the attribute is empty
     * @return The predicate
     */
    public static Predicate<? extends SailPointObject> isBooleanAttribute(final String attributeName, boolean defaultValue) {
        return spo -> {
            Attributes<String, Object> attributes = Utilities.getAttributes(spo);
            if (attributes != null) {
                return attributes.getBoolean(attributeName, defaultValue);
            }
            return defaultValue;
        };
    }

    /**
     * A predicate that resolves to true if the boolean property of the input object
     * is true according to {@link Util#otob(Object)}
     *
     * @param attributeName The property name to query
     * @return The predicate
     */
    public static Predicate<? extends SailPointObject> isBooleanProperty(final String attributeName) {
        return isBooleanProperty(attributeName, false);
    }

    /**
     * A predicate that resolves to true if the boolean property of the input object
     * is true according to {@link Util#otob(Object)}
     *
     * @param attributeName The property name to query
     * @param defaultValue The default to use if the property is empty
     * @return The predicate
     */
    public static Predicate<? extends SailPointObject> isBooleanProperty(final String attributeName, boolean defaultValue) {
        return spo -> {
            try {
                Object property = getProperty(spo, attributeName);
                return Util.otob(property);
            } catch (GeneralException e) {
                /* Ignore */
            }
            return false;
        };
    }

    /**
     * A predicate that resolves to true if the input is disabled. If the input object is of type Identity,
     * its {@link Identity#isInactive()} will be called. Otherwise, {@link SailPointObject#isDisabled()}.
     * @param <T> The type of the object
     * @return The predicate
     */
    public static <T extends SailPointObject> Predicate<T> isDisabled() {
        return spo -> {
            if (spo instanceof Identity) {
                return ((Identity) spo).isInactive();
            } else {
                return spo.isDisabled();
            }
        };
    }

    /**
     * Returns a predicate that resolves to true if the input is empty according to Sameness
     * @param <T> The type of the object
     * @return The preidcate
     */
    public static <T> Predicate<T> isEmpty() {
        return Sameness::isEmpty;
    }

    /**
     * Resolves to true if the input object is an instance of the target
     * class. For specific common classes, this uses a quicker instanceof,
     * and for everything else, it passes off to isAssignableFrom.
     * <p>
     * Null inputs will always be false.
     */
    public static <T> Predicate<T> isInstanceOf(Class<?> target) {
        Objects.requireNonNull(target);
        // Faster versions for specific common classes
        if (target.equals(String.class)) {
            return obj -> obj instanceof String;
        } else if (target.equals(List.class)) {
            return obj -> obj instanceof List;
        } else if (target.equals(Map.class)) {
            return obj -> obj instanceof Map;
        } else if (target.equals(SailPointObject.class)) {
            return obj -> obj instanceof SailPointObject;
        } else if (target.equals(Identity.class)) {
            return obj -> obj instanceof Identity;
        } else if (target.equals(Link.class)) {
            return obj -> obj instanceof Link;
        }
        return obj -> (obj != null && isAssignableFrom(target, obj.getClass()));
    }

    /**
     * Returns true if the input Link's native identity is equal to the
     * comparison value.
     */
    public static Predicate<Link> isNativeIdentity(final String nativeIdentity) {
        return link -> Util.nullSafeEq(link.getNativeIdentity(), nativeIdentity);
    }

    public static Predicate<String> isNotNullOrEmpty() {
        return Util::isNotNullOrEmpty;
    }

    /**
     * Resolves to true if the object is not null
     */
    public static <T> Predicate<T> isNull() {
        return Objects::isNull;
    }

    /**
     * Resolves to true if the given property on the given object is
     * not null or empty. Emptiness is defined by this class's isEmpty().
     */
    public static <T> Predicate<T> isNullOrEmpty(String propertyPath) {
        return o -> {
            try {
                Object property = getProperty(o, propertyPath);
                if (property == null) {
                    return true;
                }
                return isEmpty().test(property);
            } catch (GeneralException e) {
                /* Ignore this */
            }
            return true;
        };
    }

    /**
     * Functional equivalent to Util.isNullOrEmpty
     */
    public static Predicate<String> isNullOrEmpty() {
        return Util::isNullOrEmpty;
    }

    /**
     * Extracts the key from the given map entry
     */
    public static <T> Function<Map.Entry<T, ?>, T> key() {
        return Map.Entry::getKey;
    }

    /**
     * Returns true if the input string matches the given pattern, as
     * equivalent to the Filter like() method
     */
    public static Predicate<? extends String> like(final String pattern, final Filter.MatchMode matchMode) {
        if (Util.isNullOrEmpty(pattern)) {
            throw new IllegalArgumentException("Pattern must not be null or empty");
        }
        if (matchMode.equals(Filter.MatchMode.EXACT)) {
            return eq(pattern);
        }
        return s -> {
            if (s == null || s.isEmpty()) {
                return false;
            }
            if (matchMode.equals(Filter.MatchMode.START)) {
                return s.startsWith(pattern);
            } else if (matchMode.equals(Filter.MatchMode.END)) {
                return s.endsWith(pattern);
            } else if (matchMode.equals(Filter.MatchMode.ANYWHERE)) {
                return s.contains(pattern);
            } else {
                return false;
            }
        };
    }

    /**
     * Returns the Identity for the given Link object
     * @return Gets the Link identity
     */
    public static Function<Link, Identity> linkGetIdentity() {
        return Link::getIdentity;
    }

    /**
     * Creates a predicate that tests whether the Link has the given application name
     * @param applicationName The application name
     * @return The predicate
     */
    public static Predicate<Link> linkIsApplication(String applicationName) {
        return (link) -> Util.nullSafeEq(link.getApplicationName(), applicationName);
    }

    /**
     * Creates a predicate that tests whether the Link has the given application name.
     * The ID will be cached in the closure, so you do not need to keep the Application
     * object in the same Hibernate scope.
     *
     * @param application The application object
     * @return The predicate
     */
    public static Predicate<Link> linkIsApplication(Application application) {
        final String applicationId = application.getId();
        return (link) -> Util.nullSafeEq(link.getApplicationId(), applicationId);
    }


    /**
     * Returns a functional getter for a map of a given type
     * @param index The index to get
     * @param <T> The type stored in the list
     * @return The output
     */
    public static <T> Function<List<T>, T> listSafeGet(int index) {
        return (list) -> {
            if (list.size() <= index) {
                return null;
            } else if (index < 0) {
                return null;
            } else {
                return list.get(index);
            }
        };
    }

    /**
     * Logs the input object at warn() level in the default Functions logger.
     * This is intended for use with peek() in the middle of a stream.
     */
    public static <T> Consumer<T> log() {
        return (obj) -> {
            if (log.isWarnEnabled()) {
                log.warn(obj);
            }
        };
    }

    /**
     * Logs the input object at warn() level in the provided logger
     * This is intended for use with peek() in the middle of a stream.
     */
    public static <T> Consumer<T> log(Log logger) {
        return (obj) -> {
            if (logger.isWarnEnabled()) {
                logger.warn(obj);
            }
        };
    }

    /**
     * Converts the given object to XML and logs it to the default logger as a warning
     */
    public static <T extends AbstractXmlObject> Consumer<T> logXml() {
        return logXml(log);
    }

    /**
     * Converts the given object to XML and logs it to the given logger
     */
    public static <T extends AbstractXmlObject> Consumer<T> logXml(Log logger) {
        return obj -> {
            try {
                if (logger.isWarnEnabled() && obj instanceof AbstractXmlObject) {
                    logger.warn(obj.toXml());
                }
            } catch (GeneralException e) {
                logger.error("Caught an error attempting to log an object XML", e);
            }
        };
    }

    /**
     * For the given input key, returns the value of that key in the given map
     */
    public static Function<String, Object> lookup(final Map<String, Object> map) {
        return key -> {
            try {
                return MapUtil.get(map, key);
            } catch (GeneralException e) {
                /* Ignore */
                return null;
            }
        };
    }

    /**
     * For the given input map, returns the value of the key. The key is a
     * MapUtil path.
     */
    public static Function<Map<String, Object>, Object> lookup(final String key) {
        return map -> {
            try {
                return MapUtil.get(map, key);
            } catch (GeneralException e) {
                /* Ignore */
                return null;
            }
        };
    }

    /**
     * Same as lookup(String), but casts the output to the expected type
     */
    public static <B> Function<Map<String, B>, B> lookup(final String key, final Class<B> expectedType) {
        return map -> {
            try {
                return expectedType.cast(MapUtil.get((Map<String, Object>) map, key));
            } catch (GeneralException e) {
                return null;
            }
        };
    }

    /**
     * Uses the given context and class name to look up the object
     * corresponding to the input string
     */
    public static <T extends SailPointObject> Function<String, T> lookup(final SailPointContext context, final Class<T> sailpointClass) {
        return name -> {
            try {
                return context.getObject(sailpointClass, name);
            } catch (GeneralException e) {
                return null;
            }
        };
    }

    /**
     * Returns a functional getter for a map of a given type
     * @param key The key to get
     * @param <T> The key type of the map
     * @param <U> The value type of the map
     * @return The output
     */
    public static <T, U> Function<Map<T, U>, U> mapGet(T key) {
        return (map) -> map.get(key);
    }

    /**
     * Returns a functional predicate to compare a Map to the filter provided.
     *
     * @param filter The filter to execute against the Map
     * @param <T> The key type of the map
     * @param <U> The value type of the map
     * @return The output
     */
    public static <T, U> PredicateWithError<Map<T, U>> mapMatches(Filter filter) {
        return (map) -> {
            MapMatcher matcher = new MapMatcher(filter);
            return matcher.matches(map);
        };
    }

    /**
     * Creates a map comparator for sorting a map against the given key(s). Additional
     * keys are implemented using recursive calls back to this method.
     *
     * @param key The first key for comparing
     * @param keys A list of additional keys for comparing
     * @return The comparator
     */
    public static <K> Comparator<Map<K, Object>> mapValueComparator(K key, K... keys) {
        Comparator<Map<K, Object>> mapComparator = Comparator.comparing(mapGet(key).andThen(otoa()));
        if (keys != null) {
            for(K otherKey : keys) {
                if (otherKey != null) {
                    mapComparator = mapComparator.thenComparing(mapValueComparator(otherKey));
                }
            }
        }
        return mapComparator;
    }

    /**
     * Creates a Predicate that retrieves the key of the given name from the input
     * Map and returns true if the value equals the given value.
     *
     * @param key The key to query in the map
     * @param value The value to query in the map
     */
    public static <T, U> Predicate<Map<T, U>> mapValueEquals(T key, U value) {
        return eq(Functions.mapGet(key), value);
    }

    /**
     * Resolves to true if the input object matches the filter. This ought to be thread-safe
     * if the SailPointFactory's current context is correct for the thread.
     */
    public static PredicateWithError<? extends SailPointObject> matches(Filter filter) {
        return spo -> {
            HybridObjectMatcher matcher = new HybridObjectMatcher(SailPointFactory.getCurrentContext(), filter, false);
            return matcher.matches(spo);
        };
    }

    /**
     * Resolves to true if the input object matches the filter
     */
    public static PredicateWithError<? extends SailPointObject> matches(String filterString) {
        Filter filter = Filter.compile(filterString);
        return matches(filter);
    }

    /**
     * Maps to the name of the input object
     */
    public static Function<? extends SailPointObject, String> name() {
        return SailPointObject::getName;
    }

    /**
     * Resolves to true if the input object does not equal the value
     */
    public static <T> Predicate<? extends T> ne(final T value) {
        return eq(value).negate();
    }

    /**
     * Returns a supplier returning a new, writeable list. This can be passed to {@link Collectors#toCollection(Supplier)} for example.
     * @param <T> The type of the list
     * @return the new ArrayList supplier
     */
    public static <T> Supplier<List<T>> newList() {
        return ArrayList::new;
    }

    /**
     * Returns a supplier returning a new, writeable set. This can be passed to {@link Collectors#toCollection(Supplier)} for example.
     * @param <T> The type of the list
     * @return the new HashSet supplier
     */
    public static <T> Supplier<Set<T>> newSet() {
        return HashSet::new;
    }

    /**
     * Returns a predicate that resolves to true if the input item is not null
     * @return The predicate
     */
    public static Predicate<?> nonNull() {
        return Objects::nonNull;
    }

    /**
     * A very basic normalizer function that trims spaces and lowercases the
     * input string, then removes all non-ASCII characters. This should be replaced
     * with a better normalization algorithm in most cases.
     *
     * @return The function
     */
    public static Function<String, String> normalize() {
        return (s) -> s == null ? "" : s.trim().toLowerCase(Locale.getDefault()).replaceAll("[^A-Za-z0-9_]+", "");
    }

    /**
     * Implements a generic "black hole" or "dev null" consumer that swallows
     * the object. This can be used as a sane default where another consumer
     * is required.
     */
    public static NullConsumer nothing() {
        return new NullConsumer();
    }

    /**
     * Returns true if the two input objects are equal ignoring case
     */
    public static BiFunction<Object, Object, Boolean> nullSafeEq() {
        return Util::nullSafeEq;
    }

    /**
     * Transforms the input object into a non-null string
     */
    public static Function<Object, String> nullToEmpty() {
        return Utilities::safeString;
    }

    /**
     * Resolves to true if the given AccountRequest's operation equals the given value
     */
    public static Predicate<ProvisioningPlan.AccountRequest> operationEquals(ProvisioningPlan.AccountRequest.Operation operation) {
        return accountRequest -> Util.nullSafeEq(accountRequest.getOperation(), operation);
    }

    /**
     * Resolves to true if the given provisioning plan's operation equals the given value
     */
    public static Predicate<ProvisioningPlan.AttributeRequest> operationEquals(ProvisioningPlan.Operation operation) {
        return attributeRequest -> Util.nullSafeEq(attributeRequest.getOperation(), operation);
    }

    /**
     * Creates a function equivalent to Util::otoa
     * @return The function
     */
    public static <T> Function<T, String> otoa() {
        return Util::otoa;
    }

    /**
     * Returns an object that implements both Function and Predicate and returns
     * the result of Util::otob on the input object
     * @return The function/predicate
     */
    public static OtobWrapper otob() {
        return OtobWrapper.get();
    }

    /**
     * Creates a function equivalent to Util::otoi
     * @return The function
     */
    public static <T> Function<T, Integer> otoi() {
        return Util::otoi;
    }

    /**
     * Creates a function equivalent to Util::otol
     * @return The function
     */
    public static <T> Function<T, List<String>> otol() {
        return Util::otol;
    }

    /**
     * Transforms a Function that returns a boolean into a predicate
     * @param f The function to transform
     * @return The predicate
     */
    public static <T> Predicate<T> p(Function<T, Boolean> f) {
        return f::apply;
    }

    /**
     * Invokes the given method on the input object, resolving to the
     * otob truthy conversion of its output.
     */
    @SuppressWarnings("unchecked")
    public static <T> Predicate<T> p(String methodName) {
        return (Predicate<T>) p(f(methodName).andThen(Util::otob));
    }

    /**
     * Invokes the given method against the target object, passing the
     * input object as its only parameter, then resolves to true if the
     * output is otob truthy.
     */
    public static Predicate<?> p(Object target, String methodName) {
        return p(f(target, methodName, Object.class).andThen(Util::otob));
    }

    /**
     * Invokes the given method against the input object, providing the
     * additional inputs as needed, then returns true if the result is
     * otob truthy.
     */
    public static Predicate<?> p(String methodName, Object... inputs) {
        return p(f(methodName, inputs).andThen(Util::otob));
    }

    /**
     * Invokes the given Beanshell method, which will receive the input
     * object as its sole parameter, and then resolves to true if the
     * method returns an otob truthy value.
     */
    public static Predicate<?> p(bsh.This bshThis, String methodName) {
        return p(f(bshThis, methodName).andThen(Util::otob));
    }

    /**
     * Parses each incoming string as a date according to the provided format,
     * returning null if there is a parse exception
     */
    public static Function<String, Date> parseDate(String format) {
        if (Util.isNotNullOrEmpty(format)) {
            throw new IllegalArgumentException("The format string " + format + " is not valid");
        }
        // Also throws an exception if it's a bad format
        SimpleDateFormat preParse = new SimpleDateFormat(format);
        return date -> {
            final SimpleDateFormat formatter = new SimpleDateFormat(format);
            try {
                return formatter.parse(date);
            } catch (ParseException e) {
                if (log.isDebugEnabled()) {
                    log.debug("Could not parse input string " + date + " according to formatter " + format);
                }
                return null;
            }
        };
    }

    /**
     * Returns true if the given plan has the given attribute on the given
     * application. This can be done in a more fluent way using Plans.find
     * if desired.
     */
    public static Predicate<ProvisioningPlan> planHasAttribute(String application, String attribute) {
        return plan -> (
                Utilities.safeStream(plan.getAccountRequests())
                        .filter(ar -> Util.nullSafeEq(ar.getApplicationName(), application))
                        .flatMap(ar -> Utilities.safeStream(ar.getAttributeRequests()))
                        .anyMatch(attr -> Util.nullSafeEq(attr.getName(), attribute)));
    }

    public static Predicate<ProvisioningPlan> planHasOperation(ProvisioningPlan.AccountRequest.Operation operation) {
        return plan -> (Utilities.safeStream(plan.getAccountRequests()).anyMatch(ar -> Util.nullSafeEq(ar.getOperation(), operation)));
    }

    public static Predicate<ProvisioningPlan> planHasOperation(String application, ProvisioningPlan.AccountRequest.Operation operation) {
        return plan -> (Utilities.safeStream(plan.getAccountRequests()).anyMatch(ar -> Util.nullSafeEq(ar.getApplicationName(), application) && Util.nullSafeEq(ar.getOperation(), operation)));
    }

    /**
     * Maps to a Stream of provisioning plans (for use with flatMap) in the given project
     */
    public static Function<ProvisioningProject, Stream<ProvisioningPlan>> plans() {
        return (project) -> project == null ? Stream.empty() : Utilities.safeStream(project.getPlans());
    }

    /**
     * Resolves to true if the given property value on the input object can be
     * coerced to a Date and is after the given Date.
     */
    public static Predicate<Object> propertyAfter(final String propertyPath, final Date test) {
        return source -> {
            try {
                Object propertyValue = Utilities.getProperty(source, propertyPath);
                if (propertyValue == null) {
                    return false;
                }
                if (propertyValue instanceof Date) {
                    Date d = (Date) propertyValue;
                    return d.after(test);
                } else if (propertyValue instanceof Number) {
                    long date = ((Number) propertyValue).longValue();
                    Date d = new Date(date);
                    return d.after(test);
                } else if (propertyValue instanceof String) {
                    long date = Long.parseLong((String) propertyValue);
                    Date d = new Date(date);
                    return d.after(test);
                }
            } catch (GeneralException e) {
                /* Nothing */
            }
            return false;
        };
    }

    /**
     * Resolves to true if the given property value on the input object can be
     * coerced to a Date and is before the given Date.
     */
    public static Predicate<Object> propertyBefore(final String propertyPath, final Date test) {
        return source -> {
            try {
                Object propertyValue = Utilities.getProperty(source, propertyPath);
                if (propertyValue == null) {
                    return false;
                }
                if (propertyValue instanceof Date) {
                    Date d = (Date) propertyValue;
                    return d.before(test);
                } else if (propertyValue instanceof Number) {
                    long date = ((Number) propertyValue).longValue();
                    Date d = new Date(date);
                    return d.before(test);
                } else if (propertyValue instanceof String) {
                    long date = Long.parseLong((String) propertyValue);
                    Date d = new Date(date);
                    return d.before(test);
                }
            } catch (GeneralException e) {
                /* Nothing */
            }
            return false;
        };
    }

    /**
     * Returns true if the given property on the input object equals the test value
     */
    public static Predicate<Object> propertyEquals(final String propertyPath, final Object test) {
        return source -> {
            try {
                Object propertyValue = Utilities.getProperty(source, propertyPath);
                return Util.nullSafeEq(propertyValue, test);
            } catch (GeneralException e) {
                return false;
            }
        };
    }

    /**
     * Returns true if the property at the given path matches the given regex. If the
     * property is a string it will be used directly. If the property is a List containing
     * only one string, that string will be extracted and used.
     *
     * In all other cases, including parse errors, false will be returned.
     *
     * @param propertyPath The path to the property via {@link Utilities#getProperty(Object, String)}
     * @param regexString The regular expression string
     * @return A predicate that returns true if the property extracted matches the
     */
    public static Predicate<Object> propertyMatchesRegex(final String propertyPath, final String regexString) {
        Pattern regex = Pattern.compile(regexString);

        return object -> {
            try {
                Object propertyValue = getProperty(object, propertyPath, true);
                if (propertyValue instanceof String) {
                    String stringProperty = (String) propertyValue;
                    Matcher regexMatcher = regex.matcher(stringProperty);
                    return regexMatcher.find();
                } else if (propertyValue instanceof List) {
                    List<?> listProperty = (List<?>) propertyValue;
                    if (listProperty.size() == 1) {
                        Object onlyValue = listProperty.get(0);
                        if (onlyValue instanceof String) {
                            String stringProperty = (String) onlyValue;
                            Matcher regexMatcher = regex.matcher(stringProperty);
                            return regexMatcher.find();
                        }
                    }
                }
            } catch(GeneralException e) {
                return false;
            }
            return false;
        };
    }

    /**
     * Returns true if the given property on the input object is the same as the
     * test value, per Sameness rules
     */
    public static Predicate<Object> propertySame(final String propertyPath, final Object test) {
        return source -> {
            try {
                Object propertyValue = Utilities.getProperty(source, propertyPath);
                return Sameness.isSame(propertyValue, test, false);
            } catch (GeneralException e) {
                return false;
            }
        };
    }

    /**
     * Safely casts the value to the given class, returning null if it can't be
     * cast to that value.
     *
     * @param expectedClass The expected class
     * @param <T> The expected class type
     * @return The value cast to the given type, or null if not that type
     */
    public static <T> Function<Object, T> safeCast(Class<T> expectedClass) {
        return o -> Utilities.safeCast(o, expectedClass);
    }

    /**
     * Safely retrieves the given property from the input object, returning
     * the default value if the result is null or throws an exception.
     */
    public static <T> Function<Object, T> safeGet(String propertyName, T defaultValue, Class<T> expectedClass) {
        return o -> {
            try {
                Object result = Utilities.getProperty(o, propertyName, true);
                if (result == null) {
                    return defaultValue;
                }
                return expectedClass.cast(result);
            } catch (Exception e) {
                return defaultValue;
            }
        };
    }

    /**
     * Safely retrieves the given property from the input object, returning
     * null if the property value is not of the expected type.
     */
    public static <T> Function<Object, T> safeGet(String propertyName, Class<T> expectedClass) {
        return o -> {
            try {
                return expectedClass.cast(Utilities.getProperty(o, propertyName));
            } catch (Exception e) {
                return null;
            }
        };
    }

    public static Function<Object, List<String>> safeListify() {
        return Utilities::safeListify;
    }

    private static Class<?> safeType(Object obj) {
        if (obj == null) {
            return null;
        }
        return obj.getClass();
    }

    /**
     * Returns true if the input value is the same as the given test value,
     * per Sameness rules
     */
    public static Predicate<?> sameAs(final Object value) {
        return sameAs(value, false);
    }

    /**
     * Returns true if the input value is the same as the given test value,
     * ignoring case, per Sameness rules
     */
    public static Predicate<?> sameAs(final Object value, boolean ignoreCase) {
        return o -> Sameness.isSame(o, value, ignoreCase);
    }

    /**
     * Supplies a value by invoking the given Beanshell method with the
     * given parameters. On error, returns null.
     */
    public static SupplierWithError<Boolean> sb(final bsh.This bsh, final String methodName, final Object... params) {
        return () -> Util.otob(bsh.invokeMethod(methodName, params));
    }

    /**
     * Creates a comparator that sorts in the order specified, leaving the
     * input objects alone. Equivalent to calling sortListOrder and passing
     * {@link Function#identity()} as the translator.
     */
    public static <ListItem> Comparator<ListItem> sortListOrder(List<ListItem> order) {
        return sortListOrder(order, Function.identity());
    }

    /**
     * Creates a comparator that sorts in the order specified, translating input
     * values into sort keys first via the provided translator. Values not in the
     * order list will be sorted to the end of the list.
     *
     * For example, you might sort a list of Links into a specific order by
     * application to create a precedence structure.
     *
     * If no order is specified, the resulting Comparator will laboriously
     * leave the list in the original order.
     *
     * @param <ListItem> The type of the item being sorted
     * @param <SortType> The type of item in the ordering list
     * @param order The ordering to apply
     * @param keyExtractor The translator
     * @return The comparator
     */
    public static <ListItem, SortType> Comparator<ListItem> sortListOrder(List<SortType> order, Function<ListItem, SortType> keyExtractor) {
        Map<SortType, Integer> orderMap = new HashMap<>();
        if (order != null) {
            int index = 0;
            for (SortType v : order) {
                orderMap.put(v, index++);
            }
        }

        return (v1, v2) -> {
            SortType key1 = keyExtractor.apply(v1);
            SortType key2 = keyExtractor.apply(v2);

            int o1 = orderMap.getOrDefault(key1, Integer.MAX_VALUE);
            int o2 = orderMap.getOrDefault(key2, Integer.MAX_VALUE);

            return (o1 - o2);
        };
    }

    /**
     * Returns true if the input string starts with the given prefix
     */
    public static Predicate<String> startsWith(String prefix) {
        return (s) -> s.startsWith(prefix);
    }

    /**
     * Creates a stream out of the given SailPoint search
     * @param context The context
     * @param spoClass The SailPointObject to search
     * @param qo The QueryOptions
     * @param <A> The object type
     * @return The stream
     * @throws GeneralException if the query fails
     */
    public static <A extends SailPointObject> Stream<A> stream(SailPointContext context, Class<A> spoClass, QueryOptions qo) throws GeneralException {
        IncrementalObjectIterator<A> objects = new IncrementalObjectIterator<>(context, spoClass, qo);
        return StreamSupport.stream(Spliterators.spliterator(objects, objects.getSize(), Spliterator.ORDERED), false).onClose(() -> {
            Util.flushIterator(objects);
        });
    }

    /**
     * Creates a stream out of the given SailPoint projection search
     * @param context The context
     * @param spoClass The SailPoint class
     * @param qo The query filters
     * @param props The query properties to query
     * @param <A> The object type to query
     * @return The stream
     * @throws GeneralException if the query fails
     */
    public static <A extends SailPointObject> Stream<Object[]> stream(SailPointContext context, Class<A> spoClass, QueryOptions qo, List<String> props) throws GeneralException {
        IncrementalProjectionIterator objects = new IncrementalProjectionIterator(context, spoClass, qo, props);
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(objects, Spliterator.ORDERED), false).onClose(() -> {
            Util.flushIterator(objects);
        });
    }


    /**
     * Equivalent to {@link #stream(Function, Class)} if passed Object.class
     */
    public static <A> Function<A, Stream<Object>> stream(Function<A, ? extends Object> input) {
        return stream(input, Object.class);
    }

    /**
     * Applies the given function to the input object and then makes a Stream
     * out of it. When used with other functions, this is handy for flatMap.
     *
     * If the result of the function is not of the expected type, or is not a
     * List, Map, or Set of them, returns an empty Stream.
     */
    @SuppressWarnings("unchecked")
    public static <A, T> Function<A, Stream<T>> stream(Function<A, ?> input, Class<T> expectedType) {
        return object -> {
            Object result = input.apply(object);
            if (result == null) {
                return Stream.empty();
            }
            if (result instanceof List) {
                return Utilities.safeStream((List)result);
            } else if (result instanceof Set) {
                return Utilities.safeStream((Set)result);
            } else if (result instanceof Map) {
                return Utilities.safeStream(((Map)result).entrySet());
            } else if (expectedType.isAssignableFrom(result.getClass())) {
                return (Stream<T>)Stream.of(result);
            } else {
                return Stream.empty();
            }
        };
    }

    /**
     * Returns a mapping function producing the value of the input map entry
     * @param <T> The map entry's value type
     * @return A function producing the resulting value
     */
    public static <T> Function<Map.Entry<?, T>, T> value() {
        return Map.Entry::getValue;
    }
}
