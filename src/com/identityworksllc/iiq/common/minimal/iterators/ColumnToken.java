package com.identityworksllc.iiq.common.minimal.iterators;

import sailpoint.api.SailPointContext;
import sailpoint.tools.GeneralException;
import sailpoint.tools.RFC4180LineParser;
import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The generic implementation of a colon-separated column token, e.g., 'col1:blah:stuff'.
 * The first component of a column token is always the column name, as it would be recognized
 * by JDBC's ResultSet class. If present, the second component is always a type token.
 *
 * At this time, type tokens are understood by {@link ResultSetIterator#deriveTypedValue(SailPointContext, Object, String)}.
 * See that method's documentation for the accepted type token values.
 *
 * If a third (or more) component is present, they will be considered arguments to the type
 * interpreter and vary by type. For example, a timestamp type may accept a date format.
 *
 * Token components are split using Sailpoint's {@link RFC4180LineParser}, meaning that quotes
 * are respected like a typical CSV.
 */
public final class ColumnToken {
    /**
     * The base column name
     */
    private String baseColumnName;

    /**
     * Any type parameters passed after the 2nd argument
     */
    private final List<String> typeParameters;

    /**
     * The type token
     */
    private String typeToken;

    /**
     * Creates a new column token object from the input
     *
     * @param input The input string of the form described in deriveTypedValue
     */
    public ColumnToken(String input) {
        if (Util.isNullOrEmpty(input)) {
            throw new IllegalArgumentException("Column token string cannot be null or empty");
        }

        this.typeParameters = new ArrayList<>();

        try {
            RFC4180LineParser parser = new RFC4180LineParser(ColumnConfig.COLUMN_TYPE_SEPARATOR);
            List<String> pieces = parser.parseLine(input);

            if (pieces.size() == 1) {
                this.baseColumnName = pieces.get(0);
                this.typeToken = null;
            } else if (pieces.size() >= 2) {
                this.baseColumnName = pieces.get(0);
                this.typeToken = pieces.get(1);
                if (pieces.size() >= 3) {
                    for (int p = 2; p < pieces.size(); p++) {
                        String piece = pieces.get(p);
                        if (piece == null) {
                            piece = "";
                        }
                        this.typeParameters.add(piece);
                    }
                }
            }
        } catch (GeneralException e) {
            throw new IllegalArgumentException("Unparseable input: " + input, e);
        }
    }

    /**
     * Returns the base column name (the start of the token)
     *
     * @return The base column name
     */
    public String getBaseColumnName() {
        return baseColumnName;
    }

    /**
     * Returns the given type parameter, or null if the index given is out of bounds
     *
     * @param index The type parameter index
     * @return The value requested, or null if not defined
     */
    public String getTypeParameter(int index) {
        if (index < this.typeParameters.size() && index >= 0) {
            return this.typeParameters.get(index);
        } else {
            return null;
        }
    }

    /**
     * Gets the list of type parameters, which is always non-null, but may be empty
     *
     * @return The list of type parameters
     */
    public List<String> getTypeParameters() {
        return Collections.unmodifiableList(typeParameters);
    }

    /**
     * Gets the type token part of the string, e.g., 'xml' or 'timestamp'
     *
     * @return The type token part of the input string
     */
    public String getTypeToken() {
        return typeToken;
    }
}
