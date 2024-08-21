package com.identityworksllc.iiq.common.task.export;

import com.identityworksllc.iiq.common.Utilities;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class ReportExporter extends AbstractTaskExecutor {
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

        for(String reportName : reportTaskDefNames) {
            TaskDefinition report = context.getObjectByName(TaskDefinition.class, reportName);
            if (report == null) {
                taskResult.addMessage(Message.warn("Unable to find report called: {0}", reportName));
                context.saveObject(taskResult);
                context.commitTransaction();
                continue;
            }

            monitor.forceProgress("Executing: " + reportName);

            TaskManager taskManager = new TaskManager(context);
            TaskResult reportOutput = taskManager.runSync(report, new HashMap<>());

            boolean reportExported = false;

            if (reportOutput != null) {
                JasperResult jasperResult = reportOutput.getReport();

                if (jasperResult != null) {
                    List<PersistedFile> fileList = jasperResult.getFiles();
                    if (fileList != null) {
                        Optional<PersistedFile> csvFileMaybe = Utilities.safeStream(fileList).filter(PersistedFile::isCsv).findFirst();
                        if (csvFileMaybe.isPresent()) {
                            PersistedFile csvFile = csvFileMaybe.get();
                            writeCsvContents(context, connectionInfo, taskTimestamp, report, csvFile);
                        } else {
                            log.warn("Report output did not contain a CSV file");
                        }
                    } else {
                        log.warn("Report output did not contain a list of files; do you need to check CSV on the list?");
                    }
                } else {
                    log.warn("TaskResult did not contain a report object");
                }
            } else {
                log.warn("TaskResult did not contain a report object for task: " + reportName);
            }
        }
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
    private void writeCsvContents(SailPointContext context, ExportConnectionInfo connectionInfo, Timestamp taskTimestamp, TaskDefinition report, PersistedFile csvFile) throws SQLException, IOException, GeneralException {
        try (Connection connection = openConnection(context, connectionInfo); PreparedStatement rowInsert = connection.prepareStatement("insert into de_report_data ( report_name, row_index, attribute, value, insert_date ) values (?, ?, ?, ?, ?)")) {
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

                        while ((line = lineIterator.readLine()) != null) {
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
                                exportRow(rowInsert, report, rowIndex, row, taskTimestamp);
                                batchCount++;
                            }

                            rowIndex++;

                            if (batchCount > 12) {
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

    private void exportRow(PreparedStatement rowInsert, TaskDefinition report, int rowIndex, Map<String, String> row, Timestamp taskTimestamp) throws SQLException {
        rowInsert.setString(1, report.getName());
        rowInsert.setInt(2, rowIndex);
        rowInsert.setTimestamp(5, taskTimestamp);

        for(String key : row.keySet()) {
            String val = row.get(key);
            rowInsert.setString(3, key);
            rowInsert.setString(4, val);

            rowInsert.addBatch();
        }
    }

    @Override
    public boolean terminate() {
        this.terminated.set(true);
        return true;
    }
}
