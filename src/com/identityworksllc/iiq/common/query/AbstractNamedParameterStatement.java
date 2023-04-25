package com.identityworksllc.iiq.common.query;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("unused")
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
     */
    public abstract void addBatch() throws SQLException;

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
     */
    public int[] executeBatch() throws SQLException {
        return statement.executeBatch();
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
     * Returns the set of parameter names.
     * @return An unmodifiable set of parameter names
     */
    public Set<String> getParameterNames() {
        return Collections.unmodifiableSet(indexMap.keySet());
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
     * Sets the flag to allow missing attributes without throwing an exception
     * @param allowMissingAttributes True if we should not throw an exception when a parameter is unused
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
