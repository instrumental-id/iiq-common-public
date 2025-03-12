package com.identityworksllc.iiq.common.query;

import sailpoint.tools.JdbcUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Utility class for database operations
 */
public class DatabaseUtil {
    /**
     * Represents the type of a database
     */
    public enum DatabaseType {
        /**
         * Oracle database
         */
        Oracle,

        /**
         * MySQL database
         */
        MySQL,

        /**
         * Microsoft SQL Server database
         */
        Microsoft,

        /**
         * PostgreSQL database
         */
        PostgreSQL
    }
    /**
     * Set of reserved SQL words
     */
    public static final Set<String> RESERVED_SQL_WORDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "ADD", "ALL", "ALTER", "AND", "ANY", "AS", "ASC", "AUTO_INCREMENT", "BETWEEN", "BIGINT", "BINARY", "BIT",
            "BLOB", "BOTH", "BY", "CASCADE", "CASE", "CHAR", "CHARACTER", "CHECK", "COLLATE", "COLUMN", "CONSTRAINT",
            "CREATE", "CROSS", "CURRENT_DATE", "CURRENT_TIME", "CURRENT_TIMESTAMP", "CURRENT_USER", "DATABASE",
            "DEFAULT", "DELETE", "DESC", "DISTINCT", "DOUBLE", "DROP", "ELSE", "END", "ENUM", "ESCAPE", "EXISTS",
            "FALSE", "FLOAT", "FOREIGN", "FROM", "FULLTEXT", "GROUP", "HAVING", "HEX", "HOUR", "IF", "IGNORE",
            "IN", "INDEX", "INNER", "INSERT", "INT", "INTEGER", "INTERVAL", "INTO", "IS", "JOIN", "KEY", "LEFT",
            "LIKE", "LIMIT", "LONGBLOB", "LONGTEXT", "MATCH", "MEDIUMBLOB", "MEDIUMINT", "MEDIUMTEXT", "MINUTE",
            "MODIFY", "NOT", "NULL", "NUMBER", "NUMERIC", "ON", "OR", "ORDER", "OUTER", "PRIMARY", "REAL", "REFERENCES",
            "REGEXP", "RENAME", "REPLACE", "RIGHT", "SELECT", "SET", "SHOW", "SMALLINT", "TABLE", "TEXT", "TIME",
            "TIMESTAMP", "TINYBLOB", "TINYINT", "TINYTEXT", "TRUE", "UNIQUE", "UNSIGNED", "UPDATE", "USE", "USING",
            "VALUES", "VARBINARY", "VARCHAR", "VARCHAR2", "VARCHARACTER", "VARYING", "WHEN", "WHERE", "YEAR", "ZEROFILL"
    )));

    /**
     * Prevent instantiation
     */
    private DatabaseUtil() {
        // Prevent instantiation
    }

    /**
     * Determines the type of the given database
     * @param connection The connection to the database
     * @return The resulting type determination
     * @throws SQLException if type determination fails, or if the DB is not a recognized type
     */
    public static DatabaseType getType(Connection connection) throws SQLException{
        if (isOracle(connection)) {
            return DatabaseType.Oracle;
        } else if (isMysql(connection)) {
            return DatabaseType.MySQL;
        } else if (isMicrosoft(connection)) {
            return DatabaseType.Microsoft;
        } else if (isPostgres(connection)) {
            return DatabaseType.PostgreSQL;
        } else {
            throw new SQLException("Unknown database type");
        }
    }

    /**
     * Determines if the given connection is to a Microsoft SQL Server database
     * @param connection The connection to the database
     * @return true if the database is Microsoft SQL Server, false otherwise
     * @throws SQLException if the database type cannot be determined
     */
    public static boolean isMicrosoft(Connection connection) throws SQLException {
        return connection.getMetaData().getDatabaseProductName().contains("Microsoft");
    }

    /**
     * Determines if the given connection is to a MySQL database
     * @param connection The connection to the database
     * @return true if the database is MySQL, false otherwise
     * @throws SQLException if the database type cannot be determined
     */
    public static boolean isMysql(Connection connection) throws SQLException {
        return JdbcUtil.isMySQL(connection);
    }

    /**
     * Determines if the given connection is to an Oracle database
     * @param connection The connection to the database
     * @return true if the database is Oracle, false otherwise
     * @throws SQLException if the database type cannot be determined
     */
    public static boolean isOracle(Connection connection) throws SQLException {
        return JdbcUtil.isOracle(connection);
    }

    /**
     * Determines if the given connection is to a PostgreSQL database
     * @param connection The connection to the database
     * @return true if the database is PostgreSQL, false otherwise
     * @throws SQLException if the database type cannot be determined
     */
    public static boolean isPostgres(Connection connection) throws SQLException {
        return connection.getMetaData().getDatabaseProductName().contains("PostgreSQL");
    }

    public static boolean isValidObjectName(String name) {
        return name.length() < 30 && name.matches("[a-zA-Z_][a-zA-Z0-9_]*");
    }

    /**
     * Returns true if the given table exists
     * @param connection The connection to the database
     * @param tableName The name of the table to check
     * @return true if the table exists, false otherwise
     */
    public static boolean tableExists(Connection connection, String tableName) {
        if (!isValidObjectName(tableName)) {
            throw new IllegalArgumentException("Invalid table name: " + tableName);
        }
        try (PreparedStatement stmt = connection.prepareStatement("SELECT 1 FROM " + tableName + " WHERE 1 = 0")) {
            stmt.execute();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

}
