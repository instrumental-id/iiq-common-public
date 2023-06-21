package com.identityworksllc.iiq.common.query;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * A utility to read DBMS output from an Oracle database. This allows your
 * stored procedures to emit logging output that can be consumed by other parts of
 * your codebase.
 *
 * Since DBMS_OUTPUT is specific to a session, output will be read in a separate
 * thread from your main process, so your JDBC driver must be thread-safe.
 *
 * Usage of this class looks like this:
 *
 *  Open a connection to the target DB
 *  Create new OracleDBMSOutputListener, registering your callback
 *  Invoke begin()
 *  Construct and invoke your other stored procedure(s)
 *   -> Your callback will receive asynchronous lines of DBMS_OUTPUT
 *  Close the OracleDBMSOutputListener
 *  Close the connection to the target DB
 *
 * When you're done reading output, invoke close() on this class to clean up
 * resources. Failure to do this will result in prepared statement resource
 * leaks.
 */
public class OracleDBMSOutputListener implements AutoCloseable {
    private class InternalWorker extends Thread {
        /**
         * Logger
         */
        private final Log logger = LogFactory.getLog(InternalWorker.class);

        /**
         * Flag indicating that we're done
         */
        private final AtomicBoolean done;

        /**
         * The callable statement that will actually execute the dbms_output commands
         * and return the next lines.
         */
        private final CallableStatement getLineStatement;

        /**
         * Constructs a new internal worker object for this Oracle DBMS Output listener
         * @throws SQLException if constructing the PL/SQL procedure fails
         */
        public InternalWorker() throws SQLException {
            this.setName("Oracle dbms_output listener " + System.currentTimeMillis());
            this.setDaemon(true);
            this.done = new AtomicBoolean();

            this.getLineStatement = connection.prepareCall(
                    "declare " +
                            " l_line varchar2(32767); " +
                            " l_done number; " +
                            " l_buffer long; " +
                            "begin " +
                            " loop " +
                            "   exit when length(l_buffer)+32767 > :maxbytes OR l_done = 1; " +
                            "   dbms_output.get_line( l_line, l_done ); " +
                            // Note that ::;::;:: is the divider here instead of a newline
                            "   l_buffer := l_buffer || l_line || '::;::;::'; " +
                            " end loop; " +
                            // Output variables here
                            " :done := l_done; " +
                            " :buffer := l_buffer; " +
                            "end;");
        }

        /**
         * Invokes the stored procedure repeatedly in a loop, waiting 10 milliseconds
         * if the dbms_output command indicates that there are no more lines or making
         * another call immediately if more lines are available.
         *
         * The consumer will be invoked for each such line read.
         */
        @Override
        public void run() {
            try {

                // DBMS_OUTPUT returns 0 if there are more lines, 1 otherwise
                this.getLineStatement.registerOutParameter(2, Types.INTEGER);

                // This is the buffer containing up to 10 complete lines
                this.getLineStatement.registerOutParameter(3, Types.VARCHAR);

                while (!done.get() && !this.isInterrupted() && !connection.isClosed()) {
                    // Up to 10 lines of max length
                    this.getLineStatement.setInt(1, 32767 * 10);

                    this.getLineStatement.executeUpdate();
                    boolean moreLines = (this.getLineStatement.getInt(2) == 0);
                    String lines = this.getLineStatement.getString(3);

                    if (lines != null) {
                        String[] lineArray = lines.split("::;::;::", -1);
                        for (String s : lineArray) {
                            lineConsumer.accept(s);
                        }
                    }

                    // If there are NOT more lines, we can afford to wait 10ms and yield to
                    // other processes. If there are more lines, we go read them immediately
                    // so that the running procedures don't hang.
                    if (!moreLines) {
                        try {
                            Thread.sleep(10L);
                        } catch(InterruptedException e) {
                            // Silently end
                            this.done.set(true);
                        }
                    }
                }
            } catch(Exception e) {
                logger.error("Caught an error in the line consumer worker", e);
            } finally {
                try {
                    this.getLineStatement.close();
                } catch(SQLException e) {
                    logger.error("Caught an error closing the DBMS_OUTPUT get line statement; possible resource leak!", e);
                }
            }
        }
    }

    /**
     * The connection to the database, which must be shared between the main
     * process and this thread.
     */
    private final Connection connection;

    /**
     * The object that will receive each line from DBMS_OUTPUT
     */
    private final Consumer<String> lineConsumer;

    /**
     * The maximum size of the DBMS OUTPUT buffer. Processes will hang if they try
     * to emit too much data faster than this class can read it.
     */
    private final int maxSize;

    /**
     * The internal worker thread, which will be started on the call to {@link #listen()}.
     */
    private InternalWorker worker;

    public OracleDBMSOutputListener(Connection dbConnection, Consumer<String> lineConsumer, int bufferSize) {
        this.connection = Objects.requireNonNull(dbConnection);
        this.lineConsumer = Objects.requireNonNull(lineConsumer);

        // The max size of the DBMS_OUTPUT buffer on the server. The DBMS_OUTPUT.PUT_LINE
        // call in your procedure will hang if the buffer is exceeded.
        this.maxSize = Math.max(bufferSize, 1024 * 500);
    }

    /**
     * Closes this listener by cleaning up the various worker and JDBC objects.
     * The worker will be interrupted and its getLineStatement closed if present.
     * The DBMS_OUTPUT will be toggled off for the current connection.
     *
     * @throws Exception if there are any errors closing the connections
     */
    @Override
    public void close() throws Exception {
        if (this.worker != null) {
            if (this.worker.isAlive()) {
                this.worker.done.set(true);
                this.worker.interrupt();
            }
        }

        if (!this.connection.isClosed()) {
            try (CallableStatement disableStatement = this.connection.prepareCall("begin dbms_output.disable; end;")) {
                disableStatement.execute();
            }
        }
    }

    /**
     * Enables DBMS_OUTPUT and starts up the background listener. This should be
     * invoked before your stored procedure is called.
     *
     * @throws SQLException if there is a problem starting DBMS_OUTPUT
     */
    public void listen() throws SQLException {
        // See this page on AskTom -
        // https://asktom.oracle.com/pls/apex/f?p=100:11:0::::P11_QUESTION_ID:45027262935845
        try (CallableStatement enableStatement = this.connection.prepareCall("begin dbms_output.enable(:1); end;")) {
            enableStatement.setInt(1, this.maxSize);
        }

        this.worker = new InternalWorker();
        this.worker.start();
    }
}
