package com.identityworksllc.iiq.common.tools;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import picocli.CommandLine;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.AuditEvent;
import sailpoint.object.Identity;
import sailpoint.plugin.PluginBaseHelper;
import sailpoint.plugin.SqlScriptExecutor;
import sailpoint.server.Auditor;
import sailpoint.server.Environment;
import sailpoint.server.SailPointConsole;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * A command-line tool to run one or more SQL Scripts via the Plugin SQL execution
 * tool.
 *
 * You need to use this via the Launcher entry point, aka the 'iiq' command. The Launcher
 * automatically loads command classes from WEB-INF, so it's sufficient for IIQ Common
 * to be there, along with picocli.
 *
 * Usage: ./iiq com.identityworksllc.iiq.common.tools.RunSQLScript path/to/script1 path/to/script2...
 */
@CommandLine.Command(name = "sql-script", synopsisHeading = "", customSynopsis = {
        "Usage: ./iiq com.identityworksllc.iiq.common.tools.RunSQLScript FILE [FILE...]"
}, mixinStandardHelpOptions = true)
@SuppressWarnings("unused")
public class RunSQLScript implements Callable<Integer> {

    /**
     * Logger
     */
    private static final Log log = LogFactory.getLog(RunSQLScript.class);

    /**
     * Main method, to be invoked by the Sailpoint Launcher. Defers immediately
     * to picocli to handle the inputs.
     *
     * @param args The command line arguments
     */
    public static void main(String[] args) {
        new CommandLine(new RunSQLScript()).execute(args);
    }

    /**
     * The password, which can be provided at the command line, or can be
     * interactively entered if not present.
     */
    @CommandLine.Option(names = {"-p", "--password"}, arity = "0..1", interactive = true)
    private String password;

    /**
     * If present, specifies that the commands ought to be run against the plugin
     * schema, not the identityiq schema.
     */
    @CommandLine.Option(names = {"--plugin-schema"}, description = "If specified, run the script against the plugin schema instead of the IIQ schema")
    private boolean pluginSchema;

    /**
     * The command line arguments, parsed as File locations
     */
    @CommandLine.Parameters(paramLabel = "FILE", arity = "1..", description = "One or more SQL scripts to execute", type = File.class)
    private List<File> sqlScripts = new ArrayList<>();

    /**
     * The username provided at the command line
     */
    @CommandLine.Option(names = {"-u", "--user"}, required = true, description = "The username with which to log in to IIQ", defaultValue = "spadmin", showDefaultValue = CommandLine.Help.Visibility.ALWAYS)
    private String username;

    /**
     * The main action, invoked by picocli after populating the parameters.
     *
     * @return The exit code
     * @throws Exception on failures
     */
    @Override
    public Integer call() throws Exception {
        SailPointContext context = SailPointFactory.createContext();
        try {
            if (Util.isEmpty(sqlScripts)) {
                throw new IllegalArgumentException("You must provide the path to at least one SQL file");
            }

            if (Util.isEmpty(username) || Util.isEmpty(password)) {
                throw new IllegalArgumentException("Missing authentication information");
            }

            Identity authenticated = context.authenticate(username, password);

            if (authenticated == null) {
                throw new SailPointConsole.AuthenticationException("Authentication failed");
            }

            Identity.CapabilityManager capabilityManager = authenticated.getCapabilityManager();

            if (!(capabilityManager.hasCapability("SystemAdministrator") || capabilityManager.hasRight("IIQCommon_SQL_Importer"))) {
                throw new SailPointConsole.AuthenticationException("User cannot access the SQL importer");
            }

            System.out.println("Logged in successfully as user: " + authenticated.getDisplayableName());

            for (File file : sqlScripts) {
                if (!file.exists()) {
                    throw new IllegalArgumentException("File not found: " + file.getPath());
                }

                if (!file.canRead()) {
                    throw new IllegalArgumentException("File exists but cannot be read: " + file.getPath());
                }

                System.out.println("Executing SQL script file: " + file.getPath());

                String script = Util.readFile(file);

                if (Util.isNotNullOrEmpty(script) || script.trim().isEmpty()) {
                    System.out.println(" WARNING: File was empty");
                }

                AuditEvent ae = new AuditEvent();
                ae.setAction("iiqCommonSqlImport");
                ae.setServerHost(Util.getHostName());
                ae.setSource(authenticated.getName());
                ae.setAttribute("file", file.getPath());
                ae.setTarget(Util.truncateFront(file.getPath(), 390));

                Auditor.log(ae);
                context.commitTransaction();

                try (Connection connection = getConnection()) {
                    // This is the plugin script executor. Why don't they use this
                    // at the console level? We'll use it anyway. We probably
                    // will want to extend this and write our own at some point,
                    // or use the MyBatis one.
                    SqlScriptExecutor scriptExecutor = new SqlScriptExecutor();
                    scriptExecutor.execute(connection, script);
                }
            }
        } finally {
            SailPointFactory.releaseContext(context, false);
        }

        return 0;
    }

    /**
     * Gets a connection to the database, depending on which flag is set
     * @return The connection to the database
     * @throws SQLException if a SQL exception occurs
     * @throws GeneralException if a general exception occurs
     */
    private Connection getConnection() throws SQLException, GeneralException {
        if (pluginSchema) {
            return PluginBaseHelper.getConnection();
        } else {
            return Environment.getEnvironment().getSpringDataSource().getConnection();
        }
    }
}
