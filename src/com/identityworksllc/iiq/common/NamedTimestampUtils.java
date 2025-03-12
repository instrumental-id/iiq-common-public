package com.identityworksllc.iiq.common;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.Version;
import sailpoint.api.SailPointContext;
import sailpoint.object.SailPointObject;
import sailpoint.plugin.PluginBaseHelper;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * In 8.4, a new class NamedTimestamp was added. We can't depend on it existing in
 * earlier versions, but the behavior is so useful, we want to simulate it.
 *
 * In pre-8.4 versions, we will do so using a database table, iiqc_named_timestamps.
 * In 8.4, we will simply use the object as-is via reflection.
 */
public class NamedTimestampUtils {
    /**
     * The SQL query to delete a timestamp by name.
     */
    public static final String DELETE_QUERY = "DELETE FROM iiqc_named_timestamps WHERE name = ?";

    /**
     * The SQL query to fetch a timestamp by name.
     */
    public static final String FETCH_VALUE_QUERY = "SELECT value FROM iiqc_named_timestamps WHERE name = ?";

    /**
     * The version string for IIQ 8.4.
     */
    public static final String IIQ_84 = "8.4";

    /**
     * The SQL query to insert a timestamp by name.
     */
    public static final String INSERT_QUERY = "INSERT INTO iiqc_named_timestamps (id, name, value) VALUES (?, ?, ?)";

    /**
     * The method names for the NamedTimestamp class.
     */
    public static final String METHOD_GET_TIMESTAMP = "getTimestamp";

    /**
     * The method names for the NamedTimestamp class.
     */
    public static final String METHOD_SET_TIMESTAMP = "setTimestamp";

    /**
     * The name of the NamedTimestamp object.
     */
    public static final String OBJECT_NAMED_TIMESTAMP = "sailpoint.object.NamedTimestamp";

    /**
     * The SQL query to check if the iiqc_named_timestamps table exists.
     */
    public static final String TABLE_EXISTS_QUERY = "SELECT 1 FROM iiqc_named_timestamps";

    /**
     * The SQL query to update a timestamp by name.
     */
    public static final String UPDATE_QUERY = "UPDATE iiqc_named_timestamps SET value = ? WHERE name = ?";

    /**
     * The SailPointContext to use.
     */
    private final SailPointContext context;

    /**
     * The logger to use.
     */
    private final Log log;

    /**
     * Creates a new NamedTimestampUtils instance.
     * @param context the SailPointContext to use
     */
    public NamedTimestampUtils(SailPointContext context) {
        this.context = context;
        this.log = LogFactory.getLog(NamedTimestampUtils.class);
    }

    /**
     * Fetches a timestamp by name. If the timestamp is not found, an empty Optional is returned.
     * @param name The name of the timestamp
     * @return An Optional containing the timestamp, or an empty Optional if the timestamp is not found
     * @throws GeneralException if an error occurs during retrieval
     */
    private Optional<Instant> fetch84Timestamp(String name) throws GeneralException {
        if (log.isTraceEnabled()) {
            log.trace("Fetching 8.4 timestamp for " + name);
        }
        try {
            @SuppressWarnings("unchecked")
            Class<? extends SailPointObject> timestampClass = (Class<? extends SailPointObject>) Class.forName(OBJECT_NAMED_TIMESTAMP);

            SailPointObject namedTimestamp = context.getObjectByName(timestampClass, name);

            if (namedTimestamp != null) {
                Method getter = timestampClass.getMethod(METHOD_GET_TIMESTAMP);
                Date timestampValue = (Date) getter.invoke(namedTimestamp);

                if (timestampValue != null) {
                    return Optional.of(timestampValue.toInstant());
                } else {
                    log.warn("NamedTimestamp " + name + " did not have a timestamp value??");
                }
            } else {
                log.info("NamedTimestamp " + name + " not found");
            }
        } catch(ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            log.error("Failure getting 8.4 named timestamp", e);
            throw new GeneralException(e);
        }

        return Optional.empty();
    }

    /**
     * Fetches a timestamp by name. If the timestamp is not found, an empty Optional is returned.
     * @param name The name of the timestamp
     * @return An Optional containing the timestamp, or an empty Optional if the timestamp is not found
     * @throws GeneralException if an error occurs during retrieval
     */
    public Optional<Instant> get(String name) throws GeneralException {
        if (Version.getFullVersion().contains(IIQ_84)) {
            Optional<Instant> result = fetch84Timestamp(name);
            if (!result.isPresent() && tableExists()) {
                result = fetchTimestampFromDatabase(name);
            }
            return result;
        } else if (tableExists()) {
            return fetchTimestampFromDatabase(name);
        } else {
            throw new GeneralException("NamedTimestamp table does not exist and 8.4 timestamp class is not available");
        }
    }

    /**
     * Fetches a timestamp by name from the custom database table.
     * @param name the name of the timestamp
     * @return an Optional containing the timestamp, or an empty Optional if the timestamp is not found
     * @throws GeneralException if an error occurs during retrieval
     */
    private Optional<Instant> fetchTimestampFromDatabase(String name) throws GeneralException {
        if (log.isTraceEnabled()) {
            log.trace("Fetching database timestamp for " + name);
        }

        try (Connection connection = PluginBaseHelper.getConnection()) {
            try (PreparedStatement stmt = connection.prepareStatement(FETCH_VALUE_QUERY)) {
                stmt.setString(1, name);

                try (ResultSet results = stmt.executeQuery()) {
                    if (results.next()) {
                        return Optional.of(results.getTimestamp(1).toInstant());
                    } else {
                        log.info("NamedTimestamp " + name + " not found");
                    }
                }
            }
        } catch(SQLException e) {
            throw new RuntimeException(e);
        }

        return Optional.empty();
    }

    /**
     * Removes a timestamp from the database.
     * @param name the name of the timestamp to remove
     * @throws GeneralException if an error occurs during removal
     */
    private void removeTimestampFromDatabase(String name) throws GeneralException {
        if (!tableExists()) {
            return;
        }

        if (log.isTraceEnabled()) {
            log.trace("Removing timestamp from database: " + name);
        }

        try (Connection connection = PluginBaseHelper.getConnection()) {
            try (PreparedStatement stmt = connection.prepareStatement(DELETE_QUERY)) {
                stmt.setString(1, name);
                stmt.executeUpdate();
            }
        } catch(SQLException e) {
            log.error("Error removing timestamp from database", e);
            throw new GeneralException(e);
        }

    }

    /**
     * Attempts to store the timestamp using IIQ 8.4's NamedTimestamp object.
     *
     * @param name      the name of the timestamp
     * @param timestamp the timestamp to store
     * @throws GeneralException if an error occurs during storage
     */
    private void store84Timestamp(String name, Instant timestamp) throws GeneralException {
        if (log.isTraceEnabled()) {
            log.trace("Storing 8.4 timestamp: " + name + ", value = " + timestamp);
        }

        try {
            @SuppressWarnings("unchecked")
            Class<? extends SailPointObject> timestampClass = (Class<? extends SailPointObject>) Class.forName(OBJECT_NAMED_TIMESTAMP);

            SailPointObject namedTimestamp = context.getObjectByName(timestampClass, name);

            if (namedTimestamp == null) {
                // Create a new NamedTimestamp object
                namedTimestamp = timestampClass.getConstructor().newInstance();
                namedTimestamp.setName(name);
            }

            // Set the timestamp
            Method setTimestampMethod = timestampClass.getMethod(METHOD_SET_TIMESTAMP, Date.class);
            setTimestampMethod.invoke(namedTimestamp, Date.from(timestamp));

            // Save the object
            context.saveObject(namedTimestamp);
            context.commitTransaction();

        } catch(ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException | InstantiationException e) {
            log.error("Failure storing 8.4 named timestamp", e);
        }
    }

    /**
     * Stores a timestamp by name.
     * @param name the name of the timestamp
     * @param instant the timestamp to store
     * @throws GeneralException if an error occurs during storage
     */
    public void put(String name, Instant instant) throws GeneralException {
        if (Util.isNullOrEmpty(name)) {
            throw new IllegalArgumentException("Name cannot be null or empty");
        }

        if (Version.getFullVersion().contains(IIQ_84)) {
            store84Timestamp(name, instant);
            removeTimestampFromDatabase(name);
        } else if (tableExists()) {
            storeTimestampInDatabase(name, instant);
        } else {
            throw new GeneralException("NamedTimestamp table does not exist and 8.4 timestamp class is not available");
        }
    }

    /**
     * Stores a timestamp by name.
     *
     * Equivalent to calling storeTimestamp(name, Instant.ofEpochMillis(value)).
     *
     * @param name the name of the timestamp
     * @param value the timestamp to store
     * @throws GeneralException if an error occurs during storage
     */
    public void put(String name, long value) throws GeneralException {
        put(name, Instant.ofEpochMilli(value));
    }

    /**
     * Stores a timestamp in the custom database table.
     *
     * @param name the name of the timestamp
     * @param timestamp the timestamp to store
     * @throws GeneralException if an error occurs during storage
     */
    private void storeTimestampInDatabase(String name, Instant timestamp) throws GeneralException {
        if (log.isTraceEnabled()) {
            log.trace("Storing timestamp in database: " + name + ", value = " + timestamp);
        }
        try (Connection connection = PluginBaseHelper.getConnection()) {
            // Check if the timestamp already exists
            boolean exists = false;
            try (PreparedStatement stmt = connection.prepareStatement(FETCH_VALUE_QUERY)) {
                stmt.setString(1, name);
                try (ResultSet results = stmt.executeQuery()) {
                    if (results.next()) {
                        exists = true;
                    }
                }
            }

            if (exists) {
                // Update existing timestamp
                try (PreparedStatement updateStmt = connection.prepareStatement(
                        UPDATE_QUERY)) {
                    updateStmt.setTimestamp(1, java.sql.Timestamp.from(timestamp));
                    updateStmt.setString(2, name);
                    updateStmt.executeUpdate();
                }
            } else {
                // Insert new timestamp
                String newId = java.util.UUID.randomUUID().toString();
                try (PreparedStatement insertStmt = connection.prepareStatement(
                        INSERT_QUERY)) {
                    insertStmt.setString(1, newId);
                    insertStmt.setString(2, name);
                    insertStmt.setTimestamp(3, java.sql.Timestamp.from(timestamp));
                    insertStmt.executeUpdate();
                }
            }
        } catch(SQLException e) {
            log.error("Error storing timestamp in database", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Checks if the custom database table exists.
     * @return true if the table exists, false otherwise
     * @throws GeneralException if an error occurs during the check
     */
    private boolean tableExists() throws GeneralException {
        try (Connection connection = PluginBaseHelper.getConnection()) {
            try (PreparedStatement stmt = connection.prepareStatement(TABLE_EXISTS_QUERY)) {
                stmt.executeQuery();
                return true;
            }
        } catch(SQLException e) {
            if (log.isTraceEnabled()) {
                log.trace("NamedTimestamp table does not exist", e);
            }
            return false;
        }
    }
}
