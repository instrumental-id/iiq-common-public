package com.identityworksllc.iiq.common.query;

import java.sql.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The abstract superclass for the named parameter statement types.
 *
 * @param <StatementType> The type of statement, {@link PreparedStatement} or {@link CallableStatement}
 */
public abstract class AbstractNamedParameterStatement<StatementType extends Statement> implements AutoCloseable {

    /**
     * Parses a query with named parameters. The parameter-index mappings are put into the map, and the parsed query is returned.
     *
     * @param query query to parse
     * @param indexMap map to hold parameter-index mappings
     * @return the parsed query
     */
    public static String parse(String query, Map<String, int[]> indexMap) {
        Map<String, List<Integer>> paramMap = new HashMap<>();
        int length = query.length();
        StringBuilder parsedQuery = new StringBuilder(length);
        int index = 1;
        for (int i = 0; i < length; i++) {
            char c = query.charAt(i);
            if (c == '\'' || c == '"') {
                // Consume quoted substrings...
                char original = c;
                do {
                    i++;
                    parsedQuery.append(c);
                } while (i < length && (c = query.charAt(i)) != original);
            } else if (c == ':' && i + 1 < length && Character.isJavaIdentifierStart(query.charAt(i + 1))) {
                // Found a placeholder!
                String name = parseParameterName(query, i);
                c = '?'; // replace the parameter with a question mark
                i += name.length(); // skip past the end if the parameter
                List<Integer> indexList = paramMap.computeIfAbsent(name, k -> new LinkedList<>());
                indexList.add(index);
                index++;
            }
            parsedQuery.append(c);
        }
        toIntArrayMap(paramMap, indexMap);
        return parsedQuery.toString();
    }

    /**
     * Parses a name from the given query string starting at the given position.
     *
     * @param query The query string from which to parse the parameter name
     * @param pos The position at which it was detected a parameter starts
     * @return The name of the parameter parsed
     */
    private static String parseParameterName(String query, int pos) {
        int j = pos + 2;
        while (j < query.length() && Character.isJavaIdentifierPart(query.charAt(j))) {
            j++;
        }
        return query.substring(pos + 1, j);
    }

    /**
     * Moves all values from a map having a list of ints, to one having an array of ints
     *
     * @param inMap The input map, having a list of ints for values.
     * @param outMap The output map, on which to put the same values as an array of ints.
     */
    private static void toIntArrayMap(Map<String, List<Integer>> inMap, Map<String, int[]> outMap) {
        // replace the lists of Integer objects with arrays of ints
        for (Map.Entry<String, List<Integer>> entry : inMap.entrySet()) {
            List<Integer> list = entry.getValue();
            int[] indexes = new int[list.size()];
            int i = 0;
            for (Integer integer : list) {
                indexes[i++] = integer;
            }
            outMap.put(entry.getKey(), indexes);
        }
    }

    /**
     * If true, invoking one of the setXXX methods on a non-existent parameter
     * will be silently ignored. The default is to throw an exception.
     */
    protected boolean allowMissingAttributes;

    /**
     * The connection
     */
    protected Connection connection;

    /**
     * Maps parameter names to arrays of ints which are the parameter indices.
     */
    protected Map<String, int[]> indexMap;

    /**
     * The statement this object is wrapping.
     **/
    protected StatementType statement;

    /**
     * Adds an item to the current batch. Oddly this is not implemented in {@link Statement},
     * but it is implemented by both sub-types of statement.
     *
     * @see PreparedStatement#addBatch()
     * @see CallableStatement#addBatch()
     * @throws SQLException if an error occurred
     */
    public abstract void addBatch() throws SQLException;

    /**
     * Cancels this Statement object if both the DBMS and driver support aborting an SQL statement.
     * @throws SQLException if an error occurred
     * @see Statement#cancel()
     */
    public void cancel() throws SQLException {
        if (!statement.isClosed()) {
            statement.cancel();
        }
    }

    /**
     * Closes the statement.
     *
     * @throws SQLException if an error occurred
     * @see Statement#close()
     */
    public void close() throws SQLException {
        if (!statement.isClosed()) {
            statement.close();
        }
    }

    /**
     * Executes all of the batched statements. See {@link Statement#executeBatch()} and {@link #addBatch()} for details.
     *
     * @return update counts for each statement
     * @throws SQLException if something went wrong
     *
     * @see Statement#executeBatch()
     */
    public int[] executeBatch() throws SQLException {
        return statement.executeBatch();
    }

    /**
     * Returns the connection this statement is using.
     *
     * @return the connection
     * @throws SQLException if an error occurs while retrieving the connection
     * @see Statement#getConnection()
     */
    public Connection getConnection() throws SQLException {
        return statement.getConnection();
    }

    /**
     * Returns the indexes for a parameter.
     *
     * @param name parameter name
     * @return parameter indexes
     * @throws IllegalArgumentException if the parameter does not exist
     */
    protected int[] getIndexes(String name) {
        int[] indexes = indexMap.get(name);
        if (indexes == null) {
            if (allowMissingAttributes) {
                return new int[0];
            } else {
                throw new IllegalArgumentException("Parameter not found: " + name);
            }
        }
        return indexes;
    }

    /**
     * Returns any keys generated by the SQL statement.
     * @return A ResultSet containing the generated keys
     * @throws SQLException if an error occurs while retrieving the keys
     * @see Statement#getGeneratedKeys()
     */
    public ResultSet getGeneratedKeys() throws SQLException {
        return statement.getGeneratedKeys();
    }

    /**
     * Moves to this Statement object's next result, returns true if it is a ResultSet object, and implicitly closes
     * any current ResultSet object(s) obtained with the method getResultSet.
     * @return True if the next result is a ResultSet object, false if there are no more results
     * @throws SQLException if an error occurs while moving to the next result
     */
    public boolean getMoreResults() throws SQLException {
        return statement.getMoreResults();
    }

    /**
     * Moves to this Statement object's next result, deals with any current ResultSet object(s) according
     * to the instructions specified by the given flag, and returns true if the next result is a
     * ResultSet object.
     *
     * @param current What to do with the current result set, a constant from {@link Statement}
     * @return True if the next result is a ResultSet object, false if there are no more results
     * @throws SQLException if an error occurs while moving to the next result
     * @see Statement#getMoreResults(int)
     * @see Statement#CLOSE_CURRENT_RESULT
     * @see Statement#KEEP_CURRENT_RESULT
     * @see Statement#CLOSE_ALL_RESULTS
     */
    public boolean getMoreResults(int current) throws SQLException {
        return statement.getMoreResults(current);
    }

    /**
     * Returns the set of parameter names.
     * @return An unmodifiable set of parameter names
     */
    public Set<String> getParameterNames() {
        return Collections.unmodifiableSet(indexMap.keySet());
    }

    /**
     * Returns the result set of the statement.
     *
     * @return the result set
     * @throws SQLException if an error occurs while retrieving the result set
     * @see Statement#getResultSet()
     */
    public ResultSet getResultSet() throws SQLException {
        return statement.getResultSet();
    }

    /**
     * Returns the underlying statement.
     *
     * @return the statement
     */
    public StatementType getStatement() {
        return statement;
    }

    /**
     * Returns true if the wrapped statement is closed
     * @return True if the wrapped statement is closed
     * @see Statement#isClosed()
     */
    public boolean isClosed() throws SQLException {
        return statement.isClosed();
    }

    /**
     * Sets the flag to allow an attempt to set missing attributes without throwing
     * an exception. With this flag set to false, any attempt to invoke setString, or any
     * other setXYZ method, will result in an exception.
     *
     * @param allowMissingAttributes `true` if we should not throw an exception when a parameter is unused
     */
    public void setAllowMissingAttributes(boolean allowMissingAttributes) {
        this.allowMissingAttributes = allowMissingAttributes;
    }

    /**
     * @see Statement#setFetchDirection(int)
     */
    public void setFetchDirection(int i) throws SQLException {
        statement.setFetchDirection(i);
    }

    /**
     * @see Statement#setFetchSize(int)
     */
    public void setFetchSize(int i) throws SQLException {
        statement.setFetchSize(i);
    }

    /**
     * Set the max rows in the result set
     * @param i The result row size
     * @throws SQLException on errors
     */
    public void setMaxRows(int i) throws SQLException {
        statement.setMaxRows(i);
    }

    /**
     * Abstract method for use by subclasses to register their own specific value types
     * @param name The name of the argument to set
     * @param value The value to set
     * @throws SQLException if any errors occur
     */
    public abstract void setObject(String name, Object value) throws SQLException;

    /**
     * Sets all parameters to this named parameter statement in bulk from the given Map
     *
     * @param parameters The parameters in question
     * @throws SQLException if any failures occur
     */
    public final void setParameters(Map<String, Object> parameters) throws SQLException {
        if (parameters != null) {
            for (String key : parameters.keySet()) {
                setObject(key, parameters.get(key));
            }
        }
    }

    /**
     * Sets the query timeout
     * @param seconds The timeout for this query in seconds
     * @throws SQLException if any errors occur
     * @see Statement#setQueryTimeout(int)
     */
    public void setQueryTimeout(int seconds) throws SQLException {
        statement.setQueryTimeout(seconds);
    }

}
