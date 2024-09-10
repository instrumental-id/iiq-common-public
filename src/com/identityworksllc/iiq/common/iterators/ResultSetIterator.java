package com.identityworksllc.iiq.common.iterators;

import com.identityworksllc.iiq.common.Utilities;
import org.apache.commons.collections4.map.ListOrderedMap;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.ReportColumnConfig;
import sailpoint.object.Rule;
import sailpoint.object.SailPointObject;
import sailpoint.object.Script;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;

import java.sql.Blob;
import java.sql.Clob;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * An adapter that translates a {@link ResultSet} to IIQ Reporting's preferred {@link Iterator}<Object[]>.
 * This class also exposes several utility methods that can be used to implement standard
 * ResultSet behavior outside of this iterator context.
 *
 * The constructor requires that you provide a list of column detail objects. These can be:
 *
 * - A String, which will be parsed as a {@link ColumnToken} (if it contains a ':') or as a simple column name.
 *
 * - An instance of {@link ColumnConfig}, which can be used to specify column data in greater detail.
 *
 * - An instance of Sailpoint's {@link ReportColumnConfig} class, which contains vastly more options for reporting purposes than are used here.
 *
 * Column tokens are case-sensitive. Column names are not.
 *
 * After obtaining an instance of this class, the query itself will be invoked on the
 * first call to {@link #hasNext()}.
 *
 * You may obtain the next row of results as an <code>Object[]</code> using {@link #next()}
 * or as a <code>Map</code> (where the key is the column's field name) using {@link #nextRow()}.
 */
@SuppressWarnings("unused")
public final class ResultSetIterator implements Iterator<Object[]>, AutoCloseable {

	/**
	 * An interface to use for custom type handlers
	 */
	@FunctionalInterface
	public interface CustomTypeHandler {
		/**
		 * Performs some action to extract the value of the given column from
		 * the current row of the ResultSet, transforming it to the appropriate type.
		 * You should not invoke {@link ResultSet#next()} or any other method that
		 * will change the ResultSet's cursor location.
		 *
		 * @param resultSet The result set, already on the correct row
		 * @param columnName The column name
		 * @return The result of handling the data
		 * @throws SQLException on error
		 */
		Object handle(ResultSet resultSet, String columnName) throws SQLException;
	}

	/**
	 * The output of {@link #extractColumnValue(ResultSet, String, Integer)}, containing
	 * details about the column used as well as the actual value. This class exists mainly
	 * to simplify method signatures.
	 */
	public static final class ColumnOutput {
		/**
		 * The ColumnToken, which can produce the column name and the type token, among
		 * other things.
		 */
		private ColumnToken columnToken;

		/**
		 * The actual value returned from the ResultSet
		 */
		private Object value;

		/**
		 * The column name without any type tokens
		 *
		 * @return The column name
		 */
		public String getColumnName() {
			return columnToken.getBaseColumnName();
		}

		/**
		 * Retrieves the ColumnToken object
		 * @return The column token object
		 */
		public ColumnToken getColumnToken() {
			return columnToken;
		}

		/**
		 * The derived value token, which can be passed as-is to deriveTypedValue
		 *
		 * @return The derived value token
		 */
		public String getDerivedType() {
			return columnToken.getTypeToken();
		}

		/**
		 * The value of the column
		 *
		 * @return the value of the column
		 */
		public Object getValue() {
			return value;
		}
	}


	/**
	 * The logger for this class
	 */
	private static final Log log = LogFactory.getLog(ResultSetIterator.class);

	/**
	 * Type handlers
	 */
	private static final ConcurrentHashMap<Integer, CustomTypeHandler> typeHandlers = new ConcurrentHashMap<>();

	/**
	 * API method to add a new type handler, where the type is not handled out of box.
	 *
	 * @param type    The type integer (from, e,g. {@link Types}.
	 * @param handler The handler to execute
	 */
	public static void addTypeHandler(int type, CustomTypeHandler handler) {
		typeHandlers.put(type, handler);
	}

	/**
	 * TODO invert this so we can use multiple arguments
	 *
	 * A variant of deriveTypedValue that takes in a ColumnToken object rather than a
	 * String. This prevents us from having to repeatedly parse the string.
	 *
	 * @param context The current Sailpoint context context
	 * @param input The input from which to derive a proper value
	 * @param token The parsed column token
	 * @return The resulting derived value as described in {@link #deriveTypedValue(SailPointContext, Object, String)}
	 * @throws GeneralException if any failures occur
	 */
	public static Object deriveTypedValue(SailPointContext context, Object input, ColumnToken token) throws GeneralException {
		String derivedType = token.getTypeToken();

		String argument = token.getTypeParameter(0);

		String derivedToken = derivedType;

		if (Util.isNotNullOrEmpty(argument)) {
			derivedToken = derivedType + ColumnConfig.COLUMN_TYPE_SEPARATOR + argument;
		}

		return deriveTypedValue(context, input, derivedToken);
	}

	/**
	 * Static implementation of deriveTypedValue so it can be reused elsewhere.
	 *
	 * Derived types take the form [type]:[optional argument], similar to AngularJS
	 * filters. The valid types are 'xml', 'timestamp', boolean', 'object', or any
	 * SailPointObject subclass name (e.g., Identity, Link).
	 *
	 * Examples:
	 *
	 *   'col1':
	 *   Returns the value of result set field 'col1' as is
	 *
	 *   'col1:xml':
	 *   Parses the string value of result set column 'col1' as SailPoint XML and
	 *   returns the resulting object.
	 *
	 *   'col1:xml:firstname':
	 *   Parses the string value of result set column 'col1' as SailPoint XML, then
	 *   (if not null) returns the value of the property 'firstname' on that object.
	 *
	 * Types and arguments indicate:
	 *
	 *  'xml':
	 *      The value will be parsed as an XML string. If an argument is present,
	 *      Utilities.getProperty() will be used to dot-walk to that property of
	 *      the parsed object. Otherwise, the parsed object itself will be returned.
	 *
	 *  'timestamp':
	 *      The value will be parsed as a Long timestamp. The output will be
	 *      a Date object. If an argument is present, it will be interpreted
	 *      as a SimpleDateFormat format string and the output will be the
	 *      formatted String.
	 *
	 *  'boolean':
	 *      If the value is a String, it will be considered true if it is equal
	 *      to the strings 'true', '1', or 'yes'. If the value is a Number, it
	 *      will be considered true if it is non-zero. All other values are false.
	 *
	 *  'object':
	 *      The value will be handled as a Java object of an arbitrary type. The
	 *      argument will be used via Utilities.getProperty() to dot-walk to any
	 *      arbitrary property.
	 *
	 *  'script':
	 *      The value will be passed to the script provided as an argument, and
	 *      the output of the Script will become the new value.
	 *
	 *  'rule':
	 *      The value will be passed to the Rule specified by name in the argument,
	 *      and the output of the Rule will become the new value. This is more
	 *      efficient than the 'script' version because the parsed Beanshell can
	 *      be cached by the RuleRunner.
	 *
	 *  Any {@link SailPointObject} type:
	 *      If the value is a String, it will be used as an ID to look up the
	 *      actual object in the current context. The argument's function is the
	 *      same as the 'object' type.
	 *
	 * @param context The sailpoint context
	 * @param input The input object
	 * @param inputDerivedType The derived type to use
	 * @return The output object, which may be the same as the input
	 * @throws GeneralException if any derivation failures occur
	 */
	public static Object deriveTypedValue(SailPointContext context, Object input, String inputDerivedType) throws GeneralException {
		if (Util.isNullOrEmpty(inputDerivedType) || input == null) {
			return input;
		}

		String derivedType = inputDerivedType;
		String argument = null;
		if (derivedType.contains(ColumnConfig.COLUMN_TYPE_SEPARATOR)) {
			argument = derivedType.substring(derivedType.indexOf(ColumnConfig.COLUMN_TYPE_SEPARATOR) + 1);
			derivedType = derivedType.substring(0, derivedType.indexOf(ColumnConfig.COLUMN_TYPE_SEPARATOR));
		}

		Object output = input;

		if (derivedType.equals("xml") && output instanceof String) {
			output = AbstractXmlObject.parseXml(context, (String) output);
			if (Util.isNotNullOrEmpty(argument)) {
				output = Utilities.getProperty(output, argument, true);
			}
		} else if (derivedType.startsWith("timestamp")) {
			if (output instanceof String && !((String) output).isEmpty()) {
				output = new Date(Long.parseLong((String) output));
			} else if (output instanceof Number) {
				output = new Date(((Number) output).longValue());
			}
			if (Util.isNotNullOrEmpty(argument)) {
				if (!(output instanceof Date)) {
					throw new IllegalArgumentException("Derived type 'timestamp' can only be used on date and converted string types (value is type " + Utilities.safeClassName(output) + ")");
				}

				SimpleDateFormat formatter = new SimpleDateFormat(argument);
				output = formatter.format((Date) output);
			}
		} else if (derivedType.startsWith("boolean")) {
			if (output instanceof String) {
				output = Utilities.isFlagSet((String) output);
			} else if (output instanceof Number) {
				long longResult = ((Number) output).longValue();
				output = (longResult != 0);
			} else {
				output = false;
			}
		} else if (derivedType.equals("script")) {
			if (Util.isNullOrEmpty(argument)) {
				throw new IllegalArgumentException("Derived type 'script' must include one argument, the actual script text");
			}
			Script script = new Script();
			script.setSource(argument);

			Map<String, Object> scriptInput = new HashMap<>();
			scriptInput.put("value", output);

			output = context.runScript(script, scriptInput);
		} else if (derivedType.equals("rule")) {
			if (Util.isNullOrEmpty(argument)) {
				throw new IllegalArgumentException("Derived type 'rule' must include one argument, the name of an IIQ Rule");
			}

			Rule theRule = context.getObject(Rule.class, argument);
			if (theRule == null) {
				throw new IllegalArgumentException("Derived type 'rule' must specify a valid rule (rule '" + argument + "' does not exist)");
			}

			Map<String, Object> scriptInput = new HashMap<>();
			scriptInput.put("value", output);

			output = context.runRule(theRule, scriptInput);
		} else {
			if (!Util.nullSafeEq(derivedType, "object")) {
				if (output instanceof String) {
					@SuppressWarnings("unchecked")
					Class<? extends SailPointObject> spClass = ObjectUtil.getSailPointClass(derivedType);
					if (spClass != null) {
						output = context.getObject(spClass, (String) output);
					} else {
						// TODO custom type handlers
						throw new IllegalArgumentException("Unrecognized object type: " + derivedType);
					}
				}
			}

			if (Util.isNotNullOrEmpty(argument)) {
				output = Utilities.getProperty(output, argument, true);
			}
		}
		return output;
	}

	/**
	 * Processes the resulting column by extracting an appropriate object from the result set,
	 * returning the combination of the value and a derived type which can be passed to
	 * deriveTypedValue.
	 *
	 * @param resultSet Result set to read the column from
	 * @param columnToken The column token, potentially including derived types
	 * @param type The type of the column
	 * @return The derived type of the result, if any
	 * @throws SQLException on any database issues
	 * @throws GeneralException on any Sailpoint issues
	 */
	public static ColumnOutput extractColumnValue(ResultSet resultSet, final String columnToken, final Integer type) throws SQLException, GeneralException {
		ColumnOutput columnOutput = new ColumnOutput();
		ColumnToken token = new ColumnToken(columnToken);
		String derivedType = token.getTypeToken();
		String col = token.getBaseColumnName();

		columnOutput.columnToken = token;

		if (type == null) {
			columnOutput.value = null;
		} else {
			if (typeHandlers.containsKey(type)) {
				columnOutput.value = typeHandlers.get(type).handle(resultSet, col);
			} else {
				switch (type) {
					case Types.CLOB:
					case Types.NCLOB:
						Clob clob = resultSet.getClob(col);
						if (clob != null) {
							columnOutput.value = Util.readInputStream(clob.getAsciiStream());
						}
						break;
					case Types.BLOB:
					case Types.LONGVARBINARY:
						Blob blob = resultSet.getBlob(col);
						if (blob != null) {
							columnOutput.value = Util.readBinaryInputStream(blob.getBinaryStream());
						}
						break;
					case Types.BIT:
					case Types.BOOLEAN:
					case Types.INTEGER:
					case Types.BIGINT:
					case Types.TINYINT:
					case Types.SMALLINT:
					case Types.NUMERIC:
						columnOutput.value = resultSet.getLong(col);
						break;
					case Types.DATE:
						if (resultSet.getDate(col) != null) {
							columnOutput.value = new java.util.Date(resultSet.getDate(col).getTime());
						}
						break;
					case Types.TIMESTAMP:
						if (resultSet.getTimestamp(col) != null) {
							columnOutput.value = new java.util.Date(resultSet.getTimestamp(col).getTime());
						}
						break;
					case Types.TIMESTAMP_WITH_TIMEZONE:
						java.sql.Timestamp zonedTimestamp = resultSet.getTimestamp(col, Calendar.getInstance());
						if (zonedTimestamp != null) {
							columnOutput.value = new Date(zonedTimestamp.getTime());
						} else {
							columnOutput.value = null;
						}
						break;
					case Types.DECIMAL:
					case Types.DOUBLE:
					case Types.FLOAT:
						columnOutput.value = resultSet.getDouble(col);
						break;
					case Types.OTHER:
						columnOutput.value = resultSet.getObject(col);
						break;
					default:
						columnOutput.value = resultSet.getString(col);
						break;
				}
			}
		}
		return columnOutput;
	}

	private final AtomicBoolean nextAllowed;

	/**
	 * The list of columns to include in the resulting Object[]
	 */
	private final List<ColumnConfig> columns;

	/**
	 * The SailPoint context
	 */
	private final SailPointContext context;

	/**
	 * A container for the most recent row read
	 */
	private Map<String, Object> lastRow;

	/**
	 * A map from column name to column SQL type
	 */
	private final Map<String, Integer> nameTypeMap;

	/**
	 * The result set to iterate
	 */
	private final ResultSet resultSet;

	/**
	 * Constructs an iterator over the ResultSet, inferring the column names from the ResultSet's
	 * metadata object. Columns will be returned in the order specified by the ResultSet.
	 *
	 * No special parsing or object conversion options are available via this constructor.
	 *
	 * @param resultSet The result set to iterate over
	 * @param context The sailopint context
	 * @throws SQLException if anything goes wrong parsing the column names
	 */
	public ResultSetIterator(ResultSet resultSet, SailPointContext context) throws SQLException {
		this.resultSet = Objects.requireNonNull(resultSet);
		this.nextAllowed = new AtomicBoolean(true);
		this.context = context;
		this.nameTypeMap = new HashMap<>();
		this.columns = new ArrayList<>();

		ResultSetMetaData rsmd = resultSet.getMetaData();
		for(int c = 1; c <= rsmd.getColumnCount(); c++) {
			columns.add(new ColumnConfig(rsmd.getColumnLabel(c)));
			nameTypeMap.put(rsmd.getColumnLabel(c).toUpperCase(), rsmd.getColumnType(c));
		}

	}

	/**
	 * Adapts a ResultSet into Iterator<Object[]> form. The columns argument must be a list of objects
	 * that can be passed to {@link ColumnConfig#ColumnConfig(Object)}.
	 *
	 * @param resultSet The result set to adapt
	 * @param columns The ordered list of columns to include in the results
	 * @param context The IIQ context
	 * @throws SQLException if something goes wrong with checking column names
	 */
	public ResultSetIterator(ResultSet resultSet, List<?> columns, SailPointContext context) throws SQLException {
		this.resultSet = Objects.requireNonNull(resultSet);
		this.nextAllowed = new AtomicBoolean(true);
		this.context = context;
		this.nameTypeMap = new HashMap<>();

		ResultSetMetaData rsmd = resultSet.getMetaData();
		for(int c = 1; c <= rsmd.getColumnCount(); c++) {
			nameTypeMap.put(rsmd.getColumnLabel(c).toUpperCase(), rsmd.getColumnType(c));
		}

		if (columns == null || columns.isEmpty()) {
			throw new IllegalArgumentException("A list of columns must be supplied to ResultSetIterator");
		}

		for(Object o : columns) {
			if (!(o instanceof ReportColumnConfig || o instanceof String || o instanceof ColumnConfig || o instanceof Map)) {
				throw new IllegalArgumentException("The second constructor parameter for ResultSetIterator must be a non-empty List of either Strings, Maps, or ReportColumnConfigs");
			}
		}
		this.columns = columns.stream().map(ColumnConfig::new).collect(Collectors.toList());
	}

	/**
	 * Closes the result set; this can be called via an AutoCloseable
	 * @throws SQLException if the closure fails
	 */
	@Override
	public void close() throws SQLException {
		if (!resultSet.isClosed()) {
			resultSet.close();
		}
		this.nextAllowed.set(false);
		this.lastRow = null;
	}

	/**
	 * Gets the map from field name to header. In many basic configurations, the
	 * field name and header will be identical, and both will be the same as the
	 * property name.
	 *
	 * This is a {@link ListOrderedMap} and the keys will be in order.
	 *
	 * @return The map from field name to header
	 */
	public Map<String, String> getFieldHeaderMap() {
		Map<String, String> headers = new ListOrderedMap<>();
		for(ColumnConfig cc : this.columns) {
			headers.put(cc.getField(), cc.getHeader());
		}
		return headers;
	}

	/**
	 * Gets the SQL column type (or null) from the given column token.
	 *
	 * @param columnToken The column token
	 * @return The JDBC type constant or null if not mapped
	 */
	private Integer getType(ColumnToken columnToken) {
		String baseName = columnToken.getBaseColumnName();
		return nameTypeMap.get(baseName.toUpperCase());
	}

	/**
	 * Returns true if the ResultSet has another row, advancing the ResultSet in
	 * the process.
	 *
	 * @return True if the ResultSet has another row
	 */
	@Override
	public boolean hasNext() {
		boolean result = false;
		if (resultSet != null) {
			try {
				result = !resultSet.isClosed() && resultSet.next();
			} catch (SQLException e) {
				log.warn("Caught an error looping over a result set", e);
			}
		}

		if (!result) {
			this.nextAllowed.set(false);
		}

		return result;
	}

	/**
	 * Retrieves the next row from the result set as an Object[], given the column configs.
	 * This method also populates the {@link #lastRow} object returned by {@link #nextRow()}.
	 *
	 * @return The columns in the current row in order
	 * @throws NoSuchElementException if this class has been closed or if a previous call to {@link #hasNext()} returned false
	 */
	@Override
	public Object[] next() {
		if (!this.nextAllowed.get()) {
			throw new NoSuchElementException("ResultSet is closed or exhausted");
		}
		this.lastRow = new ListOrderedMap<>();
		try {
			Object[] result = new Object[columns.size()];
			for(Index<ColumnConfig> column : Index.with(columns)) {
				ColumnConfig columnConfig = column.getValue();

				ColumnToken token = columnConfig.getColumnToken();

				ColumnOutput output = extractColumnValue(resultSet, columnConfig.getProperty(), getType(token));
				Object outputObject = output.value;

				// If the value is null, and there is an alternative property specified on the
				// column config, use that.
				String ifEmptyColumn = columnConfig.getIfEmpty();
				if (outputObject == null && Util.isNotNullOrEmpty(ifEmptyColumn)) {
					ColumnToken ifEmptyColumnToken = columnConfig.getIfEmptyColumnToken();
					output = extractColumnValue(resultSet, ifEmptyColumn, getType(ifEmptyColumnToken));
					outputObject = output.value;
				}

				if (outputObject != null) {
					ColumnToken actualToken = output.columnToken;
					String actualTokenType = actualToken.getTypeToken();
					if (actualTokenType != null && !actualTokenType.isEmpty()) {
						outputObject = deriveTypedValue(context, outputObject, actualToken);
					}
				}

				lastRow.put(columnConfig.getField(), outputObject);
				result[column.getIndex()] = outputObject;
			}
			return result;
		} catch(SQLException | GeneralException e) {
			// can't throw a checked exception out of an Iterator next()
			throw new IllegalStateException(e);
		}
	}

	/**
	 * To be used in place of {@link #next()}, returns a Map structure with the
	 * column 'field' names instead of an Object[]. See {@link ColumnConfig#getField()}
	 * for detail on which keys will be used.
	 *
	 * This method advances the iterator by internally invoking {@link #next()}.
	 *
	 * @return A Map representing the next row read by {@link #next()}
	 */
	public Map<String, Object> nextRow() {
		next();
		return lastRow;
	}

}
