package com.identityworksllc.iiq.common.query;

import sailpoint.object.SailPointObject;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Date;

/**
 * Utility class for setting the parameters on a {@link java.sql.Statement} based on the input,
 * invoking the typed setXYZ methods (e.g., setString for a string).
 *
 * Unrecognized input types will be set via `setString`.
 */
public class Parameters {
    /**
     * Sets up the parameter in the given PreparedStatement per the type
     * of the input.
     *
     * @param stmt The PreparedStatement to wire up
     * @param param The parameter number to wire up
     * @param value The value of the parameter
     * @throws SQLException on any database failures
     */
    private static void setupParameter(PreparedStatement stmt, int param, Object value) throws SQLException {
        if (value == null) {
            stmt.setNull(param, Types.VARCHAR);
        } else if (value instanceof TimestampWithTimezone) {
            stmt.setTimestamp(param, (Timestamp) value, ((TimestampWithTimezone) value).getZonedCalendar());
        } else if (value instanceof Timestamp) {
            stmt.setTimestamp(param, (Timestamp) value);
        } else if (value instanceof Date) {
            stmt.setDate(param, new java.sql.Date(((Date) value).getTime()));
        } else if (value instanceof Long) {
            stmt.setLong(param, (Long) value);
        } else if (value instanceof Integer) {
            stmt.setInt(param, (Integer) value);
        } else if (value instanceof SailPointObject) {
            stmt.setString(param, ((SailPointObject) value).getId());
        } else {
            stmt.setString(param, String.valueOf(value));
        }
    }

    /**
     * Set up the given parameters in the prepared statmeent
     * @param stmt The statement
     * @param parameters The parameters
     * @throws SQLException if any failures occur setting parameters
     */
    public static void setupParameters(PreparedStatement stmt, Object[] parameters) throws SQLException {
        if (parameters == null || parameters.length == 0) {
            return;
        }
        int param = 1;
        for (Object parameter : parameters) {
            setupParameter(stmt, param, parameter);
            param++;
        }
    }

    /**
     * Private utility constructor
     */
    private Parameters() {

    }
}
