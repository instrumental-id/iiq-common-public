package com.identityworksllc.iiq.common.query;

import bsh.EvalError;
import sailpoint.api.SailPointFactory;
import sailpoint.object.Application;
import sailpoint.plugin.PluginBaseHelper;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JdbcUtil;
import sailpoint.tools.Util;

import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

/**
 * Encapsulates a variety of ways of opening connections to a database,
 * from Beanshell callbacks to connection info to specifying that we should
 * use the IIQ or Plugin DB.
 */
public class ConnectOptions {

    /**
     * A builder for this object
     */
    public static final class ConnectOptionsBuilder {

        private bsh.This bshThis;
        private Map<String, Object> connectionParams;
        private String connectionRetrievalMethod;
        private boolean iiqDatabase;
        private String password;
        private boolean pluginDatabase;
        private String url;
        private String username;

        private ConnectOptionsBuilder() {
        }

        public ConnectOptions build() {
            boolean hasDatabaseParams = false;
            if (Util.isNotNullOrEmpty(this.connectionRetrievalMethod)) {
                hasDatabaseParams = true;
            } else if (!Util.isEmpty(this.connectionParams)) {
                hasDatabaseParams = true;
            } else if (!Util.isAnyNullOrEmpty(url, username, password)) {
                hasDatabaseParams = true;
            }

            if (!hasDatabaseParams && !iiqDatabase && !pluginDatabase) {
                throw new IllegalArgumentException("Missing required input: You must specify any of: withIIQDatabase, withPluginDatabase, a connection retrieval method, connection parameters, or (url + username + password)");
            }

            ConnectOptions connectOptions = new ConnectOptions();
            connectOptions.pluginDatabase = this.pluginDatabase;
            connectOptions.bshThis = this.bshThis;
            connectOptions.iiqDatabase = this.iiqDatabase;
            connectOptions.connectionRetrievalMethod = this.connectionRetrievalMethod;
            connectOptions.username = this.username;
            connectOptions.url = this.url;
            connectOptions.connectionParams = this.connectionParams;
            connectOptions.password = this.password;
            return connectOptions;
        }

        public ConnectOptionsBuilder withBshThis(bsh.This bshThis) {
            this.bshThis = bshThis;
            return this;
        }

        public ConnectOptionsBuilder withConnectionParams(Map<String, Object> connectionParams) {
            this.connectionParams = connectionParams;
            return this;
        }

        public ConnectOptionsBuilder withConnectionRetrievalMethod(String connectionRetrievalMethod) {
            this.connectionRetrievalMethod = connectionRetrievalMethod;
            return this;
        }

        public ConnectOptionsBuilder withIiqDatabase() {
            this.iiqDatabase = true;
            return this;
        }

        public ConnectOptionsBuilder withPassword(String password) {
            this.password = password;
            return this;
        }

        public ConnectOptionsBuilder withPluginDatabase() {
            this.pluginDatabase = true;
            return this;
        }

        public ConnectOptionsBuilder withUrl(String url) {
            this.url = url;
            return this;
        }

        public ConnectOptionsBuilder withUsername(String username) {
            this.username = username;
            return this;
        }
    }

    /**
     * Creates a new builder for a {@link ConnectOptions}
     * @return The connect options object
     */
    public static ConnectOptionsBuilder builder() {
        return new ConnectOptionsBuilder();
    }

    /**
     * Gets connect options from an Application, where they ought to be stored in the
     * standard IIQ format expected by {@link JdbcUtil#getConnection(Map)}.
     * @param application The application object
     * @return A populated ConnectOptions
     */
    public static ConnectOptions fromApplication(Application application) {
        if (application == null || application.getAttributes() == null) {
            throw new IllegalArgumentException("Non-null application attributes expected in ConnectOptions.fromApplication()");
        }
        return builder().withConnectionParams(application.getAttributes()).build();
    }

    /**
     * Gets connect options from a Map, i.e., one stored in configuration
     * @param input The input connect options
     * @return if anything fails
     */
    public static ConnectOptions fromMap(Map<String, Object> input) {
        ConnectOptionsBuilder builder = new ConnectOptionsBuilder();

        if (input.get("username") != null) {
            builder.withUsername((String) input.get("username"));
        } else if (input.get("url") != null) {
            builder.withUrl((String) input.get("url"));
        } else if (input.get("password") != null) {
            builder.withPassword((String) input.get("password"));
        } else if (input.get("connectionParams") instanceof Map) {
            builder.withConnectionParams((Map<String, Object>) input.get("connectionParams"));
        } else if (Util.otob(input.get("iiqDatabase"))) {
            builder.withIiqDatabase();
        } else if (Util.otob(input.get("pluginDatabase"))) {
            builder.withPluginDatabase();
        }

        return builder.build();
    }

    /**
     * The 'this' callback, used to invoke Beanshell code from Java
     */
    private bsh.This bshThis;
    /**
     * The connection params
     */
    private Map<String, Object> connectionParams;
    /**
     * Gets the method used to retrieve a DB connection
     */
    private String connectionRetrievalMethod;
    /**
     * True if we should connect to the IIQ database
     */
    private boolean iiqDatabase;
    /**
     * The password used to retrieve a DB connection. This can be Sailpoint-encrypted
     * and will be decrypted only as needed to open a connection.
     */
    private String password;
    /**
     * True if we should connect to the plugin database
     */
    private boolean pluginDatabase;
    /**
     *
     */
    private String url;
    /**
     * The username to connect to the DB
     */
    private String username;

    /**
     * The constructor used by the builder
     */
    private ConnectOptions() {
        /* basic constructor */
    }

    /**
     * Copy constructor
     * @param other The other object
     */
    public ConnectOptions(ConnectOptions other) {
        this.bshThis = other.bshThis;

        if (other.connectionParams != null) {
            this.connectionParams = new HashMap<>(other.connectionParams);
        }

        this.connectionRetrievalMethod = other.connectionRetrievalMethod;

        this.iiqDatabase = other.iiqDatabase;

        this.password = other.password;

        this.pluginDatabase = other.pluginDatabase;


        this.url = other.url;
        this.username = other.username;
    }

    /**
     * Opens a connection to the database. The caller is responsible for closing it.
     *
     * @return The connection to the database
     * @throws GeneralException if anything fails
     */
    public Connection openConnection() throws GeneralException {
        if (iiqDatabase) {
            return ContextConnectionWrapper.getConnection();
        } else if (pluginDatabase) {
            return PluginBaseHelper.getConnection();
        } else if (Util.isNotNullOrEmpty(connectionRetrievalMethod)) {
            if (bshThis == null) {
                throw new IllegalArgumentException("Cannot specify a connectionRetrievalMethod without also specifying a bsh.This");
            }
            try {
                return (Connection) bshThis.invokeMethod(connectionRetrievalMethod, new Object[0]);
            } catch(EvalError e) {
                throw new GeneralException(e);
            }
        } else if (!Util.isEmpty(connectionParams)) {
            return JdbcUtil.getConnection(connectionParams);
        } else {
            if (Util.isAnyNullOrEmpty(url, username, password)) {
                throw new IllegalArgumentException("You must specify a 'url', 'username', and 'password', and one of them was null or empty");
            }

            String decryptedPassword = password;

            if (decryptedPassword.contains("ACP")) {
                decryptedPassword = SailPointFactory.getCurrentContext().decrypt(password);
            }

            return JdbcUtil.getConnection(null, null, url, username, decryptedPassword);
        }
    }

}
