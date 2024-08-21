package com.identityworksllc.iiq.common.task.export;

import com.identityworksllc.iiq.common.request.SailPointWorkerExecutor;
import com.identityworksllc.iiq.common.threads.SailPointWorker;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Request;
import sailpoint.object.RequestDefinition;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.task.AbstractTaskExecutor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A partitioned task for handling data exports. The task can be provided multiple filters
 * that should cover the entire set of desired export users.
 *
 * The partitions will run in three phases: the actual export, then a cleanup of any Links
 * no longer in IIQ, then a finalization step that sets the last run date.
 */
public class IDWDataExporter extends AbstractTaskExecutor {
    private final AtomicBoolean stopped;

    public IDWDataExporter() {
        this.stopped = new AtomicBoolean();
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
            // Serializes the SailPointWorker object so that it can be saved
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

        String driver = attributes.getString("driver");
        String url = attributes.getString("url");
        String username = attributes.getString("username");
        String password = attributes.getString("password");

        ExportConnectionInfo connectionInfo = new ExportConnectionInfo(url, username, password);
        connectionInfo.setDriver(driver);

        long cutoffDate = 0L;
        String configHash = String.valueOf(Objects.hash(doLinkCleanup, linkFilters, linkFilters2, identityFilters, configurationName, connectionInfo));

        try (Connection connection = ExportPartition.openConnection(context, connectionInfo)) {
            try (PreparedStatement statement = connection.prepareStatement("select last_start_time, config_hash from de_runs order by last_start_time desc")) {
                try (ResultSet results = statement.executeQuery()) {
                    if (results.next()) {
                        String configHashString = results.getString(2);
                        taskResult.addMessage(Message.info("New config hash = " + configHash + ", old config hash = " + configHashString));
                        if (Util.nullSafeEq(configHashString, configHash)) {
                            cutoffDate = results.getLong(1);
                        } else {
                            taskResult.addMessage(Message.warn("Configuration has changed; forcing a full export"));
                        }
                    }
                }
            }
        } catch(SQLException e) {
            throw new GeneralException(e);
        }

        taskResult.addMessage(Message.info("Cutoff timestamp is: " + new Date(cutoffDate)));

        int count = 1;
        for(String filter : Util.safeIterable(identityFilters)) {
            ExportIdentitiesPartition eip = new ExportIdentitiesPartition();
            eip.setName("Identity export partition " + count++);
            eip.setPhase(1);
            eip.setExportTimestamp(now);
            eip.setCutoffDate(cutoffDate);
            eip.setFilterString(filter);
            eip.setConnectionInfo(connectionInfo);
            eip.setConfigurationName(configurationName);

            partitions.add(eip);
        }

        count = 1;
        for(String filter : Util.safeIterable(linkFilters)) {
            for(String filter2 : Util.safeIterable(linkFilters2)) {
                ExportLinksPartition eip = new ExportLinksPartition();
                eip.setName("Link export partition " + count++);
                eip.setPhase(2);
                eip.setDependentPhase(1);
                eip.setExportTimestamp(now);
                eip.setCutoffDate(cutoffDate);
                eip.setFilterString(filter);
                eip.setFilterString2(filter2);
                eip.setConnectionInfo(connectionInfo);
                eip.setConfigurationName(configurationName);

                partitions.add(eip);
            }
        }

        if (doLinkCleanup) {
            CleanupLinksPartition clp = new CleanupLinksPartition();
            clp.setPhase(3);
            clp.setDependentPhase(2);
            clp.setName("Clean up deleted Links");
            clp.setConnectionInfo(connectionInfo);
            partitions.add(clp);
        }

        ExportFinishPartition efp = new ExportFinishPartition();
        efp.setExportTimestamp(now);
        efp.setCutoffDate(cutoffDate);
        efp.setName("Finalize export");
        efp.setPhase(4);
        efp.setDependentPhase(2);
        efp.setConfigurationName(configurationName);
        efp.setConfigHash(configHash);
        partitions.add(efp);

        return partitions;
    }

    @Override
    public boolean terminate() {
        this.stopped.set(true);
        return true;
    }
}
