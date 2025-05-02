package com.identityworksllc.iiq.common.task.export;

import com.identityworksllc.iiq.common.TaskUtil;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.logging.Log;
import sailpoint.api.SailPointContext;
import sailpoint.tools.GeneralException;

import java.sql.*;
import java.util.List;

/**
 * Partition to gather stats on the tables in an Oracle database
 */
public class OracleGatherStatsPartition extends ExportPartition {

    /**
     * The list of table names on which to gather stats
     */
    private final List<String> tableNames;

    /**
     * Constructs a new gather-stats partition
     * @param tableNames The table names
     */
    public OracleGatherStatsPartition(List<String> tableNames) {
        this.tableNames = tableNames;
    }

    /**
     * Executes the partition, in this case to gather stats
     * @param context The context
     * @param connection The connection to the target database
     * @param logger The logger
     * @throws GeneralException if anything fails with gathering stats
     */
    @Override
    public void export(SailPointContext context, Connection connection, Log logger) throws GeneralException {
        String command = "{ call dbms_stats.gather_table_stats('%s', '%s', cascade=>TRUE) }";

        try {
            DatabaseMetaData md = connection.getMetaData();
            String[] types = {"TABLE"};
            try (ResultSet rs = md.getTables(null, null, "DE_%", types)) {
                while(rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    String tableSchema = rs.getString("TABLE_SCHEM");

                    if (tableNames.contains(tableName)) {
                        String tableCommand = String.format(command, StringEscapeUtils.escapeSql(tableSchema), StringEscapeUtils.escapeSql(tableName));

                        TaskUtil.withLockedPartitionResult(monitor,
                                (partitionResult) -> monitor.updateProgress(partitionResult, "Gathering stats on " + tableName, -1));

                        logger.info("Gathering stats on " + tableName);

                        try(CallableStatement cstmt = connection.prepareCall(tableCommand)) {
                            cstmt.execute();
                        } catch(SQLException e) {
                            logger.error("Caught an error gathering stats on table " + tableName, e);
                            TaskUtil.withLockedPartitionResult(monitor,
                                    (partitionResult) -> partitionResult.addException(e));
                        }
                    }
                }
            }
        } catch(SQLException e) {
            logger.error("Caught an error gathering stats", e);
            throw new GeneralException(e);
        }
    }
}
