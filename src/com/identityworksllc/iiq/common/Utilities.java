package com.identityworksllc.iiq.common;

import com.identityworksllc.iiq.common.logging.SLogger;
import com.identityworksllc.iiq.common.query.ContextConnectionWrapper;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import sailpoint.api.MessageAccumulator;
import sailpoint.api.ObjectAlreadyLockedException;
import sailpoint.api.ObjectUtil;
import sailpoint.api.PersistenceManager;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.*;
import sailpoint.rest.BaseResource;
import sailpoint.server.AbstractSailPointContext;
import sailpoint.server.Environment;
import sailpoint.server.SPKeyStore;
import sailpoint.server.SailPointConsole;
import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.Console;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.RFC4180LineParser;
import sailpoint.tools.Reflection;
import sailpoint.tools.Util;
import sailpoint.tools.VelocityUtil;
import sailpoint.tools.xml.ConfigurationException;
import sailpoint.tools.xml.XMLObjectFactory;
import sailpoint.web.BaseBean;
import sailpoint.web.UserContext;
import sailpoint.web.util.WebUtil;

import javax.faces.context.FacesContext;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Static utility methods that are useful throughout any IIQ codebase, supplementing
 * SailPoint's {@link Util} in many places.
 */
@SuppressWarnings("unused")
public class Utilities {

	/**
	 * Used as an indicator that the quick property lookup produced nothing.
	 * All instances of this class are always identical via equals().
	 */
	public static final class PropertyLookupNone {
		private PropertyLookupNone() {
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			return o instanceof PropertyLookupNone;
		}

		@Override
		public int hashCode() {
			return Objects.hash("");
		}
	}

	public static final String IDW_WORKER_POOL = "idw.worker.pool";

	/**
	 * The key used to store the user's most recent locale on their UIPrefs,
	 * captured by {@link #tryCaptureLocationInfo(SailPointContext, Identity)}
	 */
	public static final String MOST_RECENT_LOCALE = "mostRecentLocale";

	/**
	 * The key used to store the user's most recent timezone on their UIPrefs,
	 * captured by {@link #tryCaptureLocationInfo(SailPointContext, Identity)}
	 */
	public static final String MOST_RECENT_TIMEZONE = "mostRecentTimezone";

	/**
	 * A magic constant for use with the {@link Utilities#getQuickProperty(Object, String)} method.
	 * If the property is not an available 'quick property', this object will be returned.
	 */
	public static final Object NONE = new PropertyLookupNone();

	/**
	 * Indicates whether velocity has been initialized in this Utilities class
	 */
	private static final AtomicBoolean VELOCITY_INITIALIZED = new AtomicBoolean();

	/**
	 * The internal logger
	 */
	private static final Log logger = LogFactory.getLog(Utilities.class);

	/**
	 * Adds the given value to a {@link Collection} at the given key in the map.
	 * <p>
	 * If the map does not have a {@link Collection} at the given key, a new {@link ArrayList} is added, then the value is added to that.
	 * <p>
	 * This method is null safe. If the map or key is null, this method has no effect.
	 *
	 * @param map   The map to modify
	 * @param key   The map key that points to the list to add the value to
	 * @param value The value to add to the list
	 * @param <S>   The key type
	 * @param <T>   The list element type
	 */
	@SuppressWarnings({"unchecked"})
	public static <S, T extends Collection<S>> void addMapped(Map<String, T> map, String key, S value) {
		if (map != null && key != null) {
			if (!map.containsKey(key)) {
				map.put(key, (T) new ArrayList<>());
			}
			map.get(key).add(value);
		}
	}

	/**
	 * Adds a message banner to the current browser session which will show up at the top of each page
	 *
	 * @param context The IIQ context
	 * @param message The Message to add
	 * @throws GeneralException if anything goes wrong
	 */
	public static void addSessionMessage(SailPointContext context, Message message) throws GeneralException {
		FacesContext fc = FacesContext.getCurrentInstance();
		if (fc != null) {
			try {
				BaseBean bean = (BaseBean) fc.getApplication().evaluateExpressionGet(fc, "#{base}", Object.class);
				bean.addMessageToSession(message);
			} catch (Exception e) {
				throw new GeneralException(e);
			}
		}
	}

	/**
	 * Adds a new MatchTerm as an 'and' to an existing MatchExpression, transforming an
	 * existing 'or' into a sub-expression if needed.
	 *
	 * @param input   The input MatchExpression
	 * @param newTerm The new term to 'and' with the existing expressions
	 * @return The resulting match term
	 */
	public static IdentitySelector.MatchExpression andMatchTerm(IdentitySelector.MatchExpression input, IdentitySelector.MatchTerm newTerm) {
		if (input.isAnd()) {
			input.addTerm(newTerm);
		} else {
			IdentitySelector.MatchTerm bigOr = new IdentitySelector.MatchTerm();
			for (IdentitySelector.MatchTerm existing : Util.safeIterable(input.getTerms())) {
				bigOr.addChild(existing);
			}
			bigOr.setContainer(true);
			bigOr.setAnd(false);

			List<IdentitySelector.MatchTerm> newChildren = new ArrayList<>();
			newChildren.add(bigOr);
			newChildren.add(newTerm);

			input.setAnd(true);
			input.setTerms(newChildren);
		}
		return input;
	}

	/**
	 * Boxes a primitive type into its java Object type
	 *
	 * @param prim The primitive type class
	 * @return The boxed type
	 */
	/*package*/
	static Class<?> box(Class<?> prim) {
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
	 * Returns true if the collection contains the given value ignoring case
	 *
	 * @param collection The collection to check
	 * @param value      The value to check for
	 */
	public static boolean caseInsensitiveContains(Collection<? extends Object> collection, Object value) {
		if (collection == null || collection.isEmpty()) {
			return false;
		}
		// Most of the Set classes have efficient implementations
		// of contains which we should check first for case-sensitive matches.
		if (collection instanceof Set && collection.contains(value)) {
			return true;
		}
		if (value instanceof String) {
			String s2 = (String) value;
			for (Object o : collection) {
				if (o instanceof String) {
					String s1 = (String) o;
					if (s1.equalsIgnoreCase(s2)) {
						return true;
					}
				}
			}
			return false;
		}
		return collection.contains(value);
	}

	/**
	 * Gets a global singleton value from CustomGlobal. If it doesn't exist, uses
	 * the supplied factory (in a synchronized thread) to create it.
	 * <p>
	 * NOTE: This should NOT be an instance of a class defined in a plugin. If the
	 * plugin is redeployed and its classloader refreshes, it will cause the return
	 * value from this method to NOT match the "new" class in the new classloader,
	 * causing ClassCastExceptions.
	 *
	 * @param key     The key
	 * @param factory The factory to use if the stored value is null
	 * @param <T>     the expected output type
	 * @return the object from the global cache
	 */
	@SuppressWarnings("unchecked")
	public static <T> T computeGlobalSingleton(String key, Supplier<T> factory) {
		T output = (T) CustomGlobal.get(key);
		if (output == null && factory != null) {
			synchronized (CustomGlobal.class) {
				output = (T) CustomGlobal.get(key);
				if (output == null) {
					output = factory.get();
					if (output != null) {
						CustomGlobal.put(key, output);
					}
				}
			}
		}
		return output;
	}

	/**
	 * Invokes a command via the IIQ console which will run as though it was
	 * typed at the command prompt
	 *
	 * @param command The command to run
	 * @return the results of the command
	 * @throws Exception if a failure occurs during run
	 */
	public static String consoleInvoke(String command) throws Exception {
		final Console console = new SailPointConsole();
		final SailPointContext context = SailPointFactory.getCurrentContext();
		try {
			SailPointFactory.setContext(null);
			try (StringWriter stringWriter = new StringWriter()) {
				try (PrintWriter writer = new PrintWriter(stringWriter)) {
					Method doCommand = console.getClass().getSuperclass().getDeclaredMethod("doCommand", String.class, PrintWriter.class);
					doCommand.setAccessible(true);
					doCommand.invoke(console, command, writer);
				}
				return stringWriter.getBuffer().toString();
			}
		} finally {
			SailPointFactory.setContext(context);
		}
	}

	/**
	 * Returns true if the Throwable message (or any of its causes) contain the given message
	 *
	 * @param t     The throwable to check
	 * @param cause The message to check for
	 * @return True if the message appears anywhere
	 */
	public static boolean containsMessage(Throwable t, String cause) {
		if (t == null || t.toString() == null || cause == null || cause.isEmpty()) {
			return false;
		}
		if (t.toString().contains(cause)) {
			return true;
		}
		if (t.getCause() != null) {
			return containsMessage(t.getCause(), cause);
		}
		return false;
	}

	/**
	 * Returns true if the given match expression references the given property anywhere. This is
	 * mainly intended for one-off operations to find roles with particular selectors.
	 *
	 * @param input    The filter input
	 * @param property The property to check for
	 * @return True if the MatchExpression references the given property anywhere in its tree
	 */
	public static boolean containsProperty(IdentitySelector.MatchExpression input, String property) {
		for (IdentitySelector.MatchTerm term : Util.safeIterable(input.getTerms())) {
			boolean contains = containsProperty(term, property);
			if (contains) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns true if the given match term references the given property anywhere. This is
	 * mainly intended for one-off operations to find roles with particular selectors.
	 *
	 * @param term     The MatchTerm to check
	 * @param property The property to check for
	 * @return True if the MatchTerm references the given property anywhere in its tree
	 */
	public static boolean containsProperty(IdentitySelector.MatchTerm term, String property) {
		if (term.isContainer()) {
			for (IdentitySelector.MatchTerm child : Util.safeIterable(term.getChildren())) {
				boolean contains = containsProperty(child, property);
				if (contains) {
					return true;
				}
			}
		} else {
			return Util.nullSafeCaseInsensitiveEq(term.getName(), property);
		}
		return false;
	}

	/**
	 * Returns true if the given filter references the given property anywhere. This is
	 * mainly intended for one-off operations to find roles with particular selectors.
	 * <p>
	 * If either the filter or the property is null, returns false.
	 *
	 * @param input    The filter input
	 * @param property The property to check for
	 * @return True if the Filter references the given property anywhere in its tree
	 */
	public static boolean containsProperty(Filter input, String property) {
		if (Util.isNullOrEmpty(property)) {
			return false;
		}
		if (input instanceof Filter.CompositeFilter) {
			Filter.CompositeFilter compositeFilter = (Filter.CompositeFilter) input;
			for (Filter child : Util.safeIterable(compositeFilter.getChildren())) {
				boolean contains = containsProperty(child, property);
				if (contains) {
					return true;
				}
			}
		} else if (input instanceof Filter.LeafFilter) {
			Filter.LeafFilter leafFilter = (Filter.LeafFilter) input;
			return Util.nullSafeCaseInsensitiveEq(leafFilter.getProperty(), property) || Util.nullSafeCaseInsensitiveEq(leafFilter.getSubqueryProperty(), property);
		}
		return false;
	}

	/**
	 * Detaches the given object as much as possible from the database context by converting it to XML and back again.
	 * <p>
	 * Converting to XML requires resolving all Hibernate lazy-loaded references.
	 *
	 * @param context The context to use to parse the XML
	 * @param o       The object to detach
	 * @return A reference to the object detached from any Hibernate session
	 * @throws GeneralException if a parsing failure occurs
	 */
	public static <T extends SailPointObject> T detach(SailPointContext context, T o) throws GeneralException {
		T retVal = (T) SailPointObject.parseXml(context, o.toXml());
		context.decache(o);
		return retVal;
	}

	/**
	 * Throws an exception if the string is null or empty
	 *
	 * @param values The strings to test
	 */
	public static void ensureAllNotNullOrEmpty(String... values) {
		if (values == null || Util.isAnyNullOrEmpty(values)) {
			throw new NullPointerException();
		}
	}

	/**
	 * Throws an exception if the string is null or empty
	 *
	 * @param value The string to test
	 */
	public static void ensureNotNullOrEmpty(String value) {
		if (Util.isNullOrEmpty(value)) {
			throw new NullPointerException();
		}
	}

	/**
	 * Uses reflection to evict the RuleRunner pool cache
	 *
	 * @throws Exception if anything goes wrong
	 */
	public static void evictRuleRunnerPool() throws Exception {
		RuleRunner ruleRunner = Environment.getEnvironment().getRuleRunner();

		java.lang.reflect.Field _poolField = ruleRunner.getClass().getDeclaredField("_pool");
		_poolField.setAccessible(true);

		Object poolObject = _poolField.get(ruleRunner);

		_poolField.setAccessible(false);

		java.lang.reflect.Method clearMethod = poolObject.getClass().getMethod("clear");
		clearMethod.invoke(poolObject);
	}

	/**
	 * Extracts the value of the given property from each item in the list and returns
	 * a new list containing those property values.
	 * <p>
	 * If the input list is null or empty, an empty list will be returned.
	 * <p>
	 * This is roughly identical to
	 * <p>
	 * input.stream().map(item -> (T)Utilities.getProperty(item, property)).collect(Collectors.toList())
	 *
	 * @param input        The input list
	 * @param property     The property to extract
	 * @param expectedType The expected type of the output objects
	 * @param <S>          The input type
	 * @param <T>          The output type
	 * @return A list of the extracted values
	 * @throws GeneralException if extraction goes wrong
	 */
	@SuppressWarnings("unchecked")
	public static <S, T> List<T> extractProperty(List<S> input, String property, Class<T> expectedType) throws GeneralException {
		List<T> output = new ArrayList<>();
		for (S inputObject : Util.safeIterable(input)) {
			output.add((T) Utilities.getProperty(inputObject, property));
		}
		return output;
	}

	/**
	 * Transfors an input Filter into a MatchExpression
	 *
	 * @param input The Filter
	 * @return The MatchExpression
	 */
	public static IdentitySelector.MatchExpression filterToMatchExpression(Filter input) {
		IdentitySelector.MatchExpression expression = new IdentitySelector.MatchExpression();
		expression.addTerm(filterToMatchTerm(input));
		return expression;
	}

	/**
	 * Transfors an input Filter into a MatchTerm
	 *
	 * @param input The Filter
	 * @return The MatchTerm
	 */
	public static IdentitySelector.MatchTerm filterToMatchTerm(Filter input) {
		IdentitySelector.MatchTerm matchTerm = null;

		if (input instanceof Filter.CompositeFilter) {
			matchTerm = new IdentitySelector.MatchTerm();
			matchTerm.setContainer(true);

			Filter.CompositeFilter compositeFilter = (Filter.CompositeFilter) input;
			if (compositeFilter.getOperation().equals(Filter.BooleanOperation.AND)) {
				matchTerm.setAnd(true);
			} else if (compositeFilter.getOperation().equals(Filter.BooleanOperation.NOT)) {
				throw new UnsupportedOperationException("MatchExpressions do not support NOT filters");
			}

			for (Filter child : Util.safeIterable(compositeFilter.getChildren())) {
				matchTerm.addChild(filterToMatchTerm(child));
			}
		} else if (input instanceof Filter.LeafFilter) {
			matchTerm = new IdentitySelector.MatchTerm();

			Filter.LeafFilter leafFilter = (Filter.LeafFilter) input;
			if (leafFilter.getOperation().equals(Filter.LogicalOperation.IN)) {
				matchTerm.setContainer(true);
				List<String> values = Util.otol(leafFilter.getValue());
				if (values == null) {
					throw new IllegalArgumentException("For IN filters, only List<String> values are accepted");
				}
				for (String value : values) {
					IdentitySelector.MatchTerm child = new IdentitySelector.MatchTerm();
					child.setName(leafFilter.getProperty());
					child.setValue(value);
					child.setType(IdentitySelector.MatchTerm.Type.Entitlement);
					matchTerm.addChild(child);
				}
			} else if (leafFilter.getOperation().equals(Filter.LogicalOperation.EQ)) {
				matchTerm.setName(leafFilter.getProperty());
				matchTerm.setValue(Util.otoa(leafFilter.getValue()));
				matchTerm.setType(IdentitySelector.MatchTerm.Type.Entitlement);
			} else if (leafFilter.getOperation().equals(Filter.LogicalOperation.ISNULL)) {
				matchTerm.setName(leafFilter.getProperty());
				matchTerm.setType(IdentitySelector.MatchTerm.Type.Entitlement);
			} else {
				throw new UnsupportedOperationException("MatchExpressions do not support " + leafFilter.getOperation() + " operations");
			}

		}

		return matchTerm;
	}

	/**
	 * Returns the first item in the list that is not null or empty. If all items are null
	 * or empty, or the input is itself a null or empty array, an empty string will be returned.
	 * This method will never return null.
	 *
	 * @param inputs The input strings
	 * @return The first not null or empty item, or an empty string if none is found
	 */
	public static String firstNotNullOrEmpty(String... inputs) {
		if (inputs == null || inputs.length == 0) {
			return "";
		}
		for (String in : inputs) {
			if (Util.isNotNullOrEmpty(in)) {
				return in;
			}
		}
		return "";
	}

	/**
	 * Formats the input message template using Java's MessageFormat class
	 * and the SLogger.Formatter class.
	 * <p>
	 * If no parameters are provided, the message template is returned as-is.
	 *
	 * @param messageTemplate The message template into which parameters should be injected
	 * @param params          The parameters to be injected
	 * @return The resulting string
	 */
	public static String format(String messageTemplate, Object... params) {
		if (params == null || params.length == 0) {
			return messageTemplate;
		}

		Object[] formattedParams = new Object[params.length];
		for (int p = 0; p < params.length; p++) {
			formattedParams[p] = new SLogger.Formatter(params[p]);
		}
		return MessageFormat.format(messageTemplate, formattedParams);
	}

	/**
	 * Retrieves a key from the given Map in a 'fuzzy' way. Keys will be matched
	 * ignoring case and whitespace.
	 * <p>
	 * For example, given the actual key "toolboxConfig", the following inputs
	 * would also match:
	 * <p>
	 * "toolbox config"
	 * "Toolbox Config"
	 * "ToolboxConfig"
	 * <p>
	 * The first matching key will be returned, so it is up to the caller to ensure
	 * that the input does not match more than one actual key in the Map. For some
	 * Map types, "first matching key" may be nondeterministic if more than one key
	 * matches.
	 * <p>
	 * If either the provided key or the map is null, this method will return null.
	 *
	 * @param map      The map from which to query the value
	 * @param fuzzyKey The fuzzy key
	 * @param <T>      The return type, for convenience
	 * @return The value from the map
	 */
	@SuppressWarnings("unchecked")
	public static <T> T fuzzyGet(Map<String, Object> map, String fuzzyKey) {
		if (map == null || map.isEmpty() || Util.isNullOrEmpty(fuzzyKey)) {
			return null;
		}

		// Quick exact match
		if (map.containsKey(fuzzyKey)) {
			return (T) map.get(fuzzyKey);
		}

		// Case-insensitive match
		for (String key : map.keySet()) {
			if (safeTrim(key).equalsIgnoreCase(safeTrim(fuzzyKey))) {
				return (T) map.get(key);
			}
		}

		// Whitespace and case insensitive match
		String collapsedKey = safeTrim(fuzzyKey.replaceAll("\\s+", ""));
		for (String key : map.keySet()) {
			if (safeTrim(key).equalsIgnoreCase(collapsedKey)) {
				return (T) map.get(key);
			}
		}

		return null;
	}

	/**
	 * Gets the input object as a thread-safe Script. If the input is a String, it
	 * will be interpreted as the source of a Script. If the input is already a Script
	 * object, it will be copied for thread safety and the copy returned.
	 *
	 * @param input The input object, either a string or a script
	 * @return The output
	 */
	public static Script getAsScript(Object input) {
		if (input instanceof Script) {
			Script copy = new Script();
			Script os = (Script) input;

			copy.setSource(os.getSource());
			copy.setIncludes(os.getIncludes());
			copy.setLanguage(os.getLanguage());
			return copy;
		} else if (input instanceof String) {
			Script tempScript = new Script();
			tempScript.setSource(Util.otoa(input));
			return tempScript;
		}
		return null;
	}

	/**
	 * Gets the attributes of the given source object. If the source is not a Sailpoint
	 * object, or if it's one of the objects without attributes, this method returns
	 * null.
	 *
	 * @param source The source object, which may implement an Attributes container method
	 * @return The attributes, if any, or null
	 */
	public static Attributes<String, Object> getAttributes(Object source) {
		if (source instanceof Identity) {
			Attributes<String, Object> attributes = ((Identity) source).getAttributes();
			if (attributes == null) {
				return new Attributes<>();
			}
			return attributes;
		} else if (source instanceof LinkInterface) {
			Attributes<String, Object> attributes = ((LinkInterface) source).getAttributes();
			if (attributes == null) {
				return new Attributes<>();
			}
			return attributes;
		} else if (source instanceof Bundle) {
			return ((Bundle) source).getAttributes();
		} else if (source instanceof Custom) {
			return ((Custom) source).getAttributes();
		} else if (source instanceof Configuration) {
			return ((Configuration) source).getAttributes();
		} else if (source instanceof Application) {
			return ((Application) source).getAttributes();
		} else if (source instanceof CertificationItem) {
			// This one returns a Map for some reason
			return new Attributes<>(((CertificationItem) source).getAttributes());
		} else if (source instanceof CertificationEntity) {
			return ((CertificationEntity) source).getAttributes();
		} else if (source instanceof Certification) {
			return ((Certification) source).getAttributes();
		} else if (source instanceof CertificationDefinition) {
			return ((CertificationDefinition) source).getAttributes();
		} else if (source instanceof TaskDefinition) {
			return ((TaskDefinition) source).getArguments();
		} else if (source instanceof TaskItem) {
			return ((TaskItem) source).getAttributes();
		} else if (source instanceof ManagedAttribute) {
			return ((ManagedAttribute) source).getAttributes();
		} else if (source instanceof Form) {
			return ((Form) source).getAttributes();
		} else if (source instanceof IdentityRequest) {
			return ((IdentityRequest) source).getAttributes();
		} else if (source instanceof IdentitySnapshot) {
			return ((IdentitySnapshot) source).getAttributes();
		} else if (source instanceof ResourceObject) {
			Attributes<String, Object> attributes = ((ResourceObject) source).getAttributes();
			if (attributes == null) {
				attributes = new Attributes<>();
			}
			return attributes;
		} else if (source instanceof Field) {
			return ((Field) source).getAttributes();
		} else if (source instanceof ProvisioningPlan) {
			Attributes<String, Object> arguments = ((ProvisioningPlan) source).getArguments();
			if (arguments == null) {
				arguments = new Attributes<>();
			}
			return arguments;
		} else if (source instanceof IntegrationConfig) {
			return ((IntegrationConfig) source).getAttributes();
		} else if (source instanceof ProvisioningProject) {
			return ((ProvisioningProject) source).getAttributes();
		} else if (source instanceof ProvisioningTransaction) {
			return ((ProvisioningTransaction) source).getAttributes();
		} else if (source instanceof ProvisioningPlan.AbstractRequest) {
			return ((ProvisioningPlan.AbstractRequest) source).getArguments();
		} else if (source instanceof Rule) {
			return ((Rule) source).getAttributes();
		} else if (source instanceof WorkItem) {
			return ((WorkItem) source).getAttributes();
		} else if (source instanceof Entitlements) {
			return ((Entitlements) source).getAttributes();
		} else if (source instanceof RpcRequest) {
			return new Attributes<>(((RpcRequest) source).getArguments());
		} else if (source instanceof ApprovalItem) {
			return ((ApprovalItem) source).getAttributes();
		}
		return null;
	}

	/**
	 * Gets the time zone associated with the logged in user's session, based on a UserContext or Identity
	 *
	 * @param userContext The user context to check for a time zone, or null
	 * @return The time zone for this user
	 */
	public static TimeZone getClientTimeZone(Identity identity, UserContext userContext) {
		if (userContext != null) {
			return userContext.getUserTimeZone();
		} else if (identity != null && identity.getUIPreference(MOST_RECENT_TIMEZONE) != null) {
			return TimeZone.getTimeZone((String) identity.getUIPreference(MOST_RECENT_TIMEZONE));
		} else {
			return WebUtil.getTimeZone(TimeZone.getDefault());
		}
	}

	/**
	 * Extracts a list of all country names from the JDK's Locale class. This will
	 * be as up-to-date as your JDK itself is.
	 *
	 * @return A sorted list of country names
	 */
	public static List<String> getCountryNames() {
		String[] countryCodes = Locale.getISOCountries();
		List<String> countries = new ArrayList<>();
		for (String countryCode : countryCodes) {
			Locale obj = new Locale("", countryCode);
			countries.add(obj.getDisplayCountry());
		}
		Collections.sort(countries);
		return countries;
	}

	/**
	 * Returns the first item in the input that is not nothing according to the {@link #isNothing(Object)} method.
	 *
	 * @param items The input items
	 * @param <T>   The superclass of all input items
	 * @return if the item is not null or empty
	 */
	public static <T> T getFirstNotNothing(T... items) {
		if (items == null || items.length == 0) {
			return null;
		}
		for (T item : items) {
			if (!Utilities.isNothing(item)) {
				return item;
			}
		}
		return null;
	}

	/**
	 * Returns the first item in the input that is not null
	 *
	 * @param items The input items
	 * @param <T>   The superclass of all input items
	 * @return if the item is not null or empty
	 */
	public static <T> T getFirstNotNull(T... items) {
		if (items == null || items.length == 0) {
			return null;
		}
		for (T item : items) {
			if (item != null) {
				return item;
			}
		}
		return null;
	}

	/**
	 * Gets the iiq.properties file contents (properly closing it, unlike IIQ...)
	 *
	 * @return The IIQ properties
	 * @throws GeneralException if any load failures occur
	 */
	public static Properties getIIQProperties() throws GeneralException {
		Properties props = new Properties();

		try (InputStream is = AbstractSailPointContext.class.getResourceAsStream("/" + BrandingServiceFactory.getService().getPropertyFile())) {
			props.load(is);
		} catch (IOException e) {
			throw new GeneralException(e);
		}
		return props;
	}

	/**
	 * IIQ tries to display timestamps in a locale-specific way to the user. It does this by storing the browser's time zone in the HTTP session and converting dates to a local value before display. This includes things like scheduled task execution times, etc.
	 * <p>
	 * However, for date fields, which should be timeless (i.e. just a date, no time component), IIQ sends a value of midnight at the browser's time zone. Depending on the browser's offset from the server time, this can result (in the worst case) in the actual selected instant being a full day ahead or behind the intended value.
	 * <p>
	 * This method corrects the offset using Java 8's Time API, which allows for timeless representations, to determine the date the user intended to select, then converting that back to midnight in the server time zone. The result is stored back onto the Field.
	 * <p>
	 * If the time is offset from midnight but the user time zone is the same as the server time zone, it means we're in a weird context where IIQ does not know the user's time zone and we have to guess. We will guess up to +12 and -11 from the server timezone, which should cover most cases. However, if you have users directly around the world from your server timezone, you may see problems.
	 * <p>
	 * If the input date is null, returns null.
	 *
	 * @param inputDate The input date for the user
	 * @param identity  The identity who has timezone information stored
	 */
	public static Date getLocalDate(Date inputDate, Identity identity) {
		if (inputDate == null) {
			return null;
		}
		Instant instant = Instant.ofEpochMilli(inputDate.getTime());
		TimeZone userTimeZone = getClientTimeZone(identity, null);
		ZoneId userZoneId = userTimeZone.toZoneId();
		ZoneId serverTimeZone = TimeZone.getDefault().toZoneId();
		ZonedDateTime zonedDateTime = instant.atZone(userZoneId);
		if (zonedDateTime.getHour() != 0) {
			// Need to shift
			if (userZoneId.equals(serverTimeZone)) {
				// IIQ doesn't know where the user is located, so we have to guess
				// Note that this will fail for user-to-server shifts greater than +12 and -11
				LocalDate timelessDate;
				if (zonedDateTime.getHour() >= 12) {
					// Assume that the user is located in a time zone ahead of the server (e.g. server is in UTC and user is in Calcutta), submitted time will appear to be before midnight in server time
					timelessDate = LocalDate.of(zonedDateTime.getYear(), zonedDateTime.getMonth(), zonedDateTime.getDayOfMonth());
					timelessDate = timelessDate.plusDays(1);
				} else {
					// The user is located in a time zone behind the server (e.g. server is in UTC and user is in New York), submitted time will appear to be after midnight in server time
					timelessDate = LocalDate.of(zonedDateTime.getYear(), zonedDateTime.getMonth(), zonedDateTime.getDayOfMonth());
				}
				return new Date(timelessDate.atStartOfDay(serverTimeZone).toInstant().toEpochMilli());
			} else {
				// IIQ knows where the user is located and we can just directly convert
				LocalDate timelessDate = LocalDate.of(zonedDateTime.getYear(), zonedDateTime.getMonth(), zonedDateTime.getDayOfMonth());
				return new Date(timelessDate.atStartOfDay(serverTimeZone).toInstant().toEpochMilli());
			}
		}
		// If the zonedDateTime in user time is midnight, then the user and server are aligned
		return inputDate;
	}

	/**
	 * Localizes a message based on the locale information captured on the
	 * target Identity. This information can be captured in any plugin web
	 * service or other session-attached code using {@link #tryCaptureLocationInfo(SailPointContext, UserContext)}
	 * <p>
	 * If no locale information has been captured, the system default will
	 * be used instead.
	 *
	 * @param target  The target user
	 * @param message The non-null message to translate
	 * @return The localized message
	 */
	public static String getLocalizedMessage(Identity target, Message message) {
		if (message == null) {
			throw new NullPointerException("message must not be null");
		}
		TimeZone tz = TimeZone.getDefault();
		if (target != null) {
			String mrtz = Util.otoa(target.getUIPreference(MOST_RECENT_TIMEZONE));
			if (Util.isNotNullOrEmpty(mrtz)) {
				tz = TimeZone.getTimeZone(mrtz);
			}
		}

		Locale locale = Locale.getDefault();
		if (target != null) {
			String mrl = Util.otoa(target.getUIPreference(MOST_RECENT_LOCALE));
			if (Util.isNotNullOrEmpty(mrl)) {
				locale = Locale.forLanguageTag(mrl);
			}
		}

		return message.getLocalizedMessage(locale, tz);
	}

	/**
	 * Gets the given property by introspection
	 *
	 * @param source            The source object
	 * @param paramPropertyPath The property path
	 * @return The object at the given path
	 * @throws GeneralException if a failure occurs
	 */
	public static Object getProperty(Object source, String paramPropertyPath) throws GeneralException {
		return getProperty(source, paramPropertyPath, false);
	}

	/**
	 * Gets the given property by introspection and 'dot-walking' the given path. Paths have the
	 * following semantics:
	 *
	 *  - Certain common 'quick paths' will be recognized and returned immediately, rather than
	 *    using reflection to construct the output.
	 *
	 *  - A dot '.' is used to separate path elements. Quotes are supported.
	 *
	 *  - Paths are evaluated against the current context.
	 *
	 *  - Elements within Collections and Maps can be addressed using three different syntaxes,
	 *    depending on your needs: list[1], list.1, or list._1. Similarly, map[key], map.key, or
	 *    map._key. Three options are available to account for Sailpoint's various parsers.
	 *
	 *  - If an element is a Collection, the context object (and thus output) becomes a Collection. All
	 *    further properties (other than indexes) are evalulated against each item in the collection.
	 *    For example, on an Identity, the property 'links.application.name' resolves to a List of Strings.
	 *
	 *    This does not cascade. For example, links.someMultiValuedAttribute will result in a List of Lists,
	 *    one for each item in 'links', rather than a single List containing all values of the attribute.
	 *    TODO improve nested expansion, if it makes sense.
	 *
	 * If you pass true for 'gracefulNulls', a null value or an invalid index at any point in the path
	 * will simply result in a null output. If it is not set, a NullPointerException or IndexOutOfBoundsException
	 * will be thrown as appropriate.
	 *
	 * @param source            The context object against which to evaluate the path
	 * @param paramPropertyPath The property path to evaluate
	 * @param gracefulNulls     If true, encountering a null or bad index mid-path will result in an overall null return value, not an exception
	 * @return The object at the given path
	 * @throws GeneralException if a failure occurs
	 */
	public static Object getProperty(Object source, String paramPropertyPath, boolean gracefulNulls) throws GeneralException {
		String propertyPath = paramPropertyPath.replaceAll("\\[(\\w+)\\]", ".$1");

		Object tryQuick = getQuickProperty(source, propertyPath);
		// This returns Utilities.NONE if this isn't an available "quick property", because
		// a property can legitimately have the value null.
		if (!Util.nullSafeEq(tryQuick, NONE)) {
			return tryQuick;
		}
		RFC4180LineParser parser = new RFC4180LineParser('.');
		List<String> tokens = parser.parseLine(propertyPath);
		Object current = source;
		StringBuilder filterString = new StringBuilder();
		for(String property : Util.safeIterable(tokens)) {
			try {
				if (current == null) {
					if (gracefulNulls) {
						return null;
					} else {
						throw new NullPointerException("Found a nested null object at " + filterString.toString());
					}
				}
				boolean found = false;
				// If this looks likely to be an index...
				if (current instanceof List) {
					if (property.startsWith("_") && property.length() > 1) {
						property = property.substring(1);
					}
					if (Character.isDigit(property.charAt(0))) {
						int index = Integer.parseInt(property);
						if (gracefulNulls) {
							current = Utilities.safeSubscript((List<?>) current, index);
						} else {
							current = ((List<?>) current).get(index);
						}
						found = true;
					} else {
						List<Object> result = new ArrayList<>();
						for (Object input : (List<?>) current) {
							result.add(getProperty(input, property, gracefulNulls));
						}
						current = result;
						found = true;
					}
				} else if (current instanceof Map) {
					if (property.startsWith("_") && property.length() > 1) {
						property = property.substring(1);
					}
					current = Util.get((Map<?, ?>) current, property);
					found = true;
				}
				if (!found) {
					// This returns Utilities.NONE if this isn't an available "quick property", because
					// a property can legitimately have the value null.
					Object result = getQuickProperty(current, property);
					if (Util.nullSafeEq(result, NONE)) {
						Method getter = Reflection.getGetter(current.getClass(), property);
						if (getter != null) {
							current = getter.invoke(current);
						} else {
							Attributes<String, Object> attrs = Utilities.getAttributes(current);
							if (attrs != null) {
								current = PropertyUtils.getProperty(attrs, property);
							} else {
								current = PropertyUtils.getProperty(current, property);
							}
						}
					} else {
						current = result;
					}
				}
			} catch (Exception e) {
				throw new GeneralException("Error resolving path '" + filterString + "." + property + "'", e);
			}
			filterString.append('.').append(property);
		}
		return current;
	}

	/**
	 * Returns a "quick property" which does not involve introspection. If the
	 * property is not in this list, the result will be {@link Utilities#NONE}.
	 *
	 * @param source       The source object
	 * @param propertyPath The property path to check
	 * @return the object, if this is a known "quick property" or Utilities.NONE if not
	 * @throws GeneralException if a failure occurs
	 */
	public static Object getQuickProperty(Object source, String propertyPath) throws GeneralException {
		// Giant ladders of if statements for common attributes will be much faster.
		if (source == null || propertyPath == null) {
			return null;
		}
		if (source instanceof SailPointObject) {
			if (propertyPath.equals("name")) {
				return ((SailPointObject) source).getName();
			} else if (propertyPath.equals("id")) {
				return ((SailPointObject) source).getId();
			} else if (propertyPath.equals("xml")) {
				return ((SailPointObject) source).toXml();
			} else if (propertyPath.equals("owner.id")) {
				Identity other = ((SailPointObject) source).getOwner();
				if (other != null) {
					return other.getId();
				} else {
					return null;
				}
			} else if (propertyPath.equals("owner.name")) {
				Identity other = ((SailPointObject) source).getOwner();
				if (other != null) {
					return other.getName();
				} else {
					return null;
				}
			} else if (propertyPath.equals("owner")) {
				return ((SailPointObject) source).getOwner();
			} else if (propertyPath.equals("created")) {
				return ((SailPointObject) source).getCreated();
			} else if (propertyPath.equals("modified")) {
				return ((SailPointObject) source).getModified();
			}
		}
		if (source instanceof Describable) {
			if (propertyPath.equals("description")) {
				return ((Describable) source).getDescription(Locale.getDefault());
			}
		}
		if (source instanceof ManagedAttribute) {
			if (propertyPath.equals("value")) {
				return ((ManagedAttribute) source).getValue();
			} else if (propertyPath.equals("attribute")) {
				return ((ManagedAttribute) source).getAttribute();
			} else if (propertyPath.equals("application")) {
				return ((ManagedAttribute) source).getApplication();
			} else if (propertyPath.equals("applicationId") || propertyPath.equals("application.id")) {
				return ((ManagedAttribute) source).getApplicationId();
			} else if (propertyPath.equals("application.name")) {
				return ((ManagedAttribute) source).getApplication().getName();
			} else if (propertyPath.equals("displayName")) {
				return ((ManagedAttribute) source).getDisplayName();
			} else if (propertyPath.equals("displayableName")) {
				return ((ManagedAttribute) source).getDisplayableName();
			}
		} else if (source instanceof Link) {
			if (propertyPath.equals("nativeIdentity")) {
				return ((Link) source).getNativeIdentity();
			} else if (propertyPath.equals("displayName") || propertyPath.equals("displayableName")) {
				return ((Link) source).getDisplayableName();
			} else if (propertyPath.equals("description")) {
				return ((Link) source).getDescription();
			} else if (propertyPath.equals("applicationName") || propertyPath.equals("application.name")) {
				return ((Link) source).getApplicationName();
			} else if (propertyPath.equals("applicationId") || propertyPath.equals("application.id")) {
				return ((Link) source).getApplicationId();
			} else if (propertyPath.equals("application")) {
				return ((Link) source).getApplication();
			} else if (propertyPath.equals("identity")) {
				return ((Link) source).getIdentity();
			} else if (propertyPath.equals("permissions")) {
				return ((Link) source).getPermissions();
			}
		} else if (source instanceof Identity) {
			if (propertyPath.equals("manager")) {
				return ((Identity) source).getManager();
			} else if (propertyPath.equals("manager.name")) {
				Identity manager = ((Identity) source).getManager();
				if (manager != null) {
					return manager.getName();
				} else {
					return null;
				}
			} else if (propertyPath.equals("manager.id")) {
				Identity manager = ((Identity) source).getManager();
				if (manager != null) {
					return manager.getId();
				} else {
					return null;
				}
			} else if (propertyPath.equals("lastRefresh")) {
				return ((Identity) source).getLastRefresh();
			} else if (propertyPath.equals("needsRefresh")) {
				return ((Identity) source).isNeedsRefresh();
			} else if (propertyPath.equals("lastname")) {
				return ((Identity) source).getLastname();
			} else if (propertyPath.equals("firstname")) {
				return ((Identity) source).getFirstname();
			} else if (propertyPath.equals("type")) {
				return ((Identity) source).getType();
			} else if (propertyPath.equals("displayName") || propertyPath.equals("displayableName")) {
				return ((Identity) source).getDisplayableName();
			} else if (propertyPath.equals("roleAssignments")) {
				return nullToEmpty(((Identity) source).getRoleAssignments());
			} else if (propertyPath.equals("roleDetections")) {
				return nullToEmpty(((Identity) source).getRoleDetections());
			} else if (propertyPath.equals("assignedRoles")) {
				return nullToEmpty(((Identity) source).getAssignedRoles());
			} else if (propertyPath.equals("assignedRoles.name")) {
				return safeStream(((Identity) source).getAssignedRoles()).map(Bundle::getName).collect(Collectors.toList());
			} else if (propertyPath.equals("detectedRoles")) {
				return nullToEmpty(((Identity) source).getDetectedRoles());
			} else if (propertyPath.equals("detectedRoles.name")) {
				return safeStream(((Identity) source).getDetectedRoles()).map(Bundle::getName).collect(Collectors.toList());
			} else if (propertyPath.equals("links.application.name")) {
				return safeStream(((Identity) source).getLinks()).map(Link::getApplicationName).collect(Collectors.toList());
			} else if (propertyPath.equals("links.application.id")) {
				return safeStream(((Identity) source).getLinks()).map(Link::getApplicationId).collect(Collectors.toList());
			} else if (propertyPath.equals("links.application")) {
				return safeStream(((Identity) source).getLinks()).map(Link::getApplication).collect(Collectors.toList());
			} else if (propertyPath.equals("links")) {
				return nullToEmpty(((Identity) source).getLinks());
			} else if (propertyPath.equals("administrator")) {
				return ((Identity) source).getAdministrator();
			} else if (propertyPath.equals("administrator.id")) {
				Identity other = ((Identity) source).getAdministrator();
				if (other != null) {
					return other.getId();
				} else {
					return null;
				}
			} else if (propertyPath.equals("administrator.name")) {
				Identity other = ((Identity) source).getAdministrator();
				if (other != null) {
					return other.getName();
				} else {
					return null;
				}
			} else if (propertyPath.equals("capabilities")) {
				return nullToEmpty(((Identity) source).getCapabilityManager().getEffectiveCapabilities());
			} else if (propertyPath.equals("email")) {
				return ((Identity) source).getEmail();
			}
		} else if (source instanceof Bundle) {
			if (propertyPath.equals("type")) {
				return ((Bundle) source).getType();
			}
		}
		return NONE;
	}

	/**
	 * Gets the shared background pool, an instance of {@link ForkJoinPool}. This is stored
	 * in the core CustomGlobal class so that it can be shared across all IIQ classloader
	 * contexts and will not leak when a new plugin is deployed.
	 * <p>
	 * The parallelism count can be changed in SystemConfiguration under the key 'commonThreadPoolParallelism'.
	 *
	 * @return An instance of the shared background pool
	 */
	public static ExecutorService getSharedBackgroundPool() {
		ExecutorService backgroundPool = (ExecutorService) CustomGlobal.get(IDW_WORKER_POOL);
		if (backgroundPool == null) {
			synchronized (CustomGlobal.class) {
				Configuration systemConfig = Configuration.getSystemConfig();
				int parallelism = 8;

				if (systemConfig != null) {
					Integer configValue = systemConfig.getInteger("commonThreadPoolParallelism");
					if (configValue != null && configValue > 1) {
						parallelism = configValue;
					}
				}

				backgroundPool = (ExecutorService) CustomGlobal.get(IDW_WORKER_POOL);
				if (backgroundPool == null) {
					backgroundPool = new ForkJoinPool(parallelism);
					CustomGlobal.put(IDW_WORKER_POOL, backgroundPool);
				}
			}
		}
		return backgroundPool;
	}

	/**
	 * Returns true if parentClass is assignable from testClass, e.g. if the following code
	 * would not fail to compile:
	 *
	 * TestClass ot = new TestClass();
	 * ParentClass tt = ot;
	 *
	 * This is also equivalent to 'b instanceof A' or 'B extends A'.
	 *
	 * Primitive types and their boxed equivalents have special handling.
	 *
	 * @param parentClass The first (parent-ish) class
	 * @param testClass  The second (child-ish) class
	 * @param <A> The parent type
	 * @param <B> The potential child type
	 * @return True if parentClass is assignable from testClass
	 */
	public static <A, B> boolean isAssignableFrom(Class<A> parentClass, Class<B> testClass) {
		Class<?> targetType = Objects.requireNonNull(parentClass);
		Class<?> otherType = Objects.requireNonNull(testClass);
		if (targetType.isPrimitive() != otherType.isPrimitive()) {
			if (targetType.isPrimitive()) {
				targetType = box(targetType);
			} else {
				otherType = box(otherType);
			}
		} else if (targetType.isPrimitive()) {
			// We know the 'primitive' flags are the same, so they must both be primitive.
			if (targetType.equals(Long.TYPE)) {
				return otherType.equals(Long.TYPE) || otherType.equals(Integer.TYPE) || otherType.equals(Short.TYPE) || otherType.equals(Character.TYPE) || otherType.equals(Byte.TYPE);
			} else if (targetType.equals(Integer.TYPE)) {
				return otherType.equals(Integer.TYPE) || otherType.equals(Short.TYPE) || otherType.equals(Character.TYPE) || otherType.equals(Byte.TYPE);
			} else if (targetType.equals(Short.TYPE)) {
				return otherType.equals(Short.TYPE) || otherType.equals(Character.TYPE) || otherType.equals(Byte.TYPE);
			} else if (targetType.equals(Character.TYPE)) {
				return otherType.equals(Character.TYPE) || otherType.equals(Byte.TYPE);
			} else if (targetType.equals(Byte.TYPE)) {
				return otherType.equals(Byte.TYPE);
			} else if (targetType.equals(Boolean.TYPE)) {
				return otherType.equals(Boolean.TYPE);
			} else if (targetType.equals(Double.TYPE)) {
				return otherType.equals(Double.TYPE) || otherType.equals(Float.TYPE) || otherType.equals(Long.TYPE) || otherType.equals(Integer.TYPE) || otherType.equals(Short.TYPE) || otherType.equals(Character.TYPE) || otherType.equals(Byte.TYPE);
			} else if (targetType.equals(Float.TYPE)) {
				return otherType.equals(Float.TYPE) || otherType.equals(Long.TYPE) || otherType.equals(Integer.TYPE) || otherType.equals(Short.TYPE) || otherType.equals(Character.TYPE) || otherType.equals(Byte.TYPE);
			} else {
				throw new IllegalArgumentException("Unrecognized primitive target class: " + targetType.getName());
			}
		}

		return targetType.isAssignableFrom(otherType);
	}

	/**
	 * Returns the inverse of {@link #isFlagSet(Object)}.
	 *
	 * @param flagValue The flag value to check
	 * @return True if the flag is NOT a 'true' value
	 */
	public static boolean isFlagNotSet(Object flagValue) {
		return !isFlagSet(flagValue);
	}

	/**
	 * Forwards to {@link Utilities#isFlagSet(Object)}
	 */
	public static boolean isFlagSet(String stringFlag) {
		if (stringFlag == null) {
			return false;
		}
		return isFlagSet((Object)stringFlag);
	}

	/**
	 * Check if a String, Boolean, or Number flag object should be considered equivalent to boolean true.
	 *
	 * For strings, true values are: (true, yes, 1, Y), all case-insensitive. All other strings are false.
	 *
	 * Boolean 'true' will also be interpreted as a true value.
	 *
	 * Numeric '1' will also be interpreted as a true value.
	 *
	 * All other values, including null, will always be false.
	 *
	 * @param flagValue String representation of a flag
	 * @return if flag is true
	 */
	public static boolean isFlagSet(Object flagValue) {
		if (flagValue instanceof String) {
			String stringFlag = (String)flagValue;
			if (stringFlag.equalsIgnoreCase("true")) {
				return true;
			} else if (stringFlag.equalsIgnoreCase("yes")) {
				return true;
			} else if (stringFlag.equals("1")) {
				return true;
			} else if (stringFlag.equalsIgnoreCase("Y")) {
				return true;
			}
		} else if (flagValue instanceof Boolean) {
			return ((Boolean) flagValue);
		} else if (flagValue instanceof Number) {
			int numValue = ((Number) flagValue).intValue();
			return (numValue == 1);
		}
		return false;
	}

	/**
	 * Returns the inverse of {@link #isAssignableFrom(Class, Class)}. In
	 * other words, returns true if the following code would fail to compile:
	 *
	 * TestClass ot = new TestClass();
	 * ParentClass tt = ot;
	 *
	 * @param parentClass The first (parent-ish) class
	 * @param testClass  The second (child-ish) class
	 * @param <A> The parent type
	 * @param <B> The potential child type
	 * @return True if parentClass is NOT assignable from testClass
	 */
	public static <A, B> boolean isNotAssignableFrom(Class<A> parentClass, Class<B> testClass) {
		return !isAssignableFrom(parentClass, testClass);
	}

	/**
	 * Returns true if the input is NOT an empty Map.
	 *
	 * @param map The map to check
	 * @return True if the input is NOT an empty map
	 */
	public static boolean isNotEmpty(Map<?, ?> map) {
		return !Util.isEmpty(map);
	}

	/**
	 * Returns true if the input is NOT an empty Collection.
	 *
	 * @param list The list to check
	 * @return True if the input is NOT an empty collection
	 */
	public static boolean isNotEmpty(Collection<?> list) {
		return !Util.isEmpty(list);
	}

	/**
	 * Returns true if the input string is not null and contains any non-whitespace
	 * characters.
	 *
	 * @param input The input string to check
	 * @return True if the input is not null, empty, or only whitespace
	 */
	public static boolean isNotNullEmptyOrWhitespace(String input) {
		return !isNullEmptyOrWhitespace(input);
	}

	/**
	 * Returns true if the input is NOT only digits
	 *
	 * @param input The input to check
	 * @return True if the input contains any non-digit characters
	 */
	public static boolean isNotNumber(String input) {
		return !isNumber(input);
	}

	/**
	 * Returns true if the given object is 'nothing': a null, empty string, empty map, empty Collection, or empty Iterable.
	 *
	 * Iterables will be flushed using {@link Util#flushIterator(Iterator)}. That means this may
	 * be a slow process for Iterables.
	 *
	 * @param thing The object to test for nothingness
	 * @return True if the object is nothing
	 */
	public static boolean isNothing(Object thing) {
		if (thing == null) {
			return true;
		} else if (thing instanceof String) {
			return ((String) thing).trim().isEmpty();
		} else if (thing instanceof Object[]) {
			return (((Object[]) thing).length == 0);
		} else if (thing instanceof Collection) {
			return ((Collection<?>) thing).isEmpty();
		} else if (thing instanceof Map) {
			return ((Map<?,?>) thing).isEmpty();
		} else if (thing instanceof Iterable) {
			Iterator<?> i = ((Iterable<?>) thing).iterator();
			boolean empty = !i.hasNext();
			Util.flushIterator(i);
			return empty;
		}
		return false;
	}

	/**
	 * Returns true if the input string is null, empty, or contains only whitespace
	 * characters according to {@link Character#isWhitespace(char)}.
	 * @param input The input string
	 * @return True if the input is null, empty, or only whitespace
	 */
	public static boolean isNullEmptyOrWhitespace(String input) {
		if (input == null || input.isEmpty()) {
			return true;
		}
		for(char c : input.toCharArray()) {
			if (!Character.isWhitespace(c)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Returns true if this string contains only digits
	 * @param input The input string
	 * @return True if this string contains only digits
	 */
	public static boolean isNumber(String input) {
		if (input == null || input.isEmpty()) {
			return false;
		}

		int nonDigits = 0;

		for(int i = 0; i < input.length(); ++i) {
			char ch = input.charAt(i);
			if (!Character.isDigit(ch)) {
				nonDigits++;
				break;
			}
		}

		return (nonDigits == 0);
	}

	/**
	 * Returns true if {@link #isNothing(Object)} would return false.
	 *
	 * @param thing The thing to check
	 * @return True if the object is NOT a 'nothing' value
	 */
	public static boolean isSomething(Object thing) {
		return !isNothing(thing);
	}

	/**
	 * Returns the current time in the standard ISO offset (date T time+1:00) format
	 * @return The formatted current time
	 */
	public static String isoOffsetTimestamp() {
		LocalDateTime now = LocalDateTime.now();
		DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
		return now.format(formatter);
	}

	/**
	 * Creates a MessageAccumulator that just inserts the message into the target list.
	 *
	 * @param target The target list, to which messages will be added. Must be thread-safe for add.
	 * @param dated If true, messages will be prepended with the output of {@link #timestamp()}
	 * @return The resulting MessageAccumulator implementation
	 */
	public static MessageAccumulator listMessageAccumulator(List<String> target, boolean dated) {
		return (msg) -> {
			String finalMessage = msg.getLocalizedMessage();
			if (dated) {
				finalMessage = timestamp() + " " + finalMessage;
			}
			target.add(finalMessage);
		};
	}

	/**
	 * Returns a new *modifiable* list with the objects specified added to it.
	 * A new list will be returned on each invocation. This method will never
	 * return null.
	 *
	 * @param objects The objects to add to the list
 	 * @param <T> The type of the list
	 * @return A list containing each of the items
	 */
	@SafeVarargs
	public static <T> List<T> listOf(T... objects) {
		List<T> list = new ArrayList<>();
		if (objects != null) {
			Collections.addAll(list, objects);
		}
		return list;
	}

	/**
	 * Returns true if every key and value in the Map can be cast to the types given.
	 * Passing a null for either Class parameter will ignore that check.
	 *
	 * A true result should guarantee no ClassCastExceptions will result from casting
	 * the Map keys to any of the given values.
	 *
	 * If both expected types are null or equal to {@link Object}, this method will trivially
	 * return true. If the map is null or empty, this method will trivially return true.
	 *
	 * NOTE that this method will iterate over all key-value pairs in the Map, so may
	 * be quite expensive if the Map is large.
	 *
	 * @param inputMap The input map to check
	 * @param expectedKeyType The type implemented or extended by all Map keys
	 * @param expectedValueType The type implemented or exended by all Map values
	 * @param <S> The map key type
	 * @param <T> The map value type
	 * @return True if all map keys and values will NOT throw an exception on being cast to either given type, false otherwise
	 */
	public static <S, T> boolean mapConformsToType(Map<?, ?> inputMap, Class<S> expectedKeyType, Class<T> expectedValueType) {
		if (inputMap == null || inputMap.isEmpty()) {
			// Trivially true, since nothing can throw a ClassCastException
			return true;
		}
		if ((expectedKeyType == null || Object.class.equals(expectedKeyType)) && (expectedValueType == null || Object.class.equals(expectedValueType))) {
			return true;
		}
		for(Map.Entry<?, ?> entry : inputMap.entrySet()) {
			Object key = entry.getKey();
			if (key != null && expectedKeyType != null && !Object.class.equals(expectedKeyType) && !expectedKeyType.isAssignableFrom(key.getClass())) {
				return false;
			}
			Object value = entry.getValue();
			if (value != null && expectedValueType != null && !Object.class.equals(expectedValueType) && !expectedValueType.isAssignableFrom(value.getClass())) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Creates a map from a varargs list of items, where every other item is a key or value.
	 *
	 * If the arguments list does not have enough items for the final value, null will be used.
	 *
	 * @param input The input items, alternating key and value
	 * @param <S> The key type
	 * @param <T> The value type
	 * @return on failures
	 */
	@SuppressWarnings("unchecked")
	public static <S, T> Map<S, T> mapOf(Object... input) {
		Map<S, T> map = new HashMap<>();

		if (input != null && input.length > 0) {
			for(int i = 0; i < input.length; i += 2) {
				S key = (S) input[i];
				T value = null;
				if (input.length > (i + 1)) {
					value = (T) input[i + 1];
				}
				map.put(key, value);
			}
		}

		return map;
	}

	/**
	 * Transforms a MatchExpression selector into a CompoundFilter
	 * @param input The input expression
	 * @return The newly created CompoundFilter corresponding to the MatchExpression
	 */
	public static CompoundFilter matchExpressionToCompoundFilter(IdentitySelector.MatchExpression input) {
		CompoundFilter filter = new CompoundFilter();
		List<Application> applications = new ArrayList<>();

		Application expressionApplication = input.getApplication();
		if (expressionApplication != null) {
			applications.add(expressionApplication);
		}

		List<Filter> filters = new ArrayList<>();

		for(IdentitySelector.MatchTerm term : Util.safeIterable(input.getTerms())) {
			filters.add(matchTermToFilter(term, expressionApplication, applications));
		}

		Filter mainFilter;

		if (filters.size() == 1) {
			mainFilter = filters.get(0);
		} else if (input.isAnd()) {
			mainFilter = Filter.and(filters);
		} else {
			mainFilter = Filter.or(filters);
		}

		filter.setFilter(mainFilter);
		filter.setApplications(applications);
		return filter;
	}

	/**
	 * Create a MatchTerm from scratch
	 * @param property The property to test
	 * @param value The value to test
	 * @param application The application to associate the term with (or null)
	 * @return The newly created MatchTerm
	 */
	public static IdentitySelector.MatchTerm matchTerm(String property, Object value, Application application) {
		IdentitySelector.MatchTerm term = new IdentitySelector.MatchTerm();
		term.setApplication(application);
		term.setName(property);
		term.setValue(Util.otoa(value));
		term.setType(IdentitySelector.MatchTerm.Type.Entitlement);
		return term;
	}

	/**
	 * Transforms a MatchTerm into a Filter, which can be useful for then modifying it
	 * @param term The matchterm to transform
	 * @param defaultApplication The default application if none is specified (may be null)
	 * @param applications The list of applications
	 * @return The new Filter object
	 */
	public static Filter matchTermToFilter(IdentitySelector.MatchTerm term, Application defaultApplication, List<Application> applications) {
		int applId = -1;
		Application appl = null;
		if (term.getApplication() != null) {
			appl = term.getApplication();
			if (!applications.contains(appl)) {
				applications.add(appl);
			}
			applId = applications.indexOf(appl);
		}
		if (appl == null && defaultApplication != null) {
			appl = defaultApplication;
			applId = applications.indexOf(defaultApplication);
		}
		if (term.isContainer()) {
			List<Filter> filters = new ArrayList<>();
			for(IdentitySelector.MatchTerm child : Util.safeIterable(term.getChildren())) {
				filters.add(matchTermToFilter(child, appl, applications));
			}
			if (term.isAnd()) {
				return Filter.and(filters);
			} else {
				return Filter.or(filters);
			}
		} else {
			String filterProperty = term.getName();
			if (applId >= 0) {
				filterProperty = applId + ":" + filterProperty;
			}
			if (Util.isNullOrEmpty(term.getValue())) {
				return Filter.isnull(filterProperty);
			}
			return Filter.eq(filterProperty, term.getValue());
		}
	}

	/**
	 * Returns the maximum of the given dates. If no values are passed, returns the
	 * earliest possible date.
	 *
	 * @param dates The dates to find the max of
	 * @return The maximum date in the array
	 */
	public static Date max(Date... dates) {
		Date maxDate = new Date(Long.MIN_VALUE);
		for(Date d : Objects.requireNonNull(dates)) {
			if (d.after(maxDate)) {
				maxDate = d;
			}
		}
		return new Date(maxDate.getTime());
	}

	/**
	 * Returns the maximum of the given dates. If no values are passed, returns the
	 * latest possible date. The returned value is always a new object, not one
	 * of the actual objects in the input.
	 *
	 * @param dates The dates to find the max of
	 * @return The maximum date in the array
	 */
	public static Date min(Date... dates) {
		Date minDate = new Date(Long.MAX_VALUE);
		for(Date d : Objects.requireNonNull(dates)) {
			if (d.before(minDate)) {
				minDate = d;
			}
		}
		return new Date(minDate.getTime());
	}

	/**
	 * Returns true if {@link Util#nullSafeEq(Object, Object)} would return
	 * false and vice versa. Two null values will be considered equal.
	 *
	 * @param a The first value
	 * @param b The second value
	 * @return True if the values are NOT equal
	 */
	public static boolean nullSafeNotEq(Object a, Object b) {
		return !Util.nullSafeEq(a, b, true);
	}

	/**
	 * Returns the input string if it is not null. Otherwise, returns an empty string.
	 *
	 * @param maybeNull The input string, which is possibly null
	 * @return The input string or an empty string
	 */
	public static String nullToEmpty(String maybeNull) {
		if (maybeNull == null) {
			return "";
		}
		return maybeNull;
	}

	/**
	 * Returns the input string if it is not null, explicitly noting the input as a String to
	 * make Beanshell happy at runtime.
	 *
	 * Identical to {@link Utilities#nullToEmpty(String)}, except Beanshell can't decide
	 * which of the method variants to call when the input is null. This leads to scripts
	 * sometimes calling {@link Utilities#nullToEmpty(Map)} instead. (Java would be able
	 * to infer the overload to invoke at compile time by using the variable type.)
	 *
	 * @param maybeNull The input string, which is possibly null
	 * @return The input string or an empty string
	 */
	public static String nullToEmptyString(String maybeNull) {
		return Utilities.nullToEmpty(maybeNull);
	}

	/**
	 * Converts the given collection to an empty Attributes, if a null object is passed,
	 * the input object (if an Attributes is passed), or a new Attributes containing all
	 * elements from the input Map (if any other type of Map is passed).
	 *
	 * @param input The input map or attributes
	 * @return The result as described above
	 */
	public static Attributes<String, Object> nullToEmpty(Map<String, ? extends Object> input) {
		if (input == null) {
			return new Attributes<>();
		} else if (input instanceof Attributes) {
			return (Attributes<String, Object>) input;
		} else {
			return new Attributes<>(input);
		}
	}

	/**
	 * Converts the given collection to an empty list (if a null value is passed),
	 * the input object (if a List is passed), or a copy of the input object in a
	 * new ArrayList (if any other Collection is passed).
	 *
	 * @param input The input collection
	 * @param <T> The type of the list
	 * @return The result as described above
	 */
	public static <T> List<T> nullToEmpty(Collection<T> input) {
		if (input == null) {
			return new ArrayList<>();
		} else if (input instanceof List) {
			return (List<T>)input;
		} else {
			return new ArrayList<>(input);
		}
	}

	/**
	 * Constructs a new map with each key having the prefix added to it. This can be
	 * used to merge two maps without overwriting keys from either one, for example.
	 *
	 * @param map The input map, which will not be modified
	 * @param prefix The prefix to add to each key
	 * @param <V> The value type of the map
	 * @return A new {@link HashMap} with each key prefixed by the given prefix
	 */
	public static <V> Map<String, V> prefixMap(Map<String, V> map, String prefix) {
		Map<String, V> newMap = new HashMap<>();
		for(String key : map.keySet()) {
			newMap.put(prefix + key, map.get(key));
		}
		return newMap;
	}

	/**
	 * Attempts to dynamically evaluate whatever thing is passed in and return the
	 * output of running the thing. If the thing is not of a known type, this method
	 * silently returns null.
	 *
	 * The following types of things are accept3ed:
	 *
	 *  - sailpoint.object.Rule: Evaluates as a SailPoint rule
	 *  - sailpoint.object.Script: Evaluates as a SailPoint script after being cloned
	 *  - java.lang.String: Evaluates as a SailPoint script
	 *  - java.util.function.Function: Accepts the Map of parameters, returns a value
	 *  - java.util.function.Consumer: Accepts the Map of parameters
	 *  - sailpoint.object.JavaRuleExecutor: Evaluates as a Java rule
	 *
	 * For Function and Consumer things, the context and a log will be added to the
	 * params.
	 *
	 * @param context The sailpoint context
	 * @param thing The thing to run
	 * @param params The parameters to pass to the thing, if any
	 * @param <T> the context-sensitive type of the return
	 * @return The return value from the thing executed, or null
	 * @throws GeneralException if any failure occurs
	 */
	@SuppressWarnings("unchecked")
	public static <T> T quietRun(SailPointContext context, Object thing, Map<String, Object> params) throws GeneralException {
		if (thing instanceof Rule) {
			Rule rule = (Rule)thing;
			return (T)context.runRule(rule, params);
		} else if (thing instanceof String || thing instanceof Script) {
			Script safeScript = getAsScript(thing);
			return (T) context.runScript(safeScript, params);
		} else if (thing instanceof Reference) {
			SailPointObject ref = ((Reference) thing).resolve(context);
			return quietRun(context, ref, params);
		} else if (thing instanceof Callable) {
			try {
				Callable<T> callable = (Callable<T>)thing;
				return callable.call();
			} catch (Exception e) {
				throw new GeneralException(e);
			}
		} else if (thing instanceof Function) {
			Function<Map<String, Object>, T> function = (Function<Map<String, Object>, T>) thing;
			params.put("context", context);
			params.put("log", LogFactory.getLog(thing.getClass()));
			return function.apply(params);
		} else if (thing instanceof Consumer) {
			Consumer<Map<String, Object>> consumer = (Consumer<Map<String, Object>>) thing;
			params.put("context", context);
			params.put("log", LogFactory.getLog(thing.getClass()));
			consumer.accept(params);
		} else if (thing instanceof JavaRuleExecutor) {
			JavaRuleExecutor executor = (JavaRuleExecutor)thing;
			JavaRuleContext javaRuleContext = new JavaRuleContext(context, params);
			try {
				return (T)executor.execute(javaRuleContext);
			} catch(GeneralException e) {
				throw e;
			} catch(Exception e) {
				throw new GeneralException(e);
			}
		}
		return null;
	}

	/**
	 * Safely casts the given input to the target type.
	 *
	 * If the object cannot be cast to the target type, this method returns null instead of throwing a ClassCastException.
	 *
	 * If the input object is null, this method returns null.
	 *
	 * If the targetClass is null, this method throws a {@link NullPointerException}.
	 *
	 * @param input The input object to cast
	 * @param targetClass The target class to which it should be cast
	 * @param <T> The expected return type
	 * @return The object cast to the given type, or null if it cannot be cast
	 */
    public static <T> T safeCast(Object input, Class<T> targetClass) {
		if (input == null) {
			return null;
		}
		Objects.requireNonNull(targetClass, "targetClass must not be null");
		if (targetClass.isAssignableFrom(input.getClass())) {
			return targetClass.cast(input);
		}
		return null;
    }

	/**
	 * Returns the class name of the object, or the string 'null', suitable for logging safely
	 * @param any The object to get the type of
	 * @return The class name, or the string 'null'
	 */
	public static String safeClassName(Object any) {
    	if (any == null) {
    		return "null";
		} else {
    		return any.getClass().getName();
		}
	}

    public static boolean safeContainsAll(Object maybeBiggerCollection, Object maybeSmallerCollection) {
    	if (maybeBiggerCollection == null || maybeSmallerCollection == null) {
    		return false;
		}
    	List<Object> biggerCollection = new ArrayList<>();
    	if (maybeBiggerCollection instanceof Collection) {
    		biggerCollection.addAll((Collection<?>) maybeBiggerCollection);
		} else if (maybeBiggerCollection instanceof Object[]) {
    		biggerCollection.addAll(Arrays.asList((Object[])maybeBiggerCollection));
		} else {
    		biggerCollection.add(maybeBiggerCollection);
		}

		List<Object> smallerCollection = new ArrayList<>();
		if (maybeSmallerCollection instanceof Collection) {
			smallerCollection.addAll((Collection<?>) maybeSmallerCollection);
		} else if (maybeSmallerCollection instanceof Object[]) {
			smallerCollection.addAll(Arrays.asList((Object[])maybeSmallerCollection));
		} else {
			smallerCollection.add(maybeSmallerCollection);
		}

		return biggerCollection.containsAll(smallerCollection);
	}

	/**
	 * Returns a long timestamp for the input Date, returning {@link Long#MIN_VALUE} if the
	 * Date is null.
	 *
	 * @param input The input date
	 * @return The timestamp of the date, or Long.MIN_VALUE if it is null
	 */
	public static long safeDateTimestamp(Date input) {
		if (input == null) {
			return Long.MIN_VALUE;
		}
		return input.getTime();
	}

	/**
	 * Returns a stream for the given map's keys if it's not null, or an empty stream if it is
	 * @param map The map
	 * @param <A> The type of the map's keys
	 * @param <B> The type of the map's values
	 */
	public static <A, B> void safeForeach(Map<A, B> map, BiConsumer<A, B> function) {
		if (map == null) {
			return;
		}
		for(Map.Entry<A, B> entry : map.entrySet()) {
			function.accept(entry.getKey(), entry.getValue());
		}
	}

	/**
	 * Returns a stream for the given map's keys if it's not null, or an empty stream if it is
	 * @param map The map
	 * @param <T> The type of the map's keys
	 * @return A stream from the map's keys, or an empty stream
	 */
	public static <T> Stream<T> safeKeyStream(Map<T, ?> map) {
		if (map == null) {
			return Stream.empty();
		}
		return map.keySet().stream();
	}

	/**
	 * Safely converts the given input to a List.
	 *
	 * If the input is a String, it will be added to a new List and returned.
	 *
	 * If the input is a Number, Boolean, or {@link Message}, it will be converted to String, added to a List, and returned.
	 *
	 * If the input is already a List, the input object will be returned as-is.
	 *
	 * If the input is an array of strings, they will be added to a new list and returned.
	 *
	 * If the input is an array of any other type of object, they will be converted to strings, added to a new list, and returned.
	 *
	 * If the input is any other kind of Collection, all elements will be added to a new List and returned.
	 *
	 * If the input is a {@link Stream}, all elements will be converted to Strings using {@link Utilities#safeString(Object)}, then added to a new List and returned.
	 *
	 * All other values result in an empty list.
	 *
	 * This method never returns null.
	 *
	 * Unlike {@link Util#otol(Object)}, this method does not split strings as CSVs.
	 *
	 * It's not my problem if your existing lists have something other than Strings in them.
	 *
	 * @param value The value to listify
	 * @return The resulting List
	 */
	@SuppressWarnings("unchecked")
	public static List<String> safeListify(Object value) {
		if (value instanceof String) {
			List<String> single = new ArrayList<>();
			single.add((String) value);
			return single;
		} else if (value instanceof Number || value instanceof Boolean) {
			List<String> single = new ArrayList<>();
			single.add(String.valueOf(value));
			return single;
		} else if (value instanceof Message) {
			List<String> single = new ArrayList<>();
			single.add(((Message) value).getLocalizedMessage());
			return single;
		} else if (value instanceof String[]) {
			String[] strings = (String[])value;
			return new ArrayList<>(Arrays.asList(strings));
		} else if (value instanceof Object[]) {
			Object[] objs = (Object[])value;
			return Arrays.stream(objs).map(Utilities::safeString).collect(Collectors.toCollection(ArrayList::new));
		} else if (value instanceof List) {
			return (List<String>)value;
		} else if (value instanceof Collection) {
			return new ArrayList<>((Collection<String>)value);
		} else if (value instanceof Stream) {
			return ((Stream<?>)value).map(Utilities::safeString).collect(Collectors.toList());
		}
		return new ArrayList<>();
	}

	/**
	 * Returns a Map cast to the given generic types if and only if it passes the check in
	 * {@link #mapConformsToType(Map, Class, Class)} for the same type parameters.
	 *
	 * If the input is not a Map or if the key/value types do not conform to the expected
	 * types, this method returns null.
	 *
	 * @param input The input map
	 * @param keyType The key type
	 * @param valueType The value type
	 * @param <S> The resulting key type
	 * @param <T> The resulting value type
	 * @return The resulting Map
	 */
	@SuppressWarnings("unchecked")
    public static <S, T> Map<S, T> safeMapCast(Object input, Class<S> keyType, Class<T> valueType) {
    	if (!(input instanceof Map)) {
    		return null;
		}
    	boolean conforms = mapConformsToType((Map<?, ?>) input, keyType, valueType);
    	if (conforms) {
    		return (Map<S, T>)input;
		} else {
    		return null;
		}
	}
	
	/**
	 * Returns the size of the input array, returning 0 if the array is null
	 * @param input The array to get the size of
	 * @param <T> The type of the array (just for compiler friendliness)
	 * @return The size of the array
	 */
	public static <T> int safeSize(T[] input) {
		if (input == null) {
			return 0;
		}
		return input.length;
	}

	/**
	 * Returns the size of the input collection, returning 0 if the collection is null
	 * @param input The collection to get the size of
	 * @return The size of the collection
	 */
	public static int safeSize(Collection<?> input) {
		if (input == null) {
			return 0;
		}
		return input.size();
	}

	/**
	 * Returns the length of the input string, returning 0 if the string is null
	 * @param input The string to get the length of
	 * @return The size of the string
	 */
	public static int safeSize(String input) {
		if (input == null) {
			return 0;
		}
		return input.length();
	}

	/**
	 * Returns a stream for the given array if it's not null, or an empty stream if it is
	 * @param array The array
	 * @param <T> The type of the array
	 * @return A stream from the array, or an empty stream
	 */
	public static <T> Stream<T> safeStream(T[] array) {
		if (array == null || array.length == 0) {
			return Stream.empty();
		}
		return Arrays.stream(array);
	}

	/**
	 * Returns a stream for the given list if it's not null, or an empty stream if it is
	 * @param list The list
	 * @param <T> The type of the list
	 * @return A stream from the list, or an empty stream
	 */
	public static <T> Stream<T> safeStream(List<T> list) {
		if (list == null) {
			return Stream.empty();
		}
		return list.stream();
	}

	/**
	 * Returns a stream for the given set if it's not null, or an empty stream if it is
	 * @param set The list
	 * @param <T> The type of the list
	 * @return A stream from the list, or an empty stream
	 */
	public static <T> Stream<T> safeStream(Set<T> set) {
		if (set == null) {
			return Stream.empty();
		}
		return set.stream();
	}

	/**
	 * Returns the given value as a "safe string". If the value is null, it will be returned as an empty string. If the value is already a String, it will be returned as-is. If the value is anything else, it will be passed through {@link String#valueOf(Object)}.
	 *
	 * If the input is an array, it will be converted to a temporary list for String.valueOf() output.
	 *
	 * The output will never be null.
	 *
	 * @param whatever The thing to return
	 * @return The string
	 */
	public static String safeString(Object whatever) {
		if (whatever == null) {
			return "";
		}
		if (whatever instanceof String) {
			return (String)whatever;
		}
		// If we've got an array, make it a list for a nicer toString()
		if (whatever.getClass().isArray()) {
			Object[] array = (Object[])whatever;
			whatever = Arrays.stream(array).collect(Collectors.toCollection(ArrayList::new));
		}
		return String.valueOf(whatever);
	}

	/**
	 * Performs a safe subscript operation against the given array.
	 *
	 * If the array is null, or if the index is out of bounds, this method returns null instead of throwing an exception.
	 *
	 * @param list The array to get the value from
	 * @param index The index from which to get the value.
	 * @param <T> The expected return type
	 * @return The value at the given index in the array, or null
	 */
	public static <T, S extends T> T safeSubscript(S[] list, int index) {
		return safeSubscript(list, index, null);
	}

	/**
	 * Performs a safe subscript operation against the given array.
	 *
	 * If the array is null, or if the index is out of bounds, this method returns the default instead of throwing an exception.
	 *
	 * @param list The array to get the value from
	 * @param index The index from which to get the value.
	 * @param <T> The expected return type
	 * @return The value at the given index in the array, or null
	 */
	public static <T, S extends T> T safeSubscript(S[] list, int index, T defaultValue) {
		if (list == null) {
			return defaultValue;
		}
		if (index >= list.length || index < 0) {
			return defaultValue;
		}
		return list[index];
	}

	/**
	 * Performs a safe subscript operation against the given {@link List}.
	 *
	 * If the list is null, or if the index is out of bounds, this method returns null instead of throwing an exception.
	 *
	 * Equivalent to safeSubscript(list, index, null).
	 *
	 * @param list The List to get the value from
	 * @param index The index from which to get the value.
	 * @param <T> The expected return type
	 * @return The value at the given index in the List, or null
	 */
	public static <T, S extends T> T safeSubscript(List<S> list, int index) {
		return safeSubscript(list, index, null);
	}

	/**
	 * Performs a safe subscript operation against the given {@link List}.
	 *
	 * If the list is null, or if the index is out of bounds, this method returns the given default object instead of throwing an exception.
	 *
	 * @param list The List to get the value from
	 * @param index The index from which to get the value.
	 * @param defaultObject The default object to return in null or out-of-bounds cases
	 * @param <S> The actual type of the list, which must be a subclass of T
	 * @param <T> The expected return type
	 * @return The value at the given index in the List, or null
	 */
	public static <T, S extends T> T safeSubscript(List<S> list, int index, T defaultObject) {
		if (list == null) {
			return defaultObject;
		}
		if (index >= list.size() || index < 0) {
			return defaultObject;
		}
		return list.get(index);
	}

	/**
	 * Safely substring the given input String, accounting for odd index situations.
	 * This method should never throw a StringIndexOutOfBounds exception.
	 *
	 * Negative values will be interpreted as distance from the end, like Python.
	 *
	 * If the start index is higher than the end index, or if the start index is
	 * higher than the string length, the substring is not defined and an empty
	 * string will be returned.
	 *
	 * If the end index is higher than the length of the string, the whole
	 * remaining string after the start index will be returned.
	 *
	 * @param input The input string to substring
	 * @param start The start index
	 * @param end The end index
	 * @return The substring
	 */
	public static String safeSubstring(String input, int start, int end) {
		if (input == null) {
			return null;
		}

		if (end < 0) {
			end = input.length() + end;
		}

		if (start < 0) {
			start = input.length() + start;
		}

		if (end > input.length()) {
			end = input.length();
		}

		if (start > end) {
			return "";
		}

		if (start < 0) {
			start = 0;
		}
		if (end < 0) {
			end = 0;
		}

		return input.substring(start, end);
	}

	/**
	 * Returns a trimmed version of the input string, returning an empty
	 * string if it is null.
	 * @param input The input string to trim
	 * @return A non-null trimmed copy of the input string
	 */
	public static String safeTrim(String input) {
		if (input == null) {
			return "";
		}
		return input.trim();
	}

	/**
	 * Sets the given attribute on the given object, if it supports attributes. The
	 * attributes on some object types may be called other things like arguments.
	 *
	 * @param source The source object, which may implement an Attributes container method
	 */
	public static void setAttribute(Object source, String attributeName, Object value) {
		/*
		 * In Java 8+, using instanceof is about as fast as using == and way faster than
		 * reflection and possibly throwing an exception, so this is the best way to get
		 * attributes if we aren't sure of the type of the object.
		 *
		 * Most classes have their attributes exposed via getAttributes, but some have
		 * them exposed via something like getArguments. This method will take care of
		 * the difference for you.
		 *
		 * Did I miss any? Probably. But this is a lot.
		 */
		Objects.requireNonNull(source, "You cannot set an attribute on a null object");
		Objects.requireNonNull(attributeName, "Attribute names must not be null");
		Attributes<String, Object> existing = null;
		if (!(source instanceof Custom || source instanceof Configuration || source instanceof Identity || source instanceof Link || source instanceof Bundle || source instanceof Application || source instanceof TaskDefinition || source instanceof TaskItem)) {
			existing = getAttributes(source);
			if (existing == null) {
				existing = new Attributes<>();
			}
			if (value == null) {
				existing.remove(attributeName);
			} else {
				existing.put(attributeName, value);
			}
		}
		if (source instanceof Identity) {
			// This does special stuff
			((Identity) source).setAttribute(attributeName, value);
		} else if (source instanceof Link) {
			((Link) source).setAttribute(attributeName, value);
		} else if (source instanceof Bundle) {
			((Bundle) source).setAttribute(attributeName, value);
		} else if (source instanceof Custom) {
			// Custom objects are gross
			((Custom) source).put(attributeName, value);
		} else if (source instanceof Configuration) {
			((Configuration) source).put(attributeName, value);
		} else if (source instanceof Application) {
			((Application) source).setAttribute(attributeName, value);
		} else if (source instanceof CertificationItem) {
			// This one returns a Map for some reason
			((CertificationItem) source).setAttributes(existing);
		} else if (source instanceof CertificationEntity) {
			((CertificationEntity) source).setAttributes(existing);
		} else if (source instanceof Certification) {
			((Certification) source).setAttributes(existing);
		} else if (source instanceof CertificationDefinition) {
			((CertificationDefinition) source).setAttributes(existing);
		} else if (source instanceof TaskDefinition) {
			((TaskDefinition) source).setArgument(attributeName, value);
		} else if (source instanceof TaskItem) {
			((TaskItem) source).setAttribute(attributeName, value);
		} else if (source instanceof ManagedAttribute) {
			((ManagedAttribute) source).setAttributes(existing);
		} else if (source instanceof Form) {
			((Form) source).setAttributes(existing);
		} else if (source instanceof IdentityRequest) {
			((IdentityRequest) source).setAttributes(existing);
		} else if (source instanceof IdentitySnapshot) {
			((IdentitySnapshot) source).setAttributes(existing);
		} else if (source instanceof ResourceObject) {
			((ResourceObject) source).setAttributes(existing);
		} else if (source instanceof Field) {
			((Field) source).setAttributes(existing);
		} else if (source instanceof ProvisioningPlan) {
			((ProvisioningPlan) source).setArguments(existing);
		} else if (source instanceof IntegrationConfig) {
			((IntegrationConfig) source).setAttributes(existing);
		} else if (source instanceof ProvisioningProject) {
			((ProvisioningProject) source).setAttributes(existing);
		} else if (source instanceof ProvisioningTransaction) {
			((ProvisioningTransaction) source).setAttributes(existing);
		} else if (source instanceof ProvisioningPlan.AbstractRequest) {
			((ProvisioningPlan.AbstractRequest) source).setArguments(existing);
		} else if (source instanceof Rule) {
			((Rule) source).setAttributes(existing);
		} else if (source instanceof WorkItem) {
			((WorkItem) source).setAttributes(existing);
		} else if (source instanceof RpcRequest) {
			((RpcRequest) source).setArguments(existing);
		} else if (source instanceof ApprovalItem) {
			((ApprovalItem) source).setAttributes(existing);
		} else {
			throw new UnsupportedOperationException("This method does not support objects of type " + source.getClass().getName());
		}
	}

	/**
	 * Returns a new *modifiable* set with the objects specified added to it.
	 * A new set will be returned on each invocation. This method will never
	 * return null.
	 *
	 * @param objects The objects to add to the set
	 * @param <T> The type of each item
	 * @return A set containing each of the items
	 */
	@SafeVarargs
	public static <T> Set<T> setOf(T... objects) {
		Set<T> set = new HashSet<>();
		if (objects != null) {
			Collections.addAll(set, objects);
		}
		return set;
	}

	/**
	 * Adds the given key and value to the Map if no existing value for the key is
	 * present. The Map will be synchronized so that only one thread is guaranteed
	 * to be able to insert the initial value.
	 *
	 * If possible, you should use a {@link java.util.concurrent.ConcurrentMap}, which
	 * already handles this situation with greater finesse.
	 *
	 * @param target The target Map to which the value should be inserted if missing
	 * @param key The key to insert
	 * @param value A supplier for the value to insert
	 * @param <S> The key type
	 * @param <T> The value type
	 */
	public static <S, T> void synchronizedPutIfAbsent(final Map<S, T> target, final S key, final Supplier<T> value) {
		Objects.requireNonNull(target, "The Map passed to synchronizedPutIfAbsent must not be null");
		if (!target.containsKey(key)) {
			synchronized(target) {
				if (!target.containsKey(key)) {
					T valueObj = value.get();
					target.put(key, valueObj);
				}
			}
		}
	}

	/**
	 * Returns the current time in a standard format
	 * @return The current time
	 */
	public static String timestamp() {
		SimpleDateFormat formatter = new SimpleDateFormat(CommonConstants.STANDARD_TIMESTAMP);
		formatter.setTimeZone(TimeZone.getDefault());
		return formatter.format(new Date());
	}

	/**
	 * Translates the input to XML, if a serializer is registered for it. In
	 * general, this can be anything in 'sailpoint.object', a Map, a List, a
	 * String, or other primitives.
	 *
	 * @param input The object to serialize
	 * @return the output XML
	 * @throws ConfigurationException if the object type cannot be serialized
	 */
	public static String toXml(Object input) throws ConfigurationException {
		if (input == null) {
			return null;
		}

		XMLObjectFactory xmlObjectFactory = XMLObjectFactory.getInstance();
		return xmlObjectFactory.toXml(input);
	}

	/**
	 * Attempts to capture the user's time zone information from the current JSF context / HTTP session, if one is available. The time zone and locale will be captured to the user's UIPreferences as 'mostRecentTimezone' and 'mostRecentLocale'.
	 *
	 * If a session is not available, this method does nothing.
	 *
	 * The JSF session is available in a subset of rule contexts, most notably the QuickLink textScript context, which runs on each load of the user's home.jsf page.
	 *
	 * @param context The current IIQ context
	 * @param currentUser The user to modify with the detected information
	 */
	public static void tryCaptureLocationInfo(SailPointContext context, Identity currentUser) {
		Objects.requireNonNull(currentUser, "A non-null Identity must be provided");
		TimeZone tz = null;
		Locale locale = null;
		FacesContext fc = FacesContext.getCurrentInstance();
		if (fc != null) {
			if (fc.getViewRoot() != null) {
				locale = fc.getViewRoot().getLocale();
			}
			HttpSession session = (HttpSession)fc.getExternalContext().getSession(true);
			if (session != null) {
				tz = (TimeZone)session.getAttribute("timeZone");
			}
		}
		boolean save = false;
		if (tz != null) {
			save = true;
			currentUser.setUIPreference(MOST_RECENT_TIMEZONE, tz.getID());
		}
		if (locale != null) {
			currentUser.setUIPreference(MOST_RECENT_LOCALE, locale.toLanguageTag());
			save = true;
		}
		if (save) {
			try {
				context.saveObject(currentUser);
				context.saveObject(currentUser.getUIPreferences());
				context.commitTransaction();
			} catch(Exception e) {
				/* Ignore this */
			}
		}
	}
	
	/**
	 * Attempts to capture the user's time zone information from the user context. This could be used via a web services call where the {@link BaseResource} class is a {@link UserContext}.
	 *
	 * @param context The current IIQ context
	 * @param currentUser The current user context
	 * @throws GeneralException if there is no currently logged in user
	 */
	public static void tryCaptureLocationInfo(SailPointContext context, UserContext currentUser) throws GeneralException {
		TimeZone tz = currentUser.getUserTimeZone();
		Locale locale = currentUser.getLocale();
		boolean save = false;
		if (tz != null) {
			save = true;
			currentUser.getLoggedInUser().setUIPreference(MOST_RECENT_TIMEZONE, tz.getID());
		}
		if (locale != null) {
			currentUser.getLoggedInUser().setUIPreference(MOST_RECENT_LOCALE, locale.toLanguageTag());
			save = true;
		}
		if (save) {
			try {
				context.saveObject(currentUser.getLoggedInUser());
				context.saveObject(currentUser.getLoggedInUser().getUIPreferences());
				context.commitTransaction();
			} catch(Exception e) {
				/* Ignore this */
			}
		}
	}
	
	/**
	 * Attempts to get the SPKeyStore, a class which is for some reason protected.
	 * @return The keystore, if we could get it
	 * @throws GeneralException If the keystore could not be retrieved
	 */
	public static SPKeyStore tryGetKeystore() throws GeneralException {
		SPKeyStore result;
		try {
			Method getMethod = SPKeyStore.class.getDeclaredMethod("getInstance");
			try {
				getMethod.setAccessible(true);
				result = (SPKeyStore) getMethod.invoke(null);
			} finally {
				getMethod.setAccessible(false);
			}
		} catch(NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
			throw new GeneralException(e);
		}
		return result;
	}

	/**
	 * Renders the given template using Velocity, passing the given arguments to the renderer.
	 * Velocity will be initialized on the first invocation of this method.
	 *
	 * TODO invoke the Sailpoint utility in versions over 8.2
	 *
	 * @param template The VTL template string
	 * @param args The arguments to pass to the template renderer
	 * @return The rendered string
	 * @throws IOException if any Velocity failures occur
	 */
	public static String velocityRender(String template, Map<String, ?> args) throws IOException {
		if (!VELOCITY_INITIALIZED.get()) {
			synchronized (VELOCITY_INITIALIZED) {
				if (!VELOCITY_INITIALIZED.get()) {
					Velocity.setProperty("runtime.log.logsystem.class", "org.apache.velocity.runtime.log.AvalonLogChute,org.apache.velocity.runtime.log.Log4JLogChute,org.apache.velocity.runtime.log.JdkLogChute");
					Velocity.setProperty("ISO-8859-1", "UTF-8");
					Velocity.setProperty("output.encoding", "UTF-8");
					Velocity.setProperty("resource.loader", "classpath");
					Velocity.setProperty("classpath.resource.loader.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
					Velocity.init();
					VELOCITY_INITIALIZED.set(true);
				}
			}
		}
		Map<String, Object> params = new HashMap<>(args);
		params.put("escapeTools", new VelocityEscapeTools());
		params.put("spTools", new VelocityUtil.SailPointVelocityTools(Locale.getDefault(), TimeZone.getDefault()));
		VelocityContext velocityContext = new VelocityContext(params);

		try (StringWriter writer = new StringWriter()) {
			String tag = "anonymous";
			boolean success = Velocity.evaluate(velocityContext, writer, tag, template);

			if (!success) {
				throw new IOException("Velocity rendering did not succeed");
			}

			writer.flush();

			return writer.toString();
		}
	}

	/**
	 * Transforms a wildcard like 'a*' to a Filter. This method cannot support mid-string
	 * cases like 'a*a' at this time.
	 *
	 * @param property The property being filtered
	 * @param input The input (e.g., 'a*'
	 * @param caseInsensitive Whether the Filter should be case-insensitive
	 * @return A filter matching the given wildcard
	 */
	public static Filter wildcardToFilter(String property, String input, boolean caseInsensitive) {
		Filter output = null;

		// Simple cases
		if (input.replace("*", "").isEmpty()) {
			output = Filter.notnull(property);
		} else if (input.startsWith("*") && !input.substring(1).contains("*")) {
			output = Filter.like(property, input.substring(1), Filter.MatchMode.END);
		} else if (input.endsWith("*") && !input.substring(0, input.length() - 1).contains("*")) {
			output = Filter.like(property, input.substring(0, input.length() - 1), Filter.MatchMode.START);
		} else if (input.length() > 2 && input.startsWith("*") && input.endsWith("*") && !input.substring(1, input.length() - 1).contains("*")) {
			output = Filter.like(property, input.substring(1, input.length() - 1), Filter.MatchMode.ANYWHERE);
		} else {
			output = Filter.like(property, input, Filter.MatchMode.ANYWHERE);
		}

		// TODO complex cases like `*a*b*`

		if (output instanceof Filter.LeafFilter && caseInsensitive) {
			output = Filter.ignoreCase(output);
		}

		return output;
	}

	/**
	 * Uses the valueProducer to extract the value from the input object if it is not
	 * null, otherwise returns the default value.
	 *
	 * @param maybeNull An object which may be null
	 * @param defaultValue The value to return if the object is null
	 * @param valueProducer The generator of the value to return if the value is not nothing
	 * @param <T> The return type
	 * @return The result of the value producer, or the default
	 */
	public static <T> T withDefault(Object maybeNull, T defaultValue, Functions.FunctionWithError<Object, T> valueProducer) {
		if (maybeNull != null) {
			try {
				return valueProducer.applyWithError(maybeNull);
			} catch(Error e) {
				throw e;
			} catch (Throwable throwable) {
				logger.debug("Caught an error in withDefault", throwable);
			}
		}
		return defaultValue;
	}

	/**
	 * Safely handles the given iterator by passing it to the Consumer and, regardless
	 * of outcome, by flushing it when the Consumer returns.
	 *
	 * @param iterator The iterator to process
	 * @param iteratorConsumer The iterator consumer, which will be invoked with the iterator
	 * @param <T> The iterator type
	 * @throws GeneralException if any failures occur
	 */
	public static <T> void withIterator(Iterator<T> iterator, Functions.ConsumerWithError<Iterator<T>> iteratorConsumer) throws GeneralException {
		withIterator(() -> iterator, iteratorConsumer);
	}

	/**
	 * Safely handles the given iterator by passing it to the Consumer and, regardless
	 * of outcome, by flushing it when the Consumer returns.
	 *
	 * @param iteratorSupplier The iterator supplier, which will be invoked once
	 * @param iteratorConsumer The iterator consumer, which will be invoked with the iterator
	 * @param <T> The iterator type
	 * @throws GeneralException if any failures occur
	 */
	public static <T> void withIterator(Functions.SupplierWithError<Iterator<T>> iteratorSupplier, Functions.ConsumerWithError<Iterator<T>> iteratorConsumer) throws GeneralException {
		try {
			Iterator<T> iterator = iteratorSupplier.getWithError();
			if (iterator != null) {
				try {
					iteratorConsumer.acceptWithError(iterator);
				} finally {
					Util.flushIterator(iterator);
				}
			}
		} catch(GeneralException | RuntimeException | Error e) {
			throw e;
		} catch(Throwable t) {
			throw new GeneralException(t);
		}
	}

	/**
	 * Obtains the lock, then executes the callback
	 * @param lock The lock to lock before doing the execution
	 * @param callback The callback to invoke after locking
	 * @throws GeneralException if any failures occur or if the lock is interrupted
	 */
	public static void withJavaLock(Lock lock, Callable<?> callback) throws GeneralException {
		try {
			lock.lockInterruptibly();
			try {
				callback.call();
			} catch(InterruptedException | GeneralException e) {
				throw e;
			} catch (Exception e) {
				throw new GeneralException(e);
			} finally {
				lock.unlock();
			}
		} catch(InterruptedException e) {
			throw new GeneralException(e);
		}
	}

	/**
	 * Obtains the lock, then executes the callback
	 * @param lock The lock to lock before doing the execution
	 * @param callback The callback to invoke after locking
	 * @throws GeneralException if any failures occur or if the lock is interrupted
	 */
	public static void withJavaTimeoutLock(Lock lock, long timeoutMillis, Callable<?> callback) throws GeneralException {
		try {
			boolean locked = lock.tryLock(timeoutMillis, TimeUnit.MILLISECONDS);
			if (!locked) {
				throw new GeneralException("Unable to obtain the lock within timeout period " + timeoutMillis + " ms");
			}
			try {
				callback.call();
			} catch(InterruptedException | GeneralException e) {
				throw e;
			} catch (Exception e) {
				throw new GeneralException(e);
			} finally {
				lock.unlock();
			}
		} catch(InterruptedException e) {
			throw new GeneralException(e);
		}
	}

	/**
	 * Creates a new database connection using the context provided, sets its auto-commit
	 * flag to false, then passes it to the consumer provided. The consumer is responsible
	 * for committing.
	 *
	 * @param context The context to produce the connection
	 * @param consumer The consumer lambda or class to handle the connection
	 * @throws GeneralException on failures
	 */
	public static void withNoCommitConnection(SailPointContext context, Functions.ConnectionHandler consumer) throws GeneralException {
		try (Connection connection = ContextConnectionWrapper.getConnection(context)) {
			try {
				connection.setAutoCommit(false);
				try {
					consumer.accept(connection);
				} catch (GeneralException | SQLException | RuntimeException | Error e) {
					Quietly.rollback(connection);
					throw e;
				} catch (Throwable t) {
					Quietly.rollback(connection);
					throw new GeneralException(t);
				}
			} finally{
				connection.setAutoCommit(true);
			}
		} catch(SQLException e) {
			throw new GeneralException(e);
		}
	}

	/**
	 * Obtains a persistent lock on the object (sets lock = 1 in the DB), then executes the callback.
	 * The lock will have a duration of 1 minute, so you should ensure that your operation is
	 * relatively short.
	 *
	 * @param context The Sailpoint context
	 * @param sailpointClass The Sailpoint class to lock
	 * @param id The ID of the Sailpoint object
	 * @param timeoutSeconds How long to wait, in seconds, before throwing {@link ObjectAlreadyLockedException}
	 * @param callback The callback to invoke after locking which will be called with the locked object
	 * @throws GeneralException if any failures occur or if the lock is interrupted
	 */
	public static <V extends SailPointObject> void withPersistentLock(SailPointContext context, Class<V> sailpointClass, String id, int timeoutSeconds, Functions.ConsumerWithError<V> callback) throws GeneralException {
		PersistenceManager.LockParameters lockParameters = PersistenceManager.LockParameters.createById(id, PersistenceManager.LOCK_TYPE_PERSISTENT);
		lockParameters.setLockTimeout(timeoutSeconds);
		lockParameters.setLockDuration(1);

		V object = ObjectUtil.lockObject(context, sailpointClass, lockParameters);
		try {
			callback.acceptWithError(object);
		} catch(Error | GeneralException e) {
			throw e;
		} catch(Throwable t) {
			throw new GeneralException(t);
		} finally {
			ObjectUtil.unlockObject(context, object, PersistenceManager.LOCK_TYPE_PERSISTENT);
		}
	}

	/**
	 * Begins a private, temporary SailpointContext session and then calls the given
	 * Beanshell method within the previous Beanshell environment.
	 *
	 * @param bshThis The 'this' object from the current Beanshell script
	 * @param methodName The callback method name to invoke after creating the private context
	 * @throws GeneralException on any Beanshell failures
	 */
	public static void withPrivateContext(bsh.This bshThis, String methodName) throws GeneralException {
		try {
			SailPointContext previousContext = SailPointFactory.pushContext();
			SailPointContext privateContext = SailPointFactory.getCurrentContext();
			try {
				Object[] args = new Object[] { privateContext };
				bshThis.invokeMethod(methodName, args);
			} finally {
				if (privateContext != null) {
					SailPointFactory.releaseContext(privateContext);
				}
				if (previousContext != null) {
					SailPointFactory.setContext(previousContext);
				}
			}
		} catch(Throwable t) {
			throw new GeneralException(t);
		}
	}

	/**
	 * Begins a private, temporary SailpointContext session and then calls the given
	 * Beanshell method within the previous Beanshell environment. The 'params' will
	 * be appended to the method call. The first argument to the Beanshell method will
	 * be the SailPointContext, and the remaining arguments will be the parameters.
	 *
	 * @param bshThis The 'this' object from the current Beanshell script
	 * @param methodName The callback method name to invoke after creating the private context
	 * @param params Any other parameters to pass to the Beanshell method
	 * @throws GeneralException on any Beanshell failures
	 */
	public static void withPrivateContext(bsh.This bshThis, String methodName, List<Object> params) throws GeneralException {
		try {
			SailPointContext previousContext = SailPointFactory.pushContext();
			SailPointContext privateContext = SailPointFactory.getCurrentContext();
			try {
				Object[] args = new Object[1 + params.size()];
				args[0] = privateContext;
				for(int i = 0; i < params.size(); i++) {
					args[i + 1] = params.get(i);
				}

				bshThis.invokeMethod(methodName, args);
			} finally {
				if (privateContext != null) {
					SailPointFactory.releaseContext(privateContext);
				}
				if (previousContext != null) {
					SailPointFactory.setContext(previousContext);
				}
			}
		} catch(Throwable t) {
			throw new GeneralException(t);
		}
	}

	/**
	 * Begins a private, temporary SailpointContext session and then invokes the given
	 * Consumer as a callback. The Consumer's input will be the temporary context.
	 *
	 * @param runner The runner
	 * @throws GeneralException if anything fails at any point
	 */
	public static void withPrivateContext(Functions.ConsumerWithError<SailPointContext> runner) throws GeneralException {
		try {
			SailPointContext previousContext = SailPointFactory.pushContext();
			SailPointContext privateContext = SailPointFactory.getCurrentContext();
			try {
				runner.acceptWithError(privateContext);
			} finally {
				if (privateContext != null) {
					SailPointFactory.releaseContext(privateContext);
				}
				if (previousContext != null) {
					SailPointFactory.setContext(previousContext);
				}
			}
		} catch(GeneralException | RuntimeException | Error e) {
			throw e;
		} catch(Throwable t) {
			throw new GeneralException(t);
		}
	}

	/**
	 * Begins a private, temporary SailpointContext session and then invokes the given
	 * Function as a callback. The Function's input will be the temporary context.
	 * The result of the Function will be returned.
	 *
	 * @param runner The runner
	 * @throws GeneralException if anything fails at any point
	 */
	public static <T> T withPrivateContext(Functions.FunctionWithError<SailPointContext, T> runner) throws GeneralException {
		try {
			SailPointContext previousContext = SailPointFactory.pushContext();
			SailPointContext privateContext = SailPointFactory.getCurrentContext();
			try {
				return runner.applyWithError(privateContext);
			} finally {
				if (privateContext != null) {
					SailPointFactory.releaseContext(privateContext);
				}
				if (previousContext != null) {
					SailPointFactory.setContext(previousContext);
				}
			}
		} catch(GeneralException e) {
			throw e;
		} catch(Throwable t) {
			throw new GeneralException(t);
		}
	}

	/**
	 * Begins a private, temporary SailpointContext session and then calls the given
	 * Beanshell method within the previous Beanshell environment. Each item in the
	 * 'params' list will be passed individually, so the method is expected to take
	 * two arguments: the context and the Object.
	 *
	 * @param bshThis The 'this' object from the current Beanshell script
	 * @param methodName The callback method name to invoke after creating the private context
	 * @param params Any other parameters to pass to the Beanshell method
	 * @throws GeneralException on any Beanshell failures
	 */
	public static void withPrivateContextIterate(bsh.This bshThis, String methodName, Collection<Object> params) throws GeneralException {
		try {
			SailPointContext previousContext = SailPointFactory.pushContext();
			SailPointContext privateContext = SailPointFactory.getCurrentContext();
			try {
				for (Object param : params) {
					Object[] args = new Object[2];
					args[0] = privateContext;
					args[1] = param;

					bshThis.invokeMethod(methodName, args);
				}
			} finally {
				if (privateContext != null) {
					SailPointFactory.releaseContext(privateContext);
				}
				if (previousContext != null) {
					SailPointFactory.setContext(previousContext);
				}
			}
		} catch(Throwable t) {
			throw new GeneralException(t);
		}
	}

	/**
	 * Obtains a transaction lock on the object (selects it 'for update'), then executes the callback.
	 * @param context The Sailpoint context
	 * @param sailpointClass The Sailpoint class to lock
	 * @param id The ID of the Sailpoint object
	 * @param timeoutSeconds How long to wait, in seconds, before throwing {@link ObjectAlreadyLockedException}
	 * @param callback The callback to invoke after locking
	 * @throws GeneralException if any failures occur or if the lock is interrupted
	 */
	public static <V extends SailPointObject> void withTransactionLock(SailPointContext context, Class<V> sailpointClass, String id, int timeoutSeconds, Functions.ConsumerWithError<V> callback) throws GeneralException {
		PersistenceManager.LockParameters lockParameters = PersistenceManager.LockParameters.createById(id, PersistenceManager.LOCK_TYPE_TRANSACTION);
		lockParameters.setLockTimeout(timeoutSeconds);
		V object = ObjectUtil.lockObject(context, sailpointClass, lockParameters);
		try {
			callback.acceptWithError(object);
		} catch(Error | GeneralException e) {
			throw e;
		} catch(Throwable t) {
			throw new GeneralException(t);
		} finally {
			ObjectUtil.unlockObject(context, object, PersistenceManager.LOCK_TYPE_TRANSACTION);
		}
	}

}
