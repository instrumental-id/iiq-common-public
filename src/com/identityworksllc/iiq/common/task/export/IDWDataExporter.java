package com.identityworksllc.iiq.common.task.export;

import com.identityworksllc.iiq.common.request.SailPointWorkerExecutor;
import com.identityworksllc.iiq.common.threads.SailPointWorker;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.SailPointContext;
import sailpoint.object.*;
import sailpoint.task.AbstractTaskExecutor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A partitioned task for handling data exports. The task can be provided multiple filters
 * that should cover the entire set of desired export users.
 *
 * The partitions will run in three phases: the actual export, then a cleanup of any Links
 * no longer in IIQ, then a finalization step that sets the last run date.
 */
public class IDWDataExporter extends AbstractTaskExecutor {
    // TODO
    public static final List<String> SUFFIXES_16 =
            Arrays.asList("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "a", "b", "c", "d", "e", "f");

    // TODO
    public static final List<String> SUFFIXES_256 =
            Arrays.asList(
                    "00", "01", "02", "03", "04", "05", "06", "07", "08", "09", "0a", "0b", "0c", "0d", "0e", "0f", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "1a", "1b", "1c", "1d", "1e", "1f", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "2a", "2b", "2c", "2d", "2e", "2f", "30", "31", "32", "33", "34", "35", "36", "37", "38", "39", "3a", "3b", "3c", "3d", "3e", "3f", "40", "41", "42", "43", "44", "45", "46", "47", "48", "49", "4a", "4b", "4c", "4d", "4e", "4f", "50", "51", "52", "53", "54", "55", "56", "57", "58", "59", "5a", "5b", "5c", "5d", "5e", "5f", "60", "61", "62", "63", "64", "65", "66", "67", "68", "69", "6a", "6b", "6c", "6d", "6e", "6f", "70", "71", "72", "73", "74", "75", "76", "77", "78", "79", "7a", "7b", "7c", "7d", "7e", "7f", "80", "81", "82", "83", "84", "85", "86", "87", "88", "89", "8a", "8b", "8c", "8d", "8e", "8f", "90", "91", "92", "93", "94", "95", "96", "97", "98", "99", "9a", "9b", "9c", "9d", "9e", "9f", "a0", "a1", "a2", "a3", "a4", "a5", "a6", "a7", "a8", "a9", "aa", "ab", "ac", "ad", "ae", "af", "b0", "b1", "b2", "b3", "b4", "b5", "b6", "b7", "b8", "b9", "ba", "bb", "bc", "bd", "be", "bf", "c0", "c1", "c2", "c3", "c4", "c5", "c6", "c7", "c8", "c9", "ca", "cb", "cc", "cd", "ce", "cf", "d0", "d1", "d2", "d3", "d4", "d5", "d6", "d7", "d8", "d9", "da", "db", "dc", "dd", "de", "df", "e0", "e1", "e2", "e3", "e4", "e5", "e6", "e7", "e8", "e9", "ea", "eb", "ec", "ed", "ee", "ef", "f0", "f1", "f2", "f3", "f4", "f5", "f6", "f7", "f8", "f9", "fa", "fb", "fc", "fd", "fe", "ff"
            );

    // TODO
    public static final int VERSION = 1;

    private final Log logger;
    private final AtomicBoolean stopped;

    public IDWDataExporter() {
        this.stopped = new AtomicBoolean();
        this.logger = LogFactory.getLog(IDWDataExporter.class);
    }
    /**
     * @see sailpoint.object.TaskExecutor#execute(SailPointContext, TaskSchedule, TaskResult, Attributes)
     */
    @Override
    public void execute(SailPointContext context, TaskSchedule taskSchedule, TaskResult taskResult, Attributes<String, Object> attributes) throws Exception {
        String requestDefinitionName = attributes.getString("requestDefinitionName");
        if (Util.isNullOrEmpty(requestDefinitionName)) {
            requestDefinitionName = SailPointWorkerExecutor.REQUEST_DEFINITION;
        }
        RequestDefinition requestDefinition = context.getObjectByName(RequestDefinition.class, requestDefinitionName);
        if (requestDefinition == null) {
            throw new IllegalArgumentException("Request definition called " + requestDefinitionName + " does not exist; do you need to import it?");
        }

        taskResult.addMessage("Partitions will execute using the Request Definition called " + requestDefinitionName);

        List<ExportPartition> clusters = getPartitions(context, taskResult, attributes);

        List<Request> partitionRequests = new ArrayList<>();
        for(ExportPartition partition : clusters) {
            List<SailPointWorker> workerCluster = new ArrayList<>();
            workerCluster.add(partition);
            // Serializes the SailPointWorker object so that it can be persisted
            Request partitionRequest = SailPointWorker.toRequest(requestDefinition, workerCluster);
            partitionRequest.setName(partition.getName());
            partitionRequests.add(partitionRequest);
        }

        taskResult.addMessage(Message.info("Launching " + partitionRequests.size() + " partitions"));

        launchPartitions(context, taskResult, partitionRequests);
    }

    /**
     * Gets the list of partitions for the export operation. These will each have their 'phase'
     * attribute set so that they run in order.
     *
     * @param context The context
     * @param taskResult The task result for the parent task
     * @param attributes The attributes of the task execution
     * @return The resulting list of partitions to launch
     * @throws GeneralException if any failures occur
     */
    public List<ExportPartition> getPartitions(SailPointContext context, TaskResult taskResult, Attributes<String, Object> attributes) throws GeneralException {
        long now = System.currentTimeMillis();

        List<ExportPartition> partitions = new ArrayList<>();
        String configurationName = attributes.getString("configurationName");
        if (Util.isNullOrEmpty(configurationName)) {
            throw new GeneralException("A configurationName setting is required for the data export job");
        }

        int linkBatchSize = Util.otoi(attributes.get("linkBatchSize"));

        List<String> identityFilters = attributes.getStringList("identityFilters");
        List<String> linkFilters = attributes.getStringList("linkFilters");
        List<String> linkFilters2 = attributes.getStringList("linkFilters2");

        // Everything in one giant partition by default
        if (identityFilters == null || identityFilters.isEmpty()) {
            identityFilters = new ArrayList<>();
            identityFilters.add("id.notNull()");
        }

        // Everything in one giant partition by default
        if (linkFilters == null || linkFilters.isEmpty()) {
            linkFilters = new ArrayList<>();
            linkFilters.add("id.notNull()");
        }

        if (linkFilters2 == null || linkFilters2.isEmpty()) {
            linkFilters2 = new ArrayList<>();
            linkFilters2.add(null);
        }

        boolean doLinkCleanup = attributes.getBoolean("linkCleanup", true);

        long networkTimeout = attributes.getLong("networkTimeout");

        String driver = attributes.getString("driver");
        String url = attributes.getString("url");
        String username = attributes.getString("username");
        String password = attributes.getString("password");

        ExportConnectionInfo connectionInfo = new ExportConnectionInfo(url, username, password);
        connectionInfo.setDriver(driver);
        connectionInfo.setNetworkTimeout(networkTimeout);

        if (attributes.get("connectionProperties") instanceof Map) {
            Map<String, Object> props = Util.otom(attributes.get("connectionProperties"));
            for(String key : props.keySet()) {
                Object val = props.get(key);
                if (val != null) {
                    connectionInfo.getOptions().setProperty(key, Util.otoa(val));
                }
            }
        }

        String configHash = String.valueOf(Objects.hash(doLinkCleanup, linkFilters, linkFilters2, identityFilters, configurationName, connectionInfo));

        Map<String, Long> cutoffDates = new HashMap<>();

        String taskName = taskResult.getDefinition().getName();

        try (Connection connection = ExportPartition.openConnection(context, connectionInfo)) {
            try (PreparedStatement statement = connection.prepareStatement("select last_start_time, run_key, config_hash from de_runs where task_name = ? order by last_start_time desc")) {
                statement.setString(1, taskName);

                try (ResultSet results = statement.executeQuery()) {
                    while (results.next()) {
                        String key = results.getString("run_key");
                        String configHashString = results.getString("config_hash");
                        long lastStartTime = results.getLong("last_start_time");

                        if (Util.nullSafeEq(configHashString, configHash)) {
                            cutoffDates.put(key, lastStartTime);
                        } else {
                            logger.warn("For export partition " + key + ": new config hash = " + configHash + ", old config hash = " + configHashString);
                            taskResult.addMessage(Message.warn("Configuration has changed after last run of partition " + key + "; forcing a full export"));
                            cutoffDates.put(key, 0L);
                        }

                        logger.info("For export partition " + key + ": threshold timestamp = " + Instant.ofEpochMilli(cutoffDates.get(key)));
                    }
                }
            }
        } catch(SQLException e) {
            throw new GeneralException(e);
        }

        taskResult.setAttribute("cutoffDates", cutoffDates);

        int count = 1;
        for(String filter : Util.safeIterable(identityFilters)) {
            String lookup = "identity:" + filter;

            Long cutoffDate = cutoffDates.get(lookup);
            if (cutoffDate == null) {
                logger.warn("No existing threshold date for " + lookup + ", using " + Instant.ofEpochMilli(0));
                cutoffDate = 0L;
            }

            ExportIdentitiesPartition eip = new ExportIdentitiesPartition();
            eip.setName("Identity export partition " + count++);
            eip.setPhase(1);
            eip.setExportTimestamp(now);
            eip.setCutoffDate(cutoffDate);
            eip.setFilterString(filter);
            eip.setConnectionInfo(connectionInfo);
            eip.setConfigurationName(configurationName);
            eip.setTaskName(taskName);
            eip.setRunKey(lookup);
            eip.setConfigHash(configHash);

            partitions.add(eip);
        }

        count = 1;
        for(String filter : Util.safeIterable(linkFilters)) {
            Filter compiled1 = Filter.compile(filter);
            for(String filter2 : Util.safeIterable(linkFilters2)) {
                String lookup;

                if (Util.isNullOrEmpty(filter2)) {
                    lookup = "link:" + compiled1.getExpression(false);
                } else {
                    lookup = "link:" + Filter.and(compiled1, Filter.compile(filter2)).getExpression(false);
                }
                Long cutoffDate = cutoffDates.get(lookup);
                if (cutoffDate == null) {
                    logger.warn("No existing threshold date for " + lookup + ", using " + Instant.ofEpochMilli(0));
                    cutoffDate = 0L;
                }

                ExportLinksPartition elp = new ExportLinksPartition();
                elp.setName("Link export partition " + count++);
                elp.setPhase(2);
                elp.setDependentPhase(1);
                elp.setExportTimestamp(now);
                elp.setCutoffDate(cutoffDate);
                elp.setFilterString(filter);
                elp.setFilterString2(filter2);
                elp.setConnectionInfo(connectionInfo);
                elp.setConfigurationName(configurationName);
                elp.setTaskName(taskName);
                elp.setRunKey(lookup);
                elp.setConfigHash(configHash);

                if (linkBatchSize > 0) {
                    elp.setBatchSize(linkBatchSize);
                }

                partitions.add(elp);
            }
        }

        if (doLinkCleanup) {
            CleanupLinksPartition clp = new CleanupLinksPartition();
            clp.setPhase(3);
            clp.setDependentPhase(2);
            clp.setName("Clean up deleted Links");
            clp.setConnectionInfo(connectionInfo);
            clp.setRunKey("cleanup");
            clp.setTaskName(taskName);
            clp.setConfigHash(configHash);
            partitions.add(clp);
        }

        return partitions;
    }

    @Override
    public boolean terminate() {
        this.stopped.set(true);
        return true;
    }
}
