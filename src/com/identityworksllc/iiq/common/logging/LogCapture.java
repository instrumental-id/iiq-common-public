package com.identityworksllc.iiq.common.logging;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import sailpoint.VersionConstants;
import sailpoint.tools.GeneralException;

/**
 * A class to start and stop log capture as needed. Start interception at any point
 * by calling {@link LogCapture#startInterception(String...)}, passing one or more logger
 * names. If no logger names are passed, a predefined set of common ones will be used.
 *
 * All messages down to DEBUG level will be captured, even if the logger is set to a
 * higher level. Existing appenders will be copied and adjusted so that they continue
 * to log at the previous level. In other words, you'll get DEBUG messages intercepted
 * but they won't end up in your actual log file.
 *
 * When you're done, make sure to call {@link LogCapture#stopInterception()} or you will
 * have a definite memory leak. You will get back your log messages as a list of strings.
 *
 * For a streaming experience, you can register a LogListener with the other version
 * of {@link #startInterception(LogListener, String...)}. This listener will receive
 * all log messages as instances of LogListener.LogMessage.
 */
public class LogCapture {
	
	/**
	 * The log message listener for this thread; if a listener is defined, no messages will be stored
	 */
	public static ThreadLocal<LogListener> listener = new InheritableThreadLocal<>();
	
	/**
	 * The log messages captured for this thread
	 */
	public static ThreadLocal<List<String>> messages = new InheritableThreadLocal<>();
	
	/**
	 * Adds logger interceptors for the given loggers if they don't already have any
	 * @param loggerNames The logger names to intercept
	 * @throws GeneralException If a reflection exception occurs
	 */
	public static void addLoggers(String... loggerNames) throws GeneralException {
		if (VersionConstants.VERSION.startsWith("7")) {
			try {
				CaptureLogs capturer = (CaptureLogs)Class.forName("com.identityworksllc.iiq.common.logging.CaptureLogs7", true, LogCapture.class.getClassLoader()).newInstance();
				capturer.capture(loggerNames);
			} catch(Exception e) {
				throw new GeneralException(e);
			}
		} else if (VersionConstants.VERSION.startsWith("8")) {
			try {
				CaptureLogs capturer = (CaptureLogs)Class.forName("com.identityworksllc.iiq.common.logging.CaptureLogs8", true, LogCapture.class.getClassLoader()).newInstance();
				capturer.capture(loggerNames);
			} catch(Exception e) {
				throw new GeneralException(e);
			}
		}
	}

	/**
	 * Sets the log listener for the current thread to the given listener object. The existing message queue
	 * will be retrieved, dumped to the listener, then disabled.
	 * @param _listener The listener to assign
	 */
	public static void setListener(LogListener _listener) {
		listener.set(_listener);
		if (messages.get() != null) {
			List<String> existingMessages = messages.get();
			messages.set(null);
			for(String message: existingMessages) {
				LogListener.LogMessage messageObject = new LogListener.LogMessage(new Date(), null, null, message, null);
				_listener.logMessageReceived(messageObject);
			}
		}
	}
	
	/**
	 * Starts capture of the given loggers
	 * @param loggers Specific loggers to capture, if desired
	 * @throws GeneralException if any failures occur
	 */
	private static void startCapture(String... loggers) throws GeneralException {
		CaptureLogs capturer = null;
		try {
			if (VersionConstants.VERSION.startsWith("7")) {
				capturer = (CaptureLogs) Class.forName("com.identityworksllc.iiq.common.logging.CaptureLogs7", true, LogCapture.class.getClassLoader()).newInstance();
			} else if (VersionConstants.VERSION.startsWith("8")) {
				capturer = (CaptureLogs) Class.forName("com.identityworksllc.iiq.common.logging.CaptureLogs8", true, LogCapture.class.getClassLoader()).newInstance();
			}
		} catch(Exception e) {
			throw new GeneralException(e);
		}
		if (capturer != null) {
			if (loggers == null || loggers.length == 0) {
				capturer.captureMost();
			} else {
				capturer.capture(loggers);
			}
		}
	}

	/**
	 * Starts log interception if it hasn't already been started
	 * @throws GeneralException on reflection failure setting up the logging interceptor
	 */
	public static void startInterception(LogListener _listener, String... loggers) throws GeneralException {
		if (listener.get() != null) {
			return;
		}
		Objects.requireNonNull(_listener);
		startCapture(loggers);
		messages.set(null);
		listener.set(_listener);
	}

	/**
	 * Starts log interception if it hasn't already been started
	 * @throws GeneralException on reflection failure setting up the logging interceptor
	 */
	public static void startInterception(String... loggers) throws GeneralException {
		if (messages.get() != null) {
			return;
		}
		startCapture(loggers);
		messages.set(new ArrayList<>());
		listener.set(null);
	}

	/**
	 * Stops interception for this thread, clears the message queue, and returns the list of messages
	 * @return The list of log messages received
	 */
	public static List<String> stopInterception() {
		List<String> messageStrings =  messages.get();
		messages.set(null);
		listener.set(null);
		return messageStrings;
	}

	/**
	 * Private utility constructor
	 */
	private LogCapture() {

	}
}
