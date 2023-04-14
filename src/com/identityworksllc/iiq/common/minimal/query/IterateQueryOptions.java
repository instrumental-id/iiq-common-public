package com.identityworksllc.iiq.common.minimal.query;

import bsh.EvalError;
import bsh.This;
import sailpoint.api.SailPointContext;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

/**
 * The input VO for {@link QueryUtil#iterateQuery(IterateQueryOptions)}, mainly to
 * collect all of the inputs in one place and allow variable inputs.
 */
@SuppressWarnings("unused")
public final class IterateQueryOptions {
    /**
     * A builder for IterateQueryInputs objects.
     * Contains a nested {@link com.identityworksllc.iiq.common.minimal.query.ConnectOptions.ConnectOptionsBuilder}
     */
    @SuppressWarnings("unused")
    public static final class IterateQueryOptionsBuilder {

        private bsh.This bshThis;
        private String callback;
        private final ConnectOptions.ConnectOptionsBuilder connectOptionsBuilder;
        private String query;
        private Map<String, Object> queryParams;

        private IterateQueryOptionsBuilder() {
            this.connectOptionsBuilder = ConnectOptions.builder();
        }

        /**
         * Validates the inputs and returns a new IterateQueryInputs object
         *
         * @return The new object
         * @throws IllegalArgumentException if any validations fail
         */
        public IterateQueryOptions build() throws IllegalArgumentException {
            if (Util.isNullOrEmpty(callback)) {
                throw new IllegalArgumentException("Missing required value: 'callback'");
            }
            if (bshThis == null) {
                throw new IllegalArgumentException("Missing required input: Beanshell 'this' object");
            }

            if (Util.isNullOrEmpty(query)) {
                throw new IllegalArgumentException("Missing required input: 'query'");
            }

            ConnectOptions connectOptions = connectOptionsBuilder.build();

            IterateQueryOptions iterateQueryInputs = new IterateQueryOptions();
            iterateQueryInputs.callback = this.callback;
            iterateQueryInputs.query = this.query;
            iterateQueryInputs.bshThis = this.bshThis;
            iterateQueryInputs.queryParams = this.queryParams;
            iterateQueryInputs.connectOptions = connectOptions;
            return iterateQueryInputs;
        }

        public IterateQueryOptionsBuilder withBshThis(bsh.This bshThis) {
            this.bshThis = bshThis;
            connectOptionsBuilder.withBshThis(bshThis);
            return this;
        }

        public IterateQueryOptionsBuilder withCallback(String callback) {
            this.callback = callback;
            return this;
        }

        public IterateQueryOptionsBuilder withConnectionParams(Map<String, Object> connectionParams) {
            connectOptionsBuilder.withConnectionParams(connectionParams);
            return this;
        }

        public IterateQueryOptionsBuilder withConnectionRetrievalMethod(String connectionRetrievalMethod) {
            connectOptionsBuilder.withConnectionRetrievalMethod(connectionRetrievalMethod);
            return this;
        }

        public IterateQueryOptionsBuilder withIiqDatabase() {
            connectOptionsBuilder.withIiqDatabase();
            return this;
        }

        public IterateQueryOptionsBuilder withPassword(String password) {
            connectOptionsBuilder.withPassword(password);
            return this;
        }

        public IterateQueryOptionsBuilder withPluginDatabase() {
            connectOptionsBuilder.withPluginDatabase();
            return this;
        }

        public IterateQueryOptionsBuilder withQuery(String query) {
            this.query = query;
            return this;
        }

        public IterateQueryOptionsBuilder withQueryParams(Map<String, Object> queryParams) {
            this.queryParams = queryParams;
            return this;
        }

        public IterateQueryOptionsBuilder withUrl(String url) {
            connectOptionsBuilder.withUrl(url);
            return this;
        }

        public IterateQueryOptionsBuilder withUsername(String username) {
            connectOptionsBuilder.withUsername(username);
            return this;
        }
    }

    /**
     * Returns a new builder object for creating an {@link IterateQueryOptions}
     *
     * @return The new builder object
     */
    public static IterateQueryOptionsBuilder builder() {
        return new IterateQueryOptionsBuilder();
    }

    /**
     * The 'this' callback, used to invoke Beanshell code from Java
     */
    private bsh.This bshThis;

    /**
     * The name of the Beanshell method, which must take a Map parameter,
     * that will be invoked for each row in the result set.
     */
    private String callback;
    /**
     * Connection options object
     */
    private ConnectOptions connectOptions;
    /**
     * The query to run
     */
    private String query;
    /**
     * Any query parameters to plug in
     */
    private Map<String, Object> queryParams;

    private IterateQueryOptions() {

    }

    /**
     * Copy constructor
     *
     * @param other The other object to copy
     */
    private IterateQueryOptions(IterateQueryOptions other) {
        this.bshThis = other.bshThis;
        this.callback = other.callback;

        this.query = other.query;

        if (other.queryParams != null) {
            this.queryParams = new HashMap<>(other.queryParams);
        }
        this.connectOptions = new ConnectOptions(other.connectOptions);
    }

    /**
     * Invoked by {@link QueryUtil#iterateQuery(IterateQueryOptions)} once per row in the
     * result set
     *
     * @param row a Map representing the current row from the result set
     * @throws GeneralException if anything goes wrong during the callback
     */
    public void doCallback(Map<String, Object> row) throws GeneralException {
        Object[] params = new Object[1];
        params[0] = row;

        try {
            bshThis.invokeMethod(this.callback, params);
        } catch (EvalError e) {
            throw new GeneralException(e);
        }
    }

    /**
     * Invoked by {@link QueryUtil#iterateQuery(IterateQueryOptions)} once per row in the
     * result set.
     *
     * @param row a Map representing the current row from the result set
     * @throws GeneralException if anything goes wrong during the callback
     */
    public void doParallelCallback(SailPointContext threadContext, Map<String, Object> row) throws GeneralException {
        if (Thread.currentThread().isInterrupted()) {
            throw new GeneralException("thread interrupted");
        }
        Object[] params = new Object[2];
        params[0] = threadContext;
        params[1] = row;

        try {
            bshThis.invokeMethod(this.callback, params);
        } catch (EvalError e) {
            throw new GeneralException(e);
        }
    }

    public This getBshThis() {
        return bshThis;
    }

    public String getCallback() {
        return callback;
    }

    public String getQuery() {
        return query;
    }

    public Map<String, Object> getQueryParams() {
        return queryParams;
    }

    public Connection openConnection() throws GeneralException {
        return connectOptions.openConnection();
    }

    /**
     * Returns a new {@link IterateQueryOptions} with a different set of query parameters.
     * This can be used to run the same query for various inputs without having to rebuild
     * the entire object.
     *
     * @param newParams The new query params
     * @return The new object, identical to this one than swapping out the query params
     */
    public IterateQueryOptions withNewQueryParams(Map<String, Object> newParams) {
        IterateQueryOptions newObject = new IterateQueryOptions(this);
        newObject.queryParams = newParams;
        return newObject;
    }
}