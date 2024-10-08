package com.identityworksllc.iiq.common.task.export;

import com.identityworksllc.iiq.common.Functions;
import com.identityworksllc.iiq.common.query.NamedParameterStatement;
import com.identityworksllc.iiq.common.threads.SailPointWorker;
import org.apache.commons.logging.Log;
import sailpoint.api.SailPointContext;
import sailpoint.object.Configuration;
import sailpoint.object.TaskResult;
import sailpoint.request.RequestPermanentException;
import sailpoint.task.TaskMonitor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JdbcUtil;
import sailpoint.tools.Message;
import sailpoint.tools.Util;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Date;
import java.util.Objects;

/**
 * An abstract superclass for all export partitions. It will open a connection to the target
 * database and then invoke export() on the subclass.
 */
public abstract class ExportPartition extends SailPointWorker implements Serializable {

    /**
     * Adds the common date fields (which must have the given names) to the given prepared statement.
     * The modified and last refresh dates can be null.
     *
     * @param statement The named parameter statement
     * @param exportDate The export date
     * @param created The creation date
     * @param modified The modified date (which can be null)
     * @param lastRefresh The last refresh date (which can be null)
     * @throws SQLException if any failures occur (unlikely)
     */
    protected static void addCommonDateFields(NamedParameterStatement statement, Date exportDate, Date created, Date modified, Date lastRefresh) throws SQLException {
        statement.setDate("created", created);
        if (modified != null) {
            statement.setDate("modified", modified);
        } else {
            statement.setNull("modified", Types.DATE);
        }
        if (lastRefresh != null) {
            statement.setDate("lastRefresh", lastRefresh);
        } else {
            statement.setNull("lastRefresh", Types.DATE);
        }
        statement.setDate("now", exportDate);
    }

    /**
     * Opens the connection to the target database using the provided connection info
     * @param context The sailpoint context, used to decrypt the password
     * @param connectionInfo The provided connection info, extracted from the export task def
     * @return The open connection
     * @throws GeneralException if any failures occur
     */
    public static Connection openConnection(SailPointContext context, ExportConnectionInfo connectionInfo) throws GeneralException {
        String decryptedPassword = context.decrypt(connectionInfo.getEncryptedPassword());
        return JdbcUtil.getConnection(connectionInfo.getDriver(), null, connectionInfo.getUrl(), connectionInfo.getUsername(), decryptedPassword, connectionInfo.getOptions());
    }

    /**
     * The configuration loaded in {@link #execute(SailPointContext, Log)}
     */
    protected transient Configuration configuration;
    /**
     * The name of the configuration object, set by the task
     */
    private String configurationName;
    /**
     * The connection info
     */
    private ExportConnectionInfo connectionInfo;
    /**
     * The cutoff date, milliseconds. We should not export records older than this date.
     */
    protected long cutoffDate;
    /**
     * The export timestamp, milliseconds. We should not export records newer than this date.
     */
    protected long exportTimestamp;
    /**
     * The filter string
     */
    protected String filterString;

    /**
     * The second filter string if any
     */
    protected String filterString2;

    /**
     * The name of the partition
     */
    private String name;

    /**
     * The worker entrypoint
     * @param context The private context to use for this thread worker
     * @param logger The log attached to this Worker
     * @return Always null, nothing required here
     * @throws Exception if anything goes wrong
     */
    @Override
    public final Object execute(SailPointContext context, Log logger) throws Exception {
        if (Util.isNullOrEmpty(configurationName)) {
            throw new GeneralException("Unable to execute export worker: configurationName not set");
        }
        this.configuration = context.getObjectByName(Configuration.class, configurationName);
        if (this.configuration == null) {
            throw new GeneralException("Unable to execute export worker: Configuration named '" + configurationName + "' does not exist");
        }

        if (Util.isNotNullOrEmpty(filterString)) {
            TaskResult partitionResult = monitor.lockPartitionResult();
            try {
                partitionResult.setAttribute("filter", filterString + (filterString2 != null ? (" && " + filterString2) : ""));
            } finally {
                monitor.commitPartitionResult();
            }
        }

        try (Connection connection = openConnection(context, connectionInfo)) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                export(context, connection, logger);
                connection.commit();
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch(Exception e) {
            TaskResult partitionResult = monitor.lockPartitionResult();
            try {
                partitionResult.addException(e);
            } finally {
                monitor.commitPartitionResult();
            }
            throw new RequestPermanentException(e);
        }
        return null;
    }

    /**
     * Exports the data required to the listed database
     *
     * @param context The context
     * @param connection The connection to the target database
     * @param logger The logger
     * @throws GeneralException if any failures occur
     */
    protected abstract void export(SailPointContext context, Connection connection, Log logger) throws GeneralException;

    public Configuration getConfiguration() {
        return configuration;
    }

    public String getConfigurationName() {
        return configurationName;
    }

    public long getCutoffDate() {
        return cutoffDate;
    }

    public long getExportTimestamp() {
        return exportTimestamp;
    }

    public String getFilterString() {
        return filterString;
    }

    public String getFilterString2() {
        return filterString2;
    }

    public String getName() {
        return name;
    }

    public void setConfigurationName(String configurationName) {
        this.configurationName = configurationName;
    }

    public void setConnectionInfo(ExportConnectionInfo connectionInfo) {
        this.connectionInfo = Objects.requireNonNull(connectionInfo, "connectionInfo");
    }

    public void setCutoffDate(long cutoffDate) {
        this.cutoffDate = cutoffDate;
    }

    public void setExportTimestamp(long exportTimestamp) {
        this.exportTimestamp = exportTimestamp;
    }

    public void setFilterString(String filterString) {
        this.filterString = filterString;
    }

    public void setFilterString2(String filterString2) {
        this.filterString2 = filterString2;
    }

    public void setName(String name) {
        this.name = name;
    }
}
