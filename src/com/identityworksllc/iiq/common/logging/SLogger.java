package com.identityworksllc.iiq.common.logging;

import bsh.This;
import org.apache.commons.collections.map.ListOrderedMap;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Document;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Bundle;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.AbstractXmlObject;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * A wrapper around the Commons Logging {@link Log} class that supplements its
 * features with some extras available in other logging libraries. Since this
 * class itself implements {@link Log}, Beanshell should not care if you simply
 * overwrite the 'log' variable in your code.
 *
 * Log strings are interpreted as Java {@link MessageFormat} objects and have
 * the various features of that class in your JDK version.
 *
 * Values passed as arguments are only evaluated when the appropriate log
 * level is active. If the log level is not active, the operation becomes a
 * quick no-op. This prevents a lot of isDebugEnabled() type checks.
 *
 * The 'S' stands for Super. Super Logger.
 */
public class SLogger implements org.apache.commons.logging.Log {
	
	/**
	 * Helper class to format an object for logging. The format is only derived
	 * when the {@link #toString()} is called, meaning that if you log one of these
	 * and the log level is not enabled, a slow string conversion will never occur.
	 *
	 * Null values are transformed into the special string '(null)'.
	 *
	 * Formatted values are cached after the first format operation, even if the
	 * underlying object is modified.
	 *
	 * The following types are handled by the Formatter:
	 *
	 * - null
	 * - Strings
	 * - Arrays of Objects
	 * - Arrays of StackTraceElements
	 * - Collections of Objects
	 * - Maps
	 * - Dates and Calendars
	 * - XML {@link Document}s
	 * - Various SailPointObjects
	 *
	 * Nested objects are also passed through a Formatter.
	 */
	public static class Formatter implements Supplier<String> {

		/**
		 * The cached formatted value
		 */
		private String formattedValue;
		/**
		 * The object to format.
		 */
		private final Object item;

		/**
		 * Creates a new formatter.
		 *
		 * @param Item The item to format.
		 */
		public Formatter(Object Item) {
			this.item = Item;
			this.formattedValue = null;
		}

		@SuppressWarnings("unchecked")
		private Map<String, Object> createLogMap(SailPointObject value) {
			Map<String, Object> map = new ListOrderedMap();
			map.put("class", value.getClass().getName());
			map.put("id", value.getId());
			map.put("name", value.getName());
			return map;
		}

		/**
		 * Formats an object.
		 *
		 * @param valueToFormat The object to format.
		 * @return The formatted version of that object.
		 */
		private String format(Object valueToFormat) {
			return format(valueToFormat, false);
		}

		/**
		 * Formats an object according to multiple type-specific format rules.
		 *
		 * @param valueToFormat The object to format.
		 * @return The formatted version of that object.
		 */
		private String format(Object valueToFormat, boolean indent) {
			StringBuilder value = new StringBuilder();

			if (valueToFormat == null) {
				value.append("(null)");
			} else if (valueToFormat instanceof String) {
				value.append(valueToFormat);
			} else if (valueToFormat instanceof bsh.This) {
				String namespaceName = "???";
				if (((This) valueToFormat).getNameSpace() != null) {
					namespaceName = ((This) valueToFormat).getNameSpace().getName();
				}
				value.append("bsh.This[namespace=").append(namespaceName).append("]");
			} else if (valueToFormat instanceof StackTraceElement[]) {
				for (StackTraceElement element : (StackTraceElement[]) valueToFormat) {
					value.append("\n  at ");
					value.append(element);
				}
			} else if (valueToFormat instanceof Throwable) {
				Throwable t = (Throwable)valueToFormat;
				try (StringWriter target = new StringWriter()) {
					try (PrintWriter printWriter = new PrintWriter(target)) {
						t.printStackTrace(printWriter);
					}
					value.append(target);
				} catch(IOException e) {
					return "Exception printing object of type Throwable: " + e;
				}
			} else if (valueToFormat.getClass().isArray()) {
				value.append("[\n");
				boolean first = true;
				int length = Array.getLength(valueToFormat);
				for (int i = 0; i < length; i++) {
					if (!first) {
						value.append(",\n");
					}
					value.append("  ");
					value.append(format(Array.get(valueToFormat, i), true));
					first = false;
				}
				value.append("\n]");
			} else if (valueToFormat instanceof Collection) {
				value.append("[\n");
				boolean first = true;
				for (Object arg : (Collection<?>) valueToFormat) {
					if (!first) {
						value.append(",\n");
					}
					value.append("  ").append(format(arg, true));
					first = false;
				}
				value.append("\n]");
			} else if (valueToFormat instanceof Map) {
				value.append("{\n");
				boolean first = true;
				for (Map.Entry<?, ?> entry : new TreeMap<Object, Object>((Map<?, ?>) valueToFormat).entrySet()) {
					if (!first) {
						value.append(",\n");
					}
					value.append("  ");
					value.append(format(entry.getKey()));
					value.append("=");
					value.append(format(entry.getValue(), true));
					first = false;
				}
				value.append("\n}");
			} else if (valueToFormat instanceof Date) {
				DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
				value.append(format.format((Date) valueToFormat));
			} else if (valueToFormat instanceof Calendar) {
				value.append(format(((Calendar) valueToFormat).getTime()));
			} else if (valueToFormat instanceof Document) {
				try {
					Transformer transformer = TransformerFactory.newInstance().newTransformer();
					transformer.setOutputProperty(OutputKeys.INDENT, "yes");
					transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
					try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
						transformer.transform(new DOMSource((Document) valueToFormat), new StreamResult(output));
						value.append(output);
					}
				} catch (Exception e) {
					return "Exception transforming object of type Document " + e;
				}
			} else if (valueToFormat instanceof Identity) {
				value.append("Identity").append(format(toLogMap((Identity) valueToFormat)));
			} else if (valueToFormat instanceof Bundle) {
				value.append("Bundle").append(format(toLogMap((Bundle)valueToFormat)));
			} else if (valueToFormat instanceof ManagedAttribute) {
				value.append("ManagedAttribute").append(format(toLogMap((ManagedAttribute) valueToFormat)));
			} else if (valueToFormat instanceof Link) {
				value.append("Link").append(format(toLogMap((Link) valueToFormat)));
			} else if (valueToFormat instanceof Application) {
				value.append("Application").append(format(toLogMap((Application) valueToFormat)));
			} else if (valueToFormat instanceof SailPointContext) {
				value.append("SailPointContext[").append(valueToFormat.hashCode()).append(", username = ").append(((SailPointContext) valueToFormat).getUserName()).append("]");
			} else if (valueToFormat instanceof ProvisioningPlan) {
				try {
					value.append(ProvisioningPlan.getLoggingPlan((ProvisioningPlan) valueToFormat).toXml());
				} catch (GeneralException e) {
					return "Exception transforming object of type " + valueToFormat.getClass().getName() + " to XML: " + e;
				}
			} else if (valueToFormat instanceof Filter) {
				value.append("Filter[").append(((Filter) valueToFormat).getExpression(true)).append("]");
			} else if (valueToFormat instanceof AbstractXmlObject) {
				try {
					value.append(((AbstractXmlObject)valueToFormat).toXml());
				} catch (GeneralException e) {
					return "Exception transforming object of type " + valueToFormat.getClass().getName() + " to XML: " + e;
				}
			} else {
				value.append(valueToFormat);
			}

			String result = value.toString();
			if (indent) {
				result = result.replace("\n", "\n  ").trim();
			}

			return result;
		}

		/**
		 * Returns the formatted string of the item when invoked via the {@link Supplier} interface
		 * @return The formatted string
		 * @see Supplier#get()
		 */
		@Override
		public String get() {
			return toString();
		}

		/**
		 * Converts the Identity to a Map for logging purposes
		 * @param value The Identity convert
		 * @return A Map containing some basic identity details
		 */
		private Map<String, Object> toLogMap(Identity value) {
			Map<String, Object> map = createLogMap(value);
			map.put("type", value.getType());
			map.put("displayName", value.getDisplayName());
			map.put("disabled", value.isDisabled() || value.isInactive());
			map.put("attributes", format(value.getAttributes()));
			return map;
		}

		/**
		 * Converts the Link to a Map for logging purposes
		 * @param value The Link to convert
		 * @return A Map containing some basic Link details
		 */
		private Map<String, Object> toLogMap(Link value) {
			Map<String, Object> map = createLogMap(value);
			map.put("application", value.getApplicationName());
			map.put("nativeIdentity", value.getNativeIdentity());
			map.put("displayName", value.getDisplayName());
			map.put("disabled", value.isDisabled());
			return map;
		}

		/**
		 * Converts the Application to a Map for logging purposes
		 * @param value The Application to convert
		 * @return A Map containing some basic Application details
		 */
		private Map<String, Object> toLogMap(Application value) {
			Map<String, Object> map = createLogMap(value);
			map.put("authoritative", value.isAuthoritative());
			map.put("connector", value.getConnector());
			if (value.isInMaintenance()) {
				map.put("maintenance", true);
			}
			return map;
		}

		/**
		 * Converts the Bundle / Role to a Map for logging purposes
		 * @param value The Bundle to convert
		 * @return A Map containing some basic Bundle details
		 */
		private Map<String, Object> toLogMap(Bundle value) {
			Map<String, Object> map = createLogMap(value);
			map.put("type", value.getType());
			map.put("displayName", value.getDisplayName());
			return map;
		}

		/**
		 * Converts the ManagedAttribute / Entitlement object to a Map for logging purposes
		 * @param value The MA to convert
		 * @return A Map containing some basic MA details
		 */
		private Map<String, Object> toLogMap(ManagedAttribute value) {
			Map<String, Object> map = createLogMap(value);
			map.put("application", value.getApplication().getName());
			map.put("attribute", value.getAttribute());
			map.put("value", value.getValue());
			map.put("displayName", value.getDisplayName());
			return map;
		}

		/**
		 * If the formatted value exists, the cached version will be returned.
		 * Otherwise, the format string will be calculated at this time, cached,
		 * and then returned.
		 *
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			if (this.formattedValue == null) {
				this.formattedValue = format(item);
			}
			return this.formattedValue;
		}
	}

	/**
	 * An enumeration of log levels to replace the one in log4j
	 */
	public enum Level {
		TRACE,
		DEBUG,
		INFO,
		WARN,
		ERROR,
		FATAL
	}

	/**
	 * Wraps the arguments for future formatting. The format string is not resolved
	 * at this time.
	 *
	 * NOTE: In newer versions of logging APIs, this would be accomplished
	 * by passing a {@link Supplier} to the API. However, in Commons Logging 1.x,
	 * this is not available. It is also not available in Beanshell, as it requires
	 * lambda syntax. If that ever becomes available, this class will become
	 * obsolete.
	 *
	 * @param args The arguments for any place-holders in the message template.
	 * @return The formatted arguments.
	 */
	public static SLogger.Formatter[] format(Object[] args) {
		if (args == null) {
			return null;
		}
		Formatter[] argsCopy = new Formatter[args.length];
		for (int i = 0; i < args.length; i++) {
			argsCopy[i] = new Formatter(args[i]);
		}
		return argsCopy;
	}

	/**
	 * Renders the MessageTemplate using the given arguments
	 * @param messageTemplate The message template
	 * @param args The arguments
	 * @return The resolved message template
	 */
	public static String renderMessage(String messageTemplate, Object[] args) {
		if (args != null && args.length > 0) {
			MessageFormat template = new MessageFormat(messageTemplate);
			return template.format(args);
		} else {
			return messageTemplate;
		}
	}

	/**
	 * The underlying logger to use.
	 */
	private final Log logger;
	/**
	 * The underlying output stream to use.
	 */
	private final PrintStream out;

	/**
	 * Creates a new logger.
	 *
	 * @param Owner The class to log messages for.
	 */
	public SLogger(Class<?> Owner) {
		logger = LogFactory.getLog(Owner);
		out = null;
	}

	/**
	 * Wraps the given log4j logger with this logger
	 *
	 * @param WrapLog The logger to wrap
	 */
	public SLogger(Log WrapLog) {
		logger = WrapLog;
		out = null;
	}

	/**
	 * Creates a new logger.
	 *
	 * @param Out The output stream to
	 */
	public SLogger(PrintStream Out) {
		logger = null;
		out = Out;
	}
	
	@Override
	public void debug(Object arg0) {
		debug("{0}", arg0);
	}
	
	@Override
	public void debug(Object arg0, Throwable arg1) {
		debug("{0} {1}", arg0, arg1);
	}

	/**
	 * Logs an debugging message.
	 *
	 * @param MessageTemplate A message template, which can either be a plain string or contain place-holders like {0} and {1}.
	 * @param Args The arguments for any place-holders in the message template.
	 */
	public void debug(String MessageTemplate, Object... Args) {
		log(Level.DEBUG, MessageTemplate, format(Args));
	}

	/**
	 * @see Log#error(Object)
	 */
	@Override
	public void error(Object arg0) {
		error("{0}", arg0);
	}

	/**
	 * @see Log#error(Object, Throwable)
	 */
	@Override
	public void error(Object arg0, Throwable arg1) {
		error("{0}", arg0);
		handleException(arg1);
	}

	/**
	 * Logs an error message.
	 *
	 * @param MessageTemplate A message template, which can either be a plain string or contain place-holders like {0} and {1}.
	 * @param Args The arguments for any place-holders in the message template.
	 */
	public void error(String MessageTemplate, Object... Args) {
		log(Level.ERROR, MessageTemplate, format(Args));
	}

	@Override
	public void fatal(Object arg0) {
		fatal("{0}", arg0);
	}

	@Override
	public void fatal(Object arg0, Throwable arg1) {
		fatal("{0}", arg0);
		handleException(arg1);
	}

	/**
	 * Logs a fatal error message.
	 *
	 * @param MessageTemplate A message template, which can either be a plain string or contain place-holders like {0} and {1}.
	 * @param Args The arguments for any place-holders in the message template.
	 */
	public void fatal(String MessageTemplate, Object... Args) {
		log(Level.FATAL, MessageTemplate, format(Args));
	}

	/**
	 * Gets the internal Log object wrapped by this class
	 * @return The internal log object
	 */
	/*package*/ Log getLogger() {
		return logger;
	}

	/**
	 * Handles an exception.
	 *
	 * @param Error The exception to handle.
	 */
	public synchronized void handleException(Throwable Error) {
		save(Error);
		if (logger != null) {
			logger.error(Error.toString(), Error);
		} else if (out != null) {
			Error.printStackTrace(out);
		}
	}
	
	@Override
	public void info(Object arg0) {
		info("{0}", arg0);
	}

	@Override
	public void info(Object arg0, Throwable arg1) {
		info("{0} {1}", arg0, arg1);
	}

	/**
	 * Logs an informational message.
	 *
	 * @param MessageTemplate A message template, which can either be a plain string or contain place-holders like {0} and {1}.
	 * @param Args The arguments for any place-holders in the message template.
	 */
	public void info(String MessageTemplate, Object... Args) {
		log(Level.INFO, MessageTemplate, format(Args));
	}

	/**
	 * @see Log#isDebugEnabled()
	 */
	@Override
	public boolean isDebugEnabled() {
		if (logger != null) {
			return logger.isDebugEnabled();
		} else {
			return true;
		}
	}

	/**
	 * Returns true if the logger is enabled for the given level. Unfortunately, Commons Logging doesn't have a friendly isEnabledFor(Level) type API, since some of its downstream loggers may not either.
	 *
	 * @param log The logger to check
	 * @param logLevel The level to check
	 * @return true if the logger is enabled
	 */
	private boolean isEnabledFor(Log log, Level logLevel) {
		switch(logLevel) {
		case TRACE:
			return log.isTraceEnabled();
		case DEBUG:
			return log.isDebugEnabled();
		case INFO:
			return log.isInfoEnabled();
		case WARN:
			return log.isWarnEnabled();
		case ERROR:
			return log.isErrorEnabled();
		case FATAL:
			return log.isFatalEnabled();
		}
		return false;
	}

	/**
	 * @see Log#isErrorEnabled()
	 */
	@Override
	public boolean isErrorEnabled() {
		if (logger != null) {
			return logger.isErrorEnabled();
		} else {
			return true;
		}
	}

	/**
	 * @see Log#isFatalEnabled()
	 */
	@Override
	public boolean isFatalEnabled() {
		if (logger != null) {
			return logger.isFatalEnabled();
		} else {
			return true;
		}
	}

	/**
	 * @see Log#isInfoEnabled()
	 */
	@Override
	public boolean isInfoEnabled() {
		if (logger != null) {
			return logger.isInfoEnabled();
		} else {
			return true;
		}
	}

	/**
	 * @see Log#isTraceEnabled()
	 */
	@Override
	public boolean isTraceEnabled() {
		if (logger != null) {
			return logger.isTraceEnabled();
		} else {
			return true;
		}
	}

	/**
	 * @see Log#isWarnEnabled()
	 */
	@Override
	public boolean isWarnEnabled() {
		if (logger != null) {
			return logger.isWarnEnabled();
		} else {
			return true;
		}
	}

	/**
	 * Logs the message at the appropriate level according to the Commons Logging API
	 * @param logLevel The log level to log at
	 * @param message The message to log
	 */
	public void log(Level logLevel, String message) {
		switch(logLevel) {
		case TRACE:
			logger.trace(message);
			break;
		case DEBUG:
			logger.debug(message);
			break;
		case INFO:
			logger.info(message);
			break;
		case WARN:
			logger.warn(message);
			break;
		case ERROR:
			logger.error(message);
			break;
		case FATAL:
			logger.fatal(message);
			break;
		}
	}

	/**
	 * Logs a message.
	 *
	 * @param logLevel The level to log the message at.
	 * @param messageTemplate A message template, which can either be a plain string or contain place-holders like {0} and {1}.
	 * @param args The arguments for any place-holders in the message template.
	 */
	private void log(Level logLevel, String messageTemplate, Object... args) {
		save(logLevel, messageTemplate, args);
		if (logger != null) {
			if (isEnabledFor(logger, logLevel)) {
				String message = renderMessage(messageTemplate, args);
				log(logLevel, message);
			}
		} else if (out != null) {
			String message = renderMessage(messageTemplate, args);
			out.println(message);
		}
	}

	/**
	 * Hook to allow log messages to be intercepted and saved.
	 *
	 * @param LogLevel The level to log the message at.
	 * @param MessageTemplate A message template, which can either be a plain string or contain place-holders like {0} and {1}.
	 * @param Args The arguments for any place-holders in the message template.
	 */
	protected void save(@SuppressWarnings("unused") Level LogLevel, @SuppressWarnings("unused") String MessageTemplate, @SuppressWarnings("unused") Object[] Args) {
		/* Does Nothing */
	}

	/**
	 * Hook to allow log messages to be intercepted and saved. In this version of this
	 * class, this is a no-op.
	 *
	 * @param Error The exception to handle.
	 */
	protected void save(@SuppressWarnings("unused") Throwable Error) {
		/* Does Nothing */
	}

	@Override
	public void trace(Object arg0) {
		trace("{0}", arg0);
	}

	@Override
	public void trace(Object arg0, Throwable arg1) {
		trace("{0} {1}", arg0, arg1);
	}

	/**
	 * Logs a trace message.
	 *
	 * @param MessageTemplate A message template, which can either be a plain string or contain place-holders like {0} and {1}.
	 * @param Args The arguments for any place-holders in the message template.
	 */
	public void trace(String MessageTemplate, Object... Args) {
		log(Level.TRACE, MessageTemplate, format(Args));
	}

	/**
	 * @see Log#warn(Object)
	 */
	@Override
	public void warn(Object arg0) {
		warn("{0}", arg0);
	}

	/**
	 * @see Log#warn(Object, Throwable)
	 */
	@Override
	public void warn(Object arg0, Throwable arg1) {
		warn("{0} {1}", arg0, arg1);
	}

	/**
	 * Logs a warning message.
	 *
	 * @param MessageTemplate A message template, which can either be a plain string or contain place-holders like {0} and {1}.
	 * @param Args The arguments for any place-holders in the message template.
	 */
	public void warn(String MessageTemplate, Object... Args) {
		log(Level.WARN, MessageTemplate, format(Args));
	}
}