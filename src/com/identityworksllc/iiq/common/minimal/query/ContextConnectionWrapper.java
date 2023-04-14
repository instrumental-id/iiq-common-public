package com.identityworksllc.iiq.common.minimal.query;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.server.Environment;
import sailpoint.tools.GeneralException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * The {@link SailPointContext#getJdbcConnection()} method caches the connection that
 * the context uses. Subsequent calls to the getJdbcConnection() on the same context
 * will return the same object.
 *
 * This means that if you close() that connection, as you should if you are writing correct
 * JDBC code, then on the second retrieval, you will get back a wrapper for an already
 * closed connection. In theory, this ought to just pull a new connection from the pool,
 * but Sailpoint has a glitch that prevents this. When you attempt to use the connection,
 * you will receive a "connection is null" error from deep within DBCP2.
 *
 * This utility goes directly to the configured Spring DataSource to get a pooled connection.
 *
 * Note that this glitch doesn't affect Hibernate-based sessions because those already use
 * their own Hibernate Session Factory that also directly calls to the underlying DataSource.
 *
 * TECHNICAL DETAILS:
 *
 * The nested layers of underlying connection wrappers is:
 *
 *   SPConnection
 *   to ConnectionWrapper
 *   to PoolGuardConnectionWrapper
 *   to DelegatingConnection
 *   to DelegatingConnection (*)
 *   to Underlying driver Connection
 *
 * The connection marked with a (*) is the one that is nulled on close().
 */
public class ContextConnectionWrapper {

    /**
     * Gets a new connection attached to the current SailPointContext.
     * @return The opened connection
     * @throws GeneralException if any failures occur opening the connection
     */
    public static Connection getConnection() throws GeneralException {
        return getConnection(null);
    }


    /**
     * Gets a new connection attached to the given SailPointContext, going directly to the
     * underlying Spring DataSource object rather than going through the context.
     *
     * @param context The context to which the open connection should be attached for logging, or null to use the current thread context
     * @return The opened connection
     * @throws GeneralException if any failures occur opening the connection
     */
    public static Connection getConnection(SailPointContext context) throws GeneralException {
        SailPointContext currentContext = SailPointFactory.peekCurrentContext();
        try {
            if (context != null) {
                SailPointFactory.setContext(context);
            }
            try {
                DataSource dataSource = Environment.getEnvironment().getSpringDataSource();
                if (dataSource == null) {
                    throw new GeneralException("Unable to return connection, no DataSource defined!");
                }

                return dataSource.getConnection();
            } catch (SQLException e) {
                throw new GeneralException(e);
            }
        } finally {
            SailPointFactory.setContext(currentContext);
        }
    }

}
