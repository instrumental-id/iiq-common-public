package com.identityworksllc.iiq.common.minimal.query;

import sailpoint.object.Attributes;
import sailpoint.object.SailPointObject;
import sailpoint.tools.JdbcUtil;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.XMLObjectFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Reader;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;

/**
 * Named parameter prepared statement wrapper, derived from various examples
 * around the Internet.
 */
@SuppressWarnings("unused")
public final class NamedParameterStatement extends AbstractNamedParameterStatement<PreparedStatement> {

	/**
	 * Creates a NamedParameterStatement. Wraps a call to {@link Connection#prepareStatement(java.lang.String)}.
	 * 
	 * @param connection the database connection
	 * @param query the parameterized query
	 * @throws SQLException if the statement could not be created
	 */
	public NamedParameterStatement(Connection connection, String query) throws SQLException {
		indexMap = new HashMap<>();
		String parsedQuery = parse(query, indexMap);
		this.connection = connection;
		this.statement = connection.prepareStatement(parsedQuery);
		this.allowMissingAttributes = false;
	}

	/**
	 * Adds the current row to the batch
	 * @throws SQLException The batch
	 */
	public void addBatch() throws SQLException {
		this.statement.addBatch();
	}

	/**
	 * Executes the statement.
	 *
	 * @return true if the first result is a {@link ResultSet}
	 * @throws SQLException if an error occurred
	 * @see PreparedStatement#execute()
	 */
	public boolean execute() throws SQLException {
		return statement.execute();
	}

	/**
	 * Executes the statement and returns a result set
	 * @return The result set
	 * @throws SQLException on errors
	 */
	public ResultSet executeQuery() throws SQLException {
		return statement.executeQuery();
	}

	/**
	 * Executes the statement and returns the update count
	 * @return The update count
	 * @throws SQLException on errors
	 */
	public int executeUpdate() throws SQLException {
		return statement.executeUpdate();
	}

	/**
	 * Sets a parameter to an array. Note that this is not supported in all database engines (particularly, not in MySQL)
	 * @param name The name of the field
	 * @param value The list to change to an array
	 * @throws SQLException if a failure generating the array occurs
	 */
	public void setArray(String name, List<String> value) throws SQLException {
		if (value == null) {
			setNull(name, Types.ARRAY);
		} else {
			int[] indexes = getIndexes(name);
			for (int idx : indexes) {
				Array array = connection.createArrayOf("VARCHAR", value.toArray());
				statement.setArray(idx, array);
			}
		}
	}

	/**
	 * Sets the given byte array as a Blob input to the statement
	 * @param name The parameter name
	 * @param blob The blob
	 * @throws SQLException on failures
	 */
	public void setBlob(String name, Blob blob) throws SQLException {
		if (blob == null) {
			setNull(name, Types.BLOB);
		} else {
			int[] indexes = getIndexes(name);
			for (int idx : indexes) {
				statement.setBlob(idx, blob);
			}
		}
	}

	/**
	 * Sets the given byte array as a Blob input to the statement
	 * @param name The parameter name
	 * @param blob The blob
	 * @throws SQLException on failures setting the blob or reading the byte array
	 */
	public void setBlob(String name, byte[] blob) throws SQLException {
		if (blob == null) {
			setNull(name, Types.BLOB);
		} else {
			int[] indexes = getIndexes(name);
			for (int idx : indexes) {
				try (ByteArrayInputStream bais = new ByteArrayInputStream(blob)) {
					statement.setBlob(idx, bais);
				} catch (IOException e) {
					throw new SQLException(e);
				}
			}
		}
	}

	/**
	 * Sets a parameter to a clob
	 * @param name The named parameter
	 * @param clob The clob to set
	 * @throws SQLException on failures
	 */
	public void setClob(String name, Reader clob) throws SQLException {
		if (clob == null) {
			setNull(name, Types.CLOB);
		} else {
			int[] indexes = getIndexes(name);
			for (int idx : indexes) {
				// What does this do if there is more than one? Readers
				// can't usually be read twice. Do we want to copy the Reader?
				statement.setClob(idx, clob);
			}
		}
	}

	/**
	 * Sets a parameter to a clob
	 * @param name The named parameter
	 * @param clob The clob to set
	 * @throws SQLException on failures
	 */
	public void setClob(String name, String clob) throws SQLException {
		if (clob == null) {
			setNull(name, Types.CLOB);
		} else {
			int[] indexes = getIndexes(name);
			for (int idx : indexes) {
				if (JdbcUtil.isOracle(statement)) {
					JdbcUtil.setOracleCLOBParameter(statement, idx, clob);
				} else {
					JdbcUtil.setClobParameter(statement, idx, clob);
				}
			}
		}
	}


	/**
	 * Sets a parameter to a clob
	 * @param name The named parameter
	 * @param clob The clob to set
	 * @throws SQLException on failures
	 */
	public void setClob(String name, Clob clob) throws SQLException {
		if (clob == null) {
			setNull(name, Types.CLOB);
		} else {
			int[] indexes = getIndexes(name);
			for (int idx : indexes) {
				statement.setClob(idx, clob);
			}
		}
	}

	/**
	 * Sets a parameter to a Java time instant.
	 *
	 * @param name parameter name
	 * @param value parameter value
	 * @throws SQLException if an error occurred
	 * @throws IllegalArgumentException if the parameter does not exist
	 * @see PreparedStatement#setDate(int, Date)
	 */
	public void setDate(String name, LocalDateTime value) throws SQLException {
		if (value == null) {
			setNull(name, Types.DATE);
		} else {
			Date localDate = new Date(value.toEpochSecond(OffsetDateTime.now().getOffset()));
			setDate(name, localDate);
		}
	}


	/**
	 * Sets a parameter to a Java time instant.
	 *
	 * @param name parameter name
	 * @param value parameter value
	 * @throws SQLException if an error occurred
	 * @throws IllegalArgumentException if the parameter does not exist
	 * @see PreparedStatement#setDate(int, Date)
	 */
	public void setDate(String name, Instant value) throws SQLException {
		if (value == null) {
			setNull(name, Types.DATE);
		} else {
			setDate(name, new Date(value.toEpochMilli()));
		}
	}

	/**
	 * Sets a parameter to a Java date.
	 *
	 * @param name parameter name
	 * @param value parameter value
	 * @throws SQLException if an error occurred
	 * @throws IllegalArgumentException if the parameter does not exist
	 * @see PreparedStatement#setDate(int, Date)
	 */
	public void setDate(String name, java.util.Date value) throws SQLException {
		if (value == null) {
			setNull(name, Types.DATE);
		} else {
			java.sql.Date sqlDate = new java.sql.Date(value.getTime());
			int[] indexes = getIndexes(name);
			for (int idx : indexes) {
				statement.setDate(idx, sqlDate);
			}
		}
	}

	/**
	 * Sets a parameter.
	 *
	 * @param name parameter name
	 * @param value parameter value
	 * @throws SQLException if an error occurred
	 * @throws IllegalArgumentException if the parameter does not exist
	 * @see PreparedStatement#setDate(int, Date)
	 */
	public void setDate(String name, Date value) throws SQLException {
		if (value == null) {
			setNull(name, Types.DATE);
		} else {
			int[] indexes = getIndexes(name);
			for (int idx : indexes) {
				statement.setDate(idx, value);
			}
		}
	}

	/**
	 * Sets a parameter.
	 *
	 * @param name parameter name
	 * @param value parameter value
	 * @throws SQLException if an error occurred
	 * @throws IllegalArgumentException if the parameter does not exist
	 * @see PreparedStatement#setDouble(int, double)
	 */
	public void setDouble(String name, double value) throws SQLException {
		int[] indexes = getIndexes(name);
		for (int idx : indexes) {
			statement.setDouble(idx, value);
		}
	}

	/**
	 * Sets a parameter.
	 *
	 * @param name parameter name
	 * @param value parameter value
	 * @throws SQLException if an error occurred
	 * @throws IllegalArgumentException if the parameter does not exist
	 * @see PreparedStatement#setInt(int, int)
	 */
	public void setInt(String name, int value) throws SQLException {
		int[] indexes = getIndexes(name);
		for (int idx : indexes) {
			statement.setInt(idx, value);
		}
	}

	/**
	 * Sets a parameter.
	 *
	 * @param name parameter name
	 * @param value parameter value
	 * @throws SQLException if an error occurred
	 * @throws IllegalArgumentException if the parameter does not exist
	 * @see PreparedStatement#setLong(int, long)
	 */
	public void setLong(String name, long value) throws SQLException {
		int[] indexes = getIndexes(name);
		for (int idx : indexes) {
			statement.setLong(idx, value);
		}
	}

	/**
	 * Sets a parameter to null, assuming the type to be VARCHAR.
	 *
	 * @param name parameter name
	 * @throws SQLException if an error occurred
	 * @throws IllegalArgumentException if the parameter does not exist
	 * @see PreparedStatement#setNull(int, int)
	 */
	public void setNull(String name) throws SQLException {
		setNull(name, Types.VARCHAR);
	}

	/**
	 * Sets a typed parameter to null
	 *
	 * @param name parameter name
	 * @param type The SQL type of the argument to set
	 * @throws SQLException if an error occurred
	 * @throws IllegalArgumentException if the parameter does not exist
	 * @see PreparedStatement#setNull(int, int)
	 */
	public void setNull(String name, int type) throws SQLException {
		int[] indexes = getIndexes(name);
		for (int idx : indexes) {
			statement.setNull(idx, type);
		}
	}

	/**
	 * Sets a parameter to the given object. If the object is a SailPointObject,
	 * the parameter will be set as to its 'id' as a string instead.
	 *
	 * @param name parameter name
	 * @param value parameter value
	 * @throws SQLException if an error occurred
	 * @throws IllegalArgumentException if the parameter does not exist
	 * @see PreparedStatement#setObject(int, java.lang.Object)
	 */
	public void setObject(String name, Object value) throws SQLException {
		if (value == null) {
			setNull(name, Types.JAVA_OBJECT);
		} else if (value instanceof Attributes) {
			XMLObjectFactory f = XMLObjectFactory.getInstance();
			String xml = f.toXmlNoIndent(value);
			setClob(name, xml);
		} else if (value instanceof SailPointObject) {
			setString(name, ((SailPointObject) value).getId());
		} else if (value instanceof String) {
			setString(name, (String) value);
		} else if (value instanceof Timestamp) {
			setTimestamp(name, (Timestamp)value);
		} else if (value instanceof java.util.Date) {
			setDate(name, (java.util.Date) value);
		} else if (value instanceof Integer) {
			setInt(name, (Integer)value);
		} else if (value instanceof Long) {
			setLong(name, (Long)value);
		} else {
			int[] indexes = getIndexes(name);
			for (int idx : indexes) {
				statement.setObject(idx, value);
			}
		}
	}

	/**
	 * Sets a parameter.
	 *
	 * @param name parameter name
	 * @param value parameter value
	 * @throws SQLException if an error occurred
	 * @throws IllegalArgumentException if the parameter does not exist
	 * @see PreparedStatement#setString(int, java.lang.String)
	 */
	public void setString(String name, String value) throws SQLException {
		if (value == null) {
			setNull(name);
		} else {
			int[] indexes = getIndexes(name);
			for (int idx : indexes) {
				statement.setString(idx, value);
			}
		}
	}

	/**
	 * Sets a parameter.
	 *
	 * @param name parameter name
	 * @param value parameter value
	 * @throws SQLException if an error occurred
	 * @throws IllegalArgumentException if the parameter does not exist
	 * @see PreparedStatement#setTimestamp(int, java.sql.Timestamp)
	 */
	public void setTimestamp(String name, Timestamp value) throws SQLException {
		if (value == null) {
			setNull(name, Types.TIMESTAMP);
		} else {
			int[] indexes = getIndexes(name);
			for (int idx : indexes) {
				statement.setTimestamp(idx, value);
			}
		}
	}

	/**
	 * Serializes the input object into XML and then adds it to the query
	 * as a CLOB.
	 *
	 * @param name The field name
	 * @param xmlObject The XML object
	 * @throws SQLException if any failures occur
	 */
	public void setXml(String name, Object xmlObject) throws SQLException {
		XMLObjectFactory f = XMLObjectFactory.getInstance();
		String xml = f.toXmlNoIndent(xmlObject);

		setClob(name, xml);
	}
}