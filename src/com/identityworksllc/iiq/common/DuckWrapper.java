package com.identityworksllc.iiq.common;

import sailpoint.tools.GeneralException;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Proxy wrapper for duck typing in Java. Duck typing is named for the old saying
 * that 'if it quacks like a duck, it must be a duck'. In dynamically typed languages
 * like JavaScript, method handles are resolved at runtime so any object implementing
 * the appropriate methods can be used.
 *
 * This class creates a {@link Proxy} that forwards interface methods to the given
 * wrapped object. This allows you to 'fake' an interface implementation where you
 * don't have control over the class's source code.
 *
 * If the object being wrapped doesn't have a method matching something in your
 * interface, it will just be ignored. Invoking the method will always return
 * null with any arguments.
 *
 * Some examples:
 *
 * 1) Many SailPointObjects have a getAttributes(), but those classes don't implement
 * a common interface. As a developer, you don't have access to modify SailPoint's
 * API classes. To simplify your own code, you could create an AttributesContainer
 * interface and use this class to coerce the SailPointObject to that interface.
 *
 * 2) IIQ has two different nearly-identical versions of WebServicesClient. One
 * is inaccessible except through the connector classloader. You could construct
 * an instance of the inaccessible client class, exposing any relevant methods
 * to your application via your own interface.
 */
public class DuckWrapper implements InvocationHandler {
    /**
     * Wraps the given object so that it appears to implement the given interface.
     * Any calls to the interface methods will forward to the most appropriate method
     * on the object.
     *
     * @param intf The interface the resulting proxy needs to implement
     * @param input The target object to be wrapped within the proxy
     * @param <T> The resulting type
     * @return A proxy to the underlying object that appears to implement the given interface
     * @throws GeneralException if any failures occur establishing the proxy
     */
    @SuppressWarnings("unchecked")
    public static <T> T wrap(Class<? super T> intf, Object input) throws GeneralException {
        return wrap(intf, input, null);
    }

    /**
     * Wraps the given object in the given interface. Any calls to the
     * interface methods will forward to the object.
     *
     * @param intf The interface the resulting proxy needs to implement
     * @param input The target object to be wrapped within the proxy
     * @param callback An optional callback that will be invoked for every method called (for testing)
     * @param <T> The resulting type
     * @return A proxy to the underlying object that appears to implement the given interface
     * @throws GeneralException if any failures occur establishing the proxy
     */
    @SuppressWarnings("unchecked")
    public static <T> T wrap(Class<? super T> intf, Object input, BiConsumer<Method, MethodHandle> callback) throws GeneralException {
        try {
            return (T) Proxy.newProxyInstance(intf.getClassLoader(), new Class[]{intf}, new DuckWrapper(intf, input, callback));
        } catch(Exception e) {
            throw new GeneralException(e);
        }
    }

    private final BiConsumer<Method, MethodHandle> callback;

    /**
     * The method handles (null means no match)
     */
    private final Map<String, MethodHandle> methodHandleMap;

    /**
     * The wrapped object
     */
    private final Object wrapped;

    /**
     * Constructs a new instance of DuckWrapper, with the given expected interface,
     * wrapped object, and optional callback.
     *
     * @param intf The interface to analyze
     * @param wrapped The wrapped object to analyze
     * @param callback The testing callback method
     * @throws Exception if any failures occur analyzing methods
     */
    private DuckWrapper(Class<?> intf, Object wrapped, BiConsumer<Method, MethodHandle> callback) throws Exception {
        this.wrapped = wrapped;
        this.methodHandleMap = new HashMap<>();
        this.callback = callback;

        MethodHandles.Lookup publicLookup = MethodHandles.publicLookup();

        for(Method m : intf.getMethods()) {
            MethodType type = MethodType.methodType(m.getReturnType(), m.getParameterTypes());
            try {
                MethodHandle mh = publicLookup.findVirtual(wrapped.getClass(), m.getName(), type);
                methodHandleMap.put(m.toString(), mh);
            } catch(NoSuchMethodException e) {
                // This is fine. invoke() will just return null
                methodHandleMap.put(m.toString(), null);
            }
        }
    }

    /**
     * Invokes the optional callback, and then the method on the wrapped object, in
     * that order. If the method handle didn't match (i.e., your interface has a
     * method not matched in the wrapped object), this will return null without
     * failing.
     *
     * @see InvocationHandler#invoke(Object, Method, Object[])
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        MethodHandle handle = methodHandleMap.get(method.toString());
        if (callback != null) {
            callback.accept(method, handle);
        }
        if (handle == null) {
            return null;
        }
        int length = 0;
        if (args != null) {
            length = args.length;
        }
        Object[] arguments = new Object[length + 1];
        arguments[0] = wrapped;
        if (args != null) {
            System.arraycopy(args, 0, arguments, 1, args.length);
        }
        return handle.invokeWithArguments(arguments);
    }
}
