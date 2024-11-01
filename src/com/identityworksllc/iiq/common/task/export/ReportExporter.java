package com.identityworksllc.iiq.common.task.export;

import com.identityworksllc.iiq.common.TaskUtil;
import com.identityworksllc.iiq.common.Utilities;
import com.identityworksllc.iiq.common.annotation.Experimental;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.SailPointContext;
import sailpoint.api.TaskManager;
import sailpoint.object.Attributes;
import sailpoint.object.JasperResult;
import sailpoint.object.PersistedFile;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.persistence.PersistedFileInputStream;
import sailpoint.task.AbstractTaskExecutor;
import sailpoint.task.TaskMonitor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JdbcUtil;
import sailpoint.tools.Message;
import sailpoint.tools.RFC4180LineIterator;
import sailpoint.tools.RFC4180LineParser;
import sailpoint.tools.Util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs a series of IIQ report tasks, then exports the contents of their CSV output
 * to a database table.
 *
 * TODO: make an ad hoc copy of the report to force CSV output and suppress emails
 */
@Experimental
public class ReportExporter extends AbstractTaskExecutor {
    private final Log log;
    private final AtomicBoolean terminated;

    public ReportExporter() {
        this.terminated = new AtomicBoolean();
        this.log = LogFactory.getLog(ReportExporter.class);
    }

    @Override
    public void execute(SailPointContext context, TaskSchedule taskSchedule, TaskResult taskResult, Attributes<String, Object> attributes) throws Exception {
        List<String> reportTaskDefNames = attributes.getStringList("reports");
        String driver = attributes.getString("driver");
        String url = attributes.getString("url");
        String username = attributes.getString("username");
        String password = attributes.getString("password");

        ExportConnectionInfo connectionInfo = new ExportConnectionInfo(url, username, password);
        connectionInfo.setDriver(driver);

        TaskMonitor monitor = new TaskMonitor(context, taskResult);

        Timestamp taskTimestamp = new Timestamp(System.currentTimeMillis());

        for(String reportIdOrName : reportTaskDefNames) {
            if (terminated.get()) {
                break;
            }

            TaskDefinition report = context.getObjectByName(TaskDefinition.class, reportIdOrName);
            if (report == null) {
                TaskUtil.withLockedMasterResult(monitor, (tr) -> {
                    tr.addMessage(Message.error("Unable to find report: {0}", reportIdOrName));
                });
                break;
            }

            String reportName = report.getName();

            monitor.forceProgress("Executing: " + reportName);

            TaskUtil.withLockedMasterResult(monitor, (tr) -> {
                tr.addMessage(Utilities.timestamp() + " " + reportName + ": Executing report task");
            });

            TaskManager taskManager = new TaskManager(context);
            TaskSchedule reportTask = taskManager.run(report, new HashMap<>());

            TaskResult reportOutput = null;

            boolean finished = false;
            while (reportOutput == null && !terminated.get()) {
                try {
                    reportOutput = taskManager.awaitTask(reportTask, 60);
                } catch(GeneralException e) {
                    if (e.toString().contains("Timeout waiting")) {
                        log.debug("Still waiting for task " + reportTask.getName());
                    } else {
                        throw e;
                    }
                }
            }

            if (terminated.get()) {
                break;
            }

            TaskUtil.withLockedMasterResult(monitor, (tr) -> {
                tr.addMessage(Utilities.timestamp() + " " + reportName + ": Finished running report");
            });

            if (reportOutput != null) {
                if (reportOutput.isError()) {
                    TaskUtil.withLockedMasterResult(monitor, (tr) -> {
                        tr.addMessage(Message.warn(Utilities.timestamp() + " " + reportName + ": Error running report {0}", tr.getErrors()));
                    });
                    continue;
                }

                JasperResult jasperResult = reportOutput.getReport();

                if (jasperResult != null) {
                    List<PersistedFile> fileList = jasperResult.getFiles();
                    if (fileList != null) {
                        Optional<PersistedFile> csvFileMaybe = Utilities.safeStream(fileList).filter(PersistedFile::isCsv).findFirst();
                        if (csvFileMaybe.isPresent()) {
                            monitor.forceProgress("Exporting: " + reportName);
                            PersistedFile csvFile = csvFileMaybe.get();
                            TaskUtil.withLockedMasterResult(monitor, (tr) -> {
                                tr.addMessage(Utilities.timestamp() + " " + reportName + ": exporting CSV file " + csvFile.getName());
                            });
                            String uuid = UUID.randomUUID().toString();
                            writeCsvContents(context, connectionInfo, taskTimestamp, uuid, report, csvFile);
                        } else {
                            log.warn("Report output did not contain a CSV file");
                            TaskUtil.withLockedMasterResult(monitor, (tr) -> {
                                tr.addMessage(Message.warn(Utilities.timestamp() + " " + reportName + ": report output did not contain a CSV file. Is it configured to produce one?"));
                            });
                        }
                    } else {
                        log.warn("Report output did not contain a list of files; do you need to check CSV on the list?");
                        TaskUtil.withLockedMasterResult(monitor, (tr) -> {
                            tr.addMessage(Message.warn(Utilities.timestamp() + " " + reportName + ": report output did not contain any files. Is it configured to produce one?"));
                        });
                    }
                } else {
                    log.warn("TaskResult did not contain a report object");
                    TaskUtil.withLockedMasterResult(monitor, (tr) -> {
                        tr.addMessage(Message.warn(Utilities.timestamp() + " " + reportName + ": report task result does not appear to contain a Jasper report result"));
                    });
                }
            } else {
                log.warn("TaskResult did not contain a report object for task: " + reportName);
            }
        }
    }

    /**
     * Stores the row to the databaes
     * @param rowInsert The insert prepared statement
     * @param report The report being invoked
     * @param rowIndex The ordinal row index
     * @param row The actual row data from the report
     * @param taskTimestamp The task timestamp
     * @throws SQLException if inserting the row fails
     */
    private void exportRow(PreparedStatement rowInsert, TaskDefinition report, int rowIndex, Map<String, String> row, Timestamp taskTimestamp, String uuid) throws SQLException {
        rowInsert.setString(1, report.getName());
        rowInsert.setString(2, uuid);
        rowInsert.setInt(3, rowIndex);
        rowInsert.setTimestamp(6, taskTimestamp);

        for(String key : row.keySet()) {
            String val = row.get(key);
            rowInsert.setString(4, key);
            rowInsert.setString(5, val);

            rowInsert.addBatch();
        }
    }

    /**
     * Opens the connection to the target database using the provided connection info
     * @param context The sailpoint context, used to decrypt the password
     * @param connectionInfo The provided connection info, extracted from the export task def
     * @return The open connection
     * @throws GeneralException if any failures occur
     */
    public Connection openConnection(SailPointContext context, ExportConnectionInfo connectionInfo) throws GeneralException {
        String decryptedPassword = context.decrypt(connectionInfo.getEncryptedPassword());
        return JdbcUtil.getConnection(connectionInfo.getDriver(), null, connectionInfo.getUrl(), connectionInfo.getUsername(), decryptedPassword, connectionInfo.getOptions());
    }

    @Override
    public boolean terminate() {
        this.terminated.set(true);
        return true;
    }

    /**
     * Writes the CSV contents from a report into the report export table
     * @param context The IIQ context
     * @param connectionInfo The connection info
     * @param taskTimestamp The task timestamp (set at start)
     * @param report The report taskdef
     * @param csvFile The CSV file report output
     * @throws SQLException if any DB failures occur
     * @throws IOException if any file read failures occur
     * @throws GeneralException if any IIQ failures occur
     */
    private void writeCsvContents(SailPointContext context, ExportConnectionInfo connectionInfo, Timestamp taskTimestamp, String uuid, TaskDefinition report, PersistedFile csvFile) throws SQLException, IOException, GeneralException {
        try (Connection connection = openConnection(context, connectionInfo); PreparedStatement rowInsert = connection.prepareStatement("insert into de_report_data ( report_name, run_uuid, row_index, attribute, value, insert_date ) values (?, ?, ?, ?, ?)")) {
            int batchCount = 0;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new PersistedFileInputStream(context, csvFile)))) {
                RFC4180LineIterator lineIterator = new RFC4180LineIterator(reader);
                RFC4180LineParser parser = new RFC4180LineParser(',');
                try {
                    String header = lineIterator.readLine();
                    if (header == null) {
                        // File is empty
                        log.info("File " + csvFile.getName() + " is empty");
                    } else {
                        List<String> headerElements = new ArrayList<>(parser.parseLine(header));
                        String line;
                        int rowIndex = 0;

                        while ((line = lineIterator.readLine()) != null && !terminated.get()) {
                            Map<String, String> row = new HashMap<>();
                            List<String> csvElements = parser.parseLine(line);
                            for (int i = 0; i < csvElements.size() && i < headerElements.size(); i++) {
                                String col = headerElements.get(i);
                                String val = csvElements.get(i);
                                if (Util.isNotNullOrEmpty(val)) {
                                    row.put(col, val);
                                }
                            }

                            if (!row.isEmpty()) {
                                exportRow(rowInsert, report, rowIndex, row, taskTimestamp, uuid);
                                batchCount++;
                            }

                            rowIndex++;

                            if (batchCount > 50) {
                                rowInsert.executeBatch();
                                batchCount = 0;
                            }

                        }

                        rowInsert.executeBatch();
                    }
                } finally {
                    lineIterator.close();
                }
            }
        }
    }
}
