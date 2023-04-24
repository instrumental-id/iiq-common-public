package com.identityworksllc.iiq.common.table;

import com.identityworksllc.iiq.common.iterators.ColumnConfig;
import com.identityworksllc.iiq.common.iterators.ResultSetIterator;
import com.identityworksllc.iiq.common.query.NamedParameterStatement;
import sailpoint.api.SailPointContext;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JdbcUtil;
import sailpoint.tools.Util;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An extension of Table to run a SQL query and export it. You must provide a
 * Connection (or way of getting one) and a list of column specs.
 *
 * The meat of the querying takes place in {@link ResultSetIterator}.
 */
public class QueryTable implements AutoCloseable, StyleTarget {
    /**
     * The column specs, recognized by {@link ColumnConfig}. At this
     * time that is Strings, other ColumnConfig objects (which will be cloned), or
     * ReportColumnConfig objects.
     */
    private final List<Object> columns;
    /**
     * The connection, which is assumed open until close() is invoked
     */
    private final Connection connection;
    /**
     * The context
     */
    private final SailPointContext context;

    /**
     * Set to true on render()
     */
    private final AtomicBoolean frozen;

    /**
     * The Table to be populated by the query output
     */
    private final Table table;

    /**
     * Constructs a n ew QueryTable with the given context, connection, and column
     * specifications. The column specs should be some object recognized by
     * the reporting class {@link ColumnConfig}.
     *
     * @param context    The context
     * @param connection The SQL connection, which must be open and ready to query
     * @param columns    A non-empty list of column specs
     */
    public QueryTable(SailPointContext context, Connection connection, List<Object> columns) {
        this.connection = Objects.requireNonNull(connection);
        this.context = Objects.requireNonNull(context);

        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("For QueryTable, 'columns' must contain at least one column specification");
        }

        this.table = new Table();
        this.columns = new ArrayList<>(columns);
        this.frozen = new AtomicBoolean();
    }

    /**
     * Constructs a new QueryTable with the given context and connection, as well
     * as a string list of column tokens.
     *
     * @param context    The context
     * @param connection The SQL connection, which must be open and ready to query
     * @param columns    A non-empty list of column tokens
     */
    public QueryTable(SailPointContext context, Connection connection, String... columns) {
        this(context, connection, Arrays.asList(columns));
    }

    /**
     * Constructs a new QueryTable with the given context and connection info, as well
     * as a string list of column tokens.
     *
     * @param context        The context
     * @param connectionInfo A map of connection info, with the same keys specified by connectors
     * @param columns        A non-empty list of column tokens
     */
    public QueryTable(SailPointContext context, Map<String, Object> connectionInfo, String... columns) throws GeneralException {
        this(context, JdbcUtil.getConnection(connectionInfo), columns);
    }

    /**
     * Constructs a new QueryTable with the given context and connection info, as well
     * as a string list of column tokens.
     *
     * @param context        The context
     * @param connectionInfo A map of connection info, with the same keys specified by connectors
     * @param columns        A non-empty list of column specs
     */
    public QueryTable(SailPointContext context, Map<String, Object> connectionInfo, List<Object> columns) throws GeneralException {
        this(context, JdbcUtil.getConnection(connectionInfo), columns);
    }

    /**
     * Adds an output column to this QueryTable
     *
     * @param columnConfig The column config
     * @return This object, for call chaining
     */
    public QueryTable addColumn(Object columnConfig) {
        if (columnConfig != null) {
            this.columns.add(columnConfig);
        }
        return this;
    }

    /**
     * Closes the connection
     *
     * @throws SQLException on failure to close the connection
     */
    @Override
    public void close() throws SQLException {
        if (this.connection != null) {
            this.connection.close();
        }
    }

    /**
     * Executes the given query with the given options. The query will be run
     * via {@link NamedParameterStatement}, so the arguments must be of a type
     * recognized by that class.
     *
     * @param queryString The query string, which must not be null or empty
     * @param arguments The list of arguments, if any
     * @return This object, for call chaining
     * @throws SQLException if any SQL failures occur
     * @throws GeneralException if any IIQ failures occur
     */
    public QueryTable executeQuery(String queryString, Map<String, Object> arguments) throws SQLException, GeneralException {
        if (this.frozen.get()) {
            throw new IllegalArgumentException("QueryTable.executeQuery() cannot be invoked twice for the same table");
        }
        if (Util.isNullOrEmpty(queryString)) {
            throw new IllegalArgumentException("The query passed to executeQuery() must not be null");
        }
        try (NamedParameterStatement statement = new NamedParameterStatement(connection, queryString)) {
            statement.setParameters(arguments);
            try (ResultSet results = statement.executeQuery()) {
                ResultSetIterator rsi = new ResultSetIterator(results, this.columns, context);

                // This is a ListOrderedMap, so the keys ought to be in order
                Map<String, String> fieldHeaderMap = rsi.getFieldHeaderMap();
                table.row().header();
                for(String key : fieldHeaderMap.keySet()) {
                    String header = fieldHeaderMap.get(key);
                    table.cell(header);
                }

                while(rsi.hasNext()) {
                    Map<String, Object> nextRow = rsi.nextRow();
                    table.row();
                    for(String key : fieldHeaderMap.keySet()) {
                        Object value = nextRow.get(key);
                        if (value == null) {
                            table.cell("");
                        } else if (value instanceof List) {
                            table.cell(value);
                        } else {
                            table.cell(Util.otoa(value));
                        }
                    }
                }

                this.frozen.set(true);
            }
        }

        return this;
    }

    /**
     * Gets the underlying table's CSS classes
     * @see Table#getCssClasses()
     */
    @Override
    public List<String> getCssClasses() {
        return table.getCssClasses();
    }

    /**
     * @see Table#getStyle()
     */
    @Override
    public String getStyle() {
        return table.getStyle();
    }

    /**
     * Renders the table resulting from a query
     * @return The rendered HTML
     * @see Table#render() 
     */
    public String render() {
        if (!this.frozen.get()) {
            throw new IllegalStateException("Execute a query via executeQuery() first");
        }
        return this.table.render();
    }

    /**
     * @see Table#setColumnCellOptions(int, CellOption...) 
     */
    public QueryTable setColumnCellOptions(int column, CellOption... options) throws GeneralException {
        table.setColumnCellOptions(column, options);
        return this;
    }

    /**
     * @see Table#setColumnStyle(int, String)
     */
    public QueryTable setColumnStyle(int column, String style) {
        table.setColumnStyle(column, style);
        return this;
    }

    /**
     * @see Table#setCssClasses(List)
     */
    public void setCssClasses(List<String> cssClasses) {
        table.setCssClasses(cssClasses);
    }

    /**
     * @see Table#setExtraColumnStyle(int, String) 
     */
    public QueryTable setExtraColumnStyle(int column, String style) {
        table.setExtraColumnStyle(column, style);
        return this;
    }

    /**
     * @see Table#setRowCellOptions(int, CellOption...)
     */
    public QueryTable setRowCellOptions(int row, CellOption... options) throws GeneralException {
        table.setRowCellOptions(row, options);
        return this;
    }

    /**
     * @see Table#setRowStyle(int, String)
     */
    public QueryTable setRowStyle(int row, String style) {
        table.setRowStyle(row, style);
        return this;
    }

    /**
     * @see Table#setStyle(String) 
     */
    public void setStyle(String style) {
        table.setStyle(style);
    }

    /**
     * @see Table#withClass(String...)
     */
    public QueryTable withClass(String... cssClasses) {
        table.withClass(cssClasses);
        return this;
    }

    /**
     * @see Table#withHeaderRow(CellOption...)
     */
    public QueryTable withHeaderRow(CellOption... options) throws GeneralException {
        table.withHeaderRow(options);
        return this;
    }


}
