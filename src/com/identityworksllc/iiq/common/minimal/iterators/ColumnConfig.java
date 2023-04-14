package com.identityworksllc.iiq.common.minimal.iterators;

import com.identityworksllc.iiq.common.minimal.Utilities;
import sailpoint.api.SailPointContext;
import sailpoint.object.DynamicValue;
import sailpoint.object.ReportColumnConfig;
import sailpoint.object.Rule;
import sailpoint.object.Script;
import sailpoint.tools.Util;

import java.util.Map;

/**
 * A wrapper around the various ways of structuring report columns. This object produces
 * a standard column representation that can be consumed by various ResultSet processing
 * classes. The properties of a column that can be represented by this class are:
 *
 * - <code>column</code>: a required string that can be parsed by {@link ColumnToken}
 * - <code>ifEmpty</code>: an optional fallback column token that can be used if the first results in null
 * - <code>field</code>: a unique name for this column
 * - <code>header</code>: an optional header name for this column
 * - <code>renderScript</code>: an optional Beanshell script that can be used to further render a column value
 *
 * An instance of this class can be constructed from:
 *
 * - A String column token (parsed by {@link ColumnToken})
 * - A Map containing any of the above column arguments
 * - An IIQ {@link ReportColumnConfig} object (e.g., from a report executor)
 * - Another instance of this class (to copy)
 *
 * If a String column token is provided, the value for <code>field</code> will be the
 * first component of the token.
 *
 * An instance of ReportColumnConfig may contain vastly more properties than are used
 * here. This implementation is not guaranteed to have the same semantics as Sailpoint's
 * reporting use of that class.
 *
 */
public final class ColumnConfig {

    /**
     * The error returned if the input to the constructor is wrong
     */
    private static final String BAD_INPUT_ERROR = "Input must be a non-null String column token, a Sailpoint ReportColumnConfig, a Map, or a ColumnConfig";

    /**
     * The column token derived type separator
     */
    public static final String COLUMN_TYPE_SEPARATOR = ":";

    /**
     * The column token, which must be in a format recognized by {@link ResultSetIterator#deriveTypedValue(SailPointContext, Object, String)}}
     */
    private final String column;
    /**
     * The parsed column token, if a string column was provided
     */
    private ColumnToken columnToken;

    /**
     * The field name, which is used as the key for {@link ResultSetIterator#nextRow()} and can be arbitrary
     */
    private String field;
    /**
     * The header for this column, allowing friendly display of the results
     */
    private String header;
    /**
     * If not null, must contain a second column token that will be used as a fallback
     * if the original returns null
     */
    private String ifEmpty;

    /**
     * The parsed column token for the ifEmpty column
     */
    private ColumnToken ifEmptyColumnToken;
    /**
     * Stores a rule that can be used to render this column further
     */
    private Rule renderRule;
    /**
     * Stores a script that can be used to render this column further
     */
    private Script renderScript;
    /**
     * A wrapped {@link ReportColumnConfig} object, allowing use of this class in
     * report executors
     */
    private final ReportColumnConfig reportColumnConfig;

    /**
     * Constructs a ColumnConfig from the given input. The input must be a String, which
     * will be interpreted as a column token, a ReportColumnConfig object, which will be
     * taken as-is, a Map, or an instance of this class, which will be copied.
     *
     * If you specify a Map, it must at least contain a 'column' value.
     *
     * @param rcc The input argument
     */
    public ColumnConfig(Object rcc) {
        this(rcc, true);
    }

    /**
     * Constructs a ColumnConfig with a fallback property, which will be used if the
     * main property value is null.
     *
     * @param column         The column token to use first
     * @param fallbackColumn The column token to try if the first result is null (optional)
     */
    public ColumnConfig(String column, String fallbackColumn) {
        // NOTE: we pass false here because we need to calculate tokens after setting isEmpty
        this(column, false);
        this.ifEmpty = fallbackColumn;

        this.recalculateColumnTokens();
    }

    /**
     * Internal constructor that will optionally recalculate the tokens if true
     * is provided for the boolean parameter.
     * @param rcc The input argument
     * @param recalculateTokens True if we ought to recalculate tokens, false if the caller intends to do it manually
     */
    private ColumnConfig(Object rcc, boolean recalculateTokens) {
        if (rcc == null) {
            throw new IllegalArgumentException(BAD_INPUT_ERROR);
        }
        if (rcc instanceof ColumnConfig) {
            ColumnConfig other = (ColumnConfig) rcc;
            this.column = other.column;
            this.reportColumnConfig = other.reportColumnConfig;
            this.ifEmpty = other.ifEmpty;
            this.header = other.header;
            this.field = other.field;
            this.renderScript = other.renderScript;
            this.renderRule = other.renderRule;
        } else if (rcc instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> input = (Map<String, Object>) rcc;
            this.reportColumnConfig = null;
            this.column = Util.otoa(input.get("column"));
            this.ifEmpty = Util.otoa(input.get("ifEmpty"));
            this.field = Util.otoa(input.get("field"));
            this.header = Util.otoa(input.get("header"));
            this.renderScript = Utilities.getAsScript(input.get("renderScript"));
        } else {
            // Returns null if the object is not castable to this type
            this.reportColumnConfig = Utilities.safeCast(rcc, ReportColumnConfig.class);
            this.column = Utilities.safeCast(rcc, String.class);
            this.ifEmpty = null;
        }

        if (this.reportColumnConfig == null && this.column == null) {
            throw new IllegalArgumentException(BAD_INPUT_ERROR);
        }

        if (recalculateTokens) {
            this.recalculateColumnTokens();
        }
    }

    /**
     * Gets the parsed column token value
     * @return The parsed column token value
     */
    public ColumnToken getColumnToken() {
        return columnToken;
    }

    /**
     * Gets the field name of this column. This will be the key in the Map returned from
     * {@link ResultSetIterator#nextRow()} and {@link ResultSetIterator#getFieldHeaderMap()}.
     * It is NOT used in any way for SQL.
     * <p>
     * The result will be equal to:
     * <p>
     * 1) The value of this object's 'field' property if {@link #withFieldName} has been used
     * <p>
     * 2) the value of the wrapped ReportColumnConfig's 'field'
     * <p>
     * 3) Otherwise, it will be the base name of the main column without any type tokens.
     * If the property name is 'col1:timestamp:yyyy-MM-dd', the output of this method
     * would be 'col1'.
     * <p>
     * All other scenarios will result in an exception.
     *
     * @return The field name
     */
    public String getField() {
        String result = null;
        boolean finished = false;
        if (Util.isNotNullOrEmpty(this.field)) {
            result = this.field;
        } else {
            if (reportColumnConfig != null) {
                if (Util.isNotNullOrEmpty(reportColumnConfig.getField())) {
                    result = reportColumnConfig.getField();
                    finished = true;
                }
            }
            if (!finished) {
                result = this.columnToken.getBaseColumnName();
            }
        }
        if (result == null) {
            throw new IllegalStateException("Column defined as property token " + getProperty() + " produces null from getField()??");
        }
        return result;
    }

    /**
     * Gets the header mapped for this field. You can get a mapping from field names
     * to headers via {@link ResultSetIterator#getFieldHeaderMap()}.
     * <p>
     * The header will be derived as, in order:
     * <p>
     * 1. The header set via {@link #withHeader(String)}.
     * 2. The header column on the wrapped ReportColumnConfig.
     * 3. The result of {@link #getField()}
     *
     * @return The header for this column (never null)
     */
    public String getHeader() {
        if (Util.isNotNullOrEmpty(header)) {
            return header;
        } else if (reportColumnConfig != null) {
            if (Util.isNotNullOrEmpty(reportColumnConfig.getHeader())) {
                return reportColumnConfig.getHeader();
            }
        }
        return getField();
    }

    /**
     * Returns the 'fallback' column token string
     *
     * @return The column token string to use if the main column is null
     */
    public String getIfEmpty() {
        if (reportColumnConfig != null) {
            return reportColumnConfig.getIfEmpty();
        } else if (ifEmpty != null) {
            return this.ifEmpty;
        }
        return null;
    }

    /**
     * Gets the column token for the ifEmpty fallback column
     * @return The column token, if one exists, or else null
     */
    public ColumnToken getIfEmptyColumnToken() {
        return ifEmptyColumnToken;
    }

    /**
     * Returns the 'main' column token, e.g., "col1" or "col1:boolean". The first
     * part of this value will be the name of the column extracted from the SQL
     * ResultSet. The remaining parts will be used for type derivation, if needed.
     *
     * This will either be the value of 'column' or the property field on a
     * ReportColumnConfig. This is also the value parsed as the primary column
     * token.
     *
     * @return The column token string to read
     */
    public String getProperty() {
        if (reportColumnConfig == null) {
            return column;
        } else {
            return reportColumnConfig.getProperty();
        }
    }

    public DynamicValue getRenderDef() {
        if (renderScript != null || renderRule != null) {
            return new DynamicValue(renderRule, renderScript, null);
        } else if (reportColumnConfig != null) {
            return reportColumnConfig.getRenderDef();
        }
        return null;
    }

    /**
     * Recalculates the column tokens for this column config. This should be invoked
     * either in the constructor or manually after making changes to the class.
     */
    private void recalculateColumnTokens() {
        this.columnToken = new ColumnToken(this.getProperty());

        if (Util.isNotNullOrEmpty(getIfEmpty())) {
            this.ifEmptyColumnToken = new ColumnToken(getIfEmpty());
        }
    }

    /**
     * Constructs a copy of this ColumnConfig with the given field name.
     *
     * @param fieldName The field name to set in the copied ColumnConfig
     * @return A new ColumnConfig object with the field name set to that value
     */
    public ColumnConfig withFieldName(String fieldName) {
        ColumnConfig clone = new ColumnConfig(this, false);
        clone.field = fieldName;
        clone.recalculateColumnTokens();
        return clone;
    }

    /**
     * Constructs a copy of this ColumnConfig with the given header.
     *
     * @param header The header to set in the copied ColumnConfig
     * @return A new ColumnConfig object with the header
     */
    public ColumnConfig withHeader(String header) {
        ColumnConfig clone = new ColumnConfig(this, true);
        clone.header = header;
        return clone;
    }

    /**
     * Constructs a copy of this ColumnConfig with the given render rule. The Rule object
     * will NOT be copied, so you must ensure that it is properly detached from the context
     * or that the rules will always run in the context that loaded the Rule object.
     *
     * @param rule The rule object
     * @return A new ColumnConfig object with the renderRule set to the given Rule
     */
    public ColumnConfig withRenderRule(Rule rule) {
        ColumnConfig clone = new ColumnConfig(this, false);
        clone.renderRule = rule;
        clone.recalculateColumnTokens();
        return clone;
    }

    /**
     * Constructs a copy of this ColumnConfig with the given render script. The Script
     * object itself will also be copied to avoid caching thread-safety problems.
     *
     * @param script The render script
     * @return A new ColumnConfig object with the renderScript set to the given Script
     */
    public ColumnConfig withRenderScript(Script script) {
        ColumnConfig clone = new ColumnConfig(this, false);
        clone.renderScript = script;
        clone.recalculateColumnTokens();
        return clone;
    }
}
