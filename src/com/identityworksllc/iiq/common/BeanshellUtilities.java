package com.identityworksllc.iiq.common;

import bsh.NameSpace;
import bsh.Primitive;
import bsh.UtilEvalError;
import org.apache.bsf.BSFManager;
import org.apache.bsf.util.BSFFunctions;
import org.apache.commons.logging.Log;
import sailpoint.object.TaskResult;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Objects;

/**
 * Utilities for working with Beanshell at a language level
 */
@SuppressWarnings("unused")
public class BeanshellUtilities {
	/**
	 * Create a Proxy implementing the given interface which will invoke a similarly
	 * named Beanshell method for any function call to the interface.
	 *
	 * @param bshThis The 'this' instance in Beanshell
	 * @param types The interface types that the Beanshell context ought to proxy
	 * @return the proxy implementing the interface
	 */
	public static Object coerce(bsh.This bshThis, Class<?>... types) {
		return Proxy.newProxyInstance(BeanshellUtilities.class.getClassLoader(), types,
				(proxy, method, args) -> bshThis.invokeMethod(method.getName(), args)
		);
	}

	/**
	 * Create a Proxy implementing the given class which will invoke the
	 * given Beanshell function for any method call. This should be used
	 * mainly for "functional" interfaces with a single abstract method,
	 * like Runnable or Callable or the various event handlers.
	 *
	 * @param type The proxy type
	 * @param bshThis The 'this' instance in Beanshell
	 * @param methodName The method name to invoke on method call
	 * @param <T> the interface type to implement
	 * @return the proxy implementing the interface
	 */
	public static <T> T coerce(Class<T> type, bsh.This bshThis, String methodName) {
		return coerce(type, bshThis, methodName, methodName);
	}

	/**
	 * Create a Proxy implementing the given class which will invoke the
	 * given Beanshell function for any method call. This should be used
	 * mainly for "functional" interfaces with a single abstract method,
	 * like Runnable or Callable or the various event handlers.
	 *
	 * @param type The proxy type
	 * @param bshThis The 'this' instance in Beanshell
	 * @param targetMethod The method to intercept on the interface (e.g. 'run' on a Runnable)
	 * @param methodName The Beanshell method name to invoke on method call
	 * @param <T> the interface type to implement
	 * @return the proxy implementing the interface
	 */
	@SuppressWarnings("unchecked")
	public static <T> T coerce(Class<T> type, bsh.This bshThis, String targetMethod, String methodName) {
		Class<?>[] interfaces = new Class<?>[] {Objects.requireNonNull(type)};
		return (T) Proxy.newProxyInstance(BeanshellUtilities.class.getClassLoader(), interfaces,
				(proxy, method, args) ->
				{
					if (method.getName().equals(targetMethod)) {
						return bshThis.invokeMethod(methodName, args);
					} else {
						return null;
					}
				}
		);
	}

	/**
	 * Dumps the beanshell namespace passed to this method to the
	 * given logger
	 * @param namespace The beanshell namespace to dump
	 * @throws UtilEvalError On errors getting the variable value
	 * @throws GeneralException If a Sailpoint exception occurs
	 */
	public static void dump(bsh.This namespace, Log logger) throws UtilEvalError, GeneralException {
		for(String name : namespace.getNameSpace().getVariableNames()) {
			if ("transient".equals(name)) { continue; }

			Object value = namespace.getNameSpace().getVariable(name);

			if (value == null) {
				logger.warn(name + " = null");
			} else {
				if (value instanceof sailpoint.object.SailPointObject) {
					sailpoint.object.SailPointObject objValue = (sailpoint.object.SailPointObject)value;
					logger.warn(name + "(" + value.getClass().getSimpleName() + ") = " + objValue.toXml());
				} else {
					logger.warn(name + "(" + value.getClass().getSimpleName() + ") = " + value);
				}
			}
		}
	}

	/**
	 * An indirect reference to {@link BeanshellUtilities#exists(bsh.This, String)}
	 * that will allow the Eclipse plugin to compile rules properly. If the bshThis
	 * passed is not a This object, this method will silently return false.
	 *
	 * @param bshThis The Beanshell this variable
	 * @param variableName The variable name to check
	 * @return True if the variable exists, false otherwise
	 */
	public static boolean exists(Object bshThis, String variableName) {
		if (bshThis instanceof bsh.This) {
			return exists((bsh.This)bshThis, variableName);
		}
		return false;
	}

	/**
	 * Returns true if a Beanshell variable with the given name exists in the
	 * current namespace or one of its parents, returns false otherwise. This
	 * avoids the need for 'void' checks which mess with Beanshell parsing in
	 * the Eclipse plugin.
	 *
	 * @param bshThis The Beanshell 'this'
	 * @param variableName The variable name
	 * @return true if the variable exists in the current namespace
	 */
	private static boolean exists(bsh.This bshThis, String variableName) {
		NameSpace bshNamespace = bshThis.getNameSpace();
		try {
			Object value = bshNamespace.getVariable(variableName);
			return !Primitive.VOID.equals(value);
		} catch(UtilEvalError e) {
			/* Ignore this */
		}
		return false;
	}

	/**
	 * Extracts the BSFManager from the current Beanshell context using reflection
	 * @param bsf The 'bsf' variable passed to all Beanshell scripts
	 * @return The BSFManager
	 * @throws GeneralException if any issues occur retrieving the value using reflection
	 */
	public static BSFManager getBSFManager(BSFFunctions bsf) throws GeneralException {
		try {
			Field mgrField = bsf.getClass().getDeclaredField("mgr");
			mgrField.setAccessible(true);
			try {
				return (BSFManager) mgrField.get(bsf);
			} finally {
				mgrField.setAccessible(false);
			}
		} catch(Exception e) {
			throw new GeneralException(e);
		}
	}

	/**
	 * Gets the value of the Beanshell variable, if it exists and is the
	 * expected object type, otherwise null.
	 *
	 * @param bshThis The beanshell 'this' object
	 * @param variableName The variable name to retrieve
	 * @param expectedType The expected type of the variable
	 * @param <T> The expected object type
	 * @return The value of the given variable, or null
	 */
	public static <T> T get(Object bshThis, String variableName, Class<T> expectedType) {
		if (bshThis instanceof bsh.This) {
			return get((bsh.This)bshThis, variableName, expectedType);
		} else {
			return null;
		}
	}

	/**
	 * Gets the value of the Beanshell variable, if it exists and is the
	 * expected object type, otherwise null.
	 *
	 * @param bshThis The beanshell 'this' object
	 * @param variableName The variable name to retrieve
	 * @param expectedType The expected type of the variable
	 * @param <T> The expected object type
	 * @return The value of the given variable, or null
	 */
	public static <T> T get(bsh.This bshThis, String variableName, Class<T> expectedType) {
		if (bshThis != null && bshThis.getNameSpace() != null) {
			try {
				Object resultMaybe = bshThis.getNameSpace().getVariable(variableName, true);
				if (resultMaybe != null && Functions.isAssignableFrom(expectedType, resultMaybe.getClass())) {
					return (T)resultMaybe;
				}
			} catch(UtilEvalError e) {
				// Ignore this, return null
			}
		}
		return null;
	}

	/**
	 * Imports static methods from the given target class into the namespace
	 * @param bshThis The 'this' reference from Beanshell
	 * @param target The target class to import
	 */
	public static void importStatic(bsh.This bshThis, Class<?> target) {
		NameSpace bshNamespace = bshThis.getNameSpace();
		bshNamespace.importStatic(target);
	}

	/**
	 * Intended to be invoked from a Run Rule task (or code that may be invoked
	 * from one), will check whether the task has been stopped.
	 *
	 * @param bshThis The 'this' object from Beanshell
	 * @return True if the task has been terminated
	 */
	public static boolean runRuleTerminated(bsh.This bshThis)  {
		if (bshThis != null && bshThis.getNameSpace() != null) {
			try {
				Object maybeTaskResult = bshThis.getNameSpace().getVariable("taskResult", true);
				if (maybeTaskResult instanceof TaskResult) {
					TaskResult tr = (TaskResult) maybeTaskResult;
					return (tr.isTerminated() || tr.isTerminateRequested());
				}
			} catch (UtilEvalError e) {
				// Ignore this, at least check Thread interrupt
			}
		}

		return Thread.currentThread().isInterrupted();
	}

	/**
	 * If the given variable does not exist, sets it to null, enabling ordinary
	 * null checks. If the "this" reference is not a Beanshell context, this
	 * method will have no effect.
	 *
	 * @param bshThis The current Beanshell namespace
	 * @param variableName The variable name
	 * @param defaultValue The default value
	 * @throws GeneralException if any failures occur
	 */
	public static void safe(Object bshThis, String variableName, Object defaultValue) throws GeneralException {
		if (bshThis instanceof bsh.This) {
			safe((bsh.This) bshThis, variableName, defaultValue);
		}
	}

	/**
	 * If the given variable does not exist, sets it to the given default value. Otherwise,
	 * the value is retained as-is.
	 *
	 * @param bshThis The current Beanshell namespace
	 * @param variableName The variable name
	 * @param defaultValue The default value
	 * @throws GeneralException if any failures occur
	 */
	private static void safe(bsh.This bshThis, String variableName, Object defaultValue) throws GeneralException {
		if (!exists(bshThis, variableName)) {
			try {
				bshThis.getNameSpace().setVariable(variableName, defaultValue, false);
			} catch(UtilEvalError e) {
				throw new GeneralException(e);
			}
		}
	}

	/**
	 * If the given variable does not exist, sets it to null, enabling ordinary
	 * null checks. If the "this" reference is not a Beanshell context, this
	 * method will have no effect.
	 *
	 * @param bshThis The current Beanshell namespace
	 * @param variableName The variable name
	 * @throws GeneralException if any failures occur
	 */
	public static void safe(Object bshThis, String variableName) throws GeneralException {
		if (bshThis instanceof bsh.This) {
			safe((bsh.This) bshThis, variableName, Primitive.NULL);
		}
	}


	/**
	 * Returns true if the variable exists and is equal to the expected value. If the variable
	 * is null or void, it will match an expected value of null. If the variable is not null or
	 * void, it will be passed to {@link Util#nullSafeEq(Object, Object)}.
	 *
	 * @param bshThis The 'this' object from Beanshell
	 * @param variableName The variable name to extract
	 * @param expectedValue The value we expect the variable to have
	 * @return True if the variable's value is equal to the expected value
	 * @throws GeneralException if reading the variable fails
	 */
	public static boolean safeEquals(Object bshThis, String variableName, Object expectedValue) throws GeneralException {
		if (bshThis instanceof bsh.This) {
			return safeEquals((bsh.This) bshThis, variableName, expectedValue);
		}
		return false;
	}

	/**
	 * Returns true if the variable exists and is equal to the expected value. If the variable
	 * is null or void, it will match an expected value of null. If the variable is not null or
	 * void, it will be passed to {@link Util#nullSafeEq(Object, Object)}.
	 *
	 * @param bshThis The 'this' object from Beanshell
	 * @param variableName The variable name to extract
	 * @param expectedValue The value we expect the variable to have
	 * @return True if the variable's value is equal to the expected value
	 * @throws GeneralException if reading the variable fails
	 */
	private static boolean safeEquals(bsh.This bshThis, String variableName, Object expectedValue) throws GeneralException {
		try {
			Object existingValue = bshThis.getNameSpace().getVariable(variableName, true);
			if (existingValue == null || Primitive.NULL.equals(existingValue) || Primitive.VOID.equals(existingValue)) {
				return (expectedValue == null);
			} else {
				return Util.nullSafeEq(existingValue, expectedValue);
			}
		} catch(UtilEvalError e) {
			throw new GeneralException(e);
		}
	}

	/**
	 * Private utility constructor
	 */
	private BeanshellUtilities() {

	}
}
