package com.identityworksllc.iiq.common.table;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * A class representing a row in an HTML table. Rows can be header rows or not,
 * can have specific classes or styles applied, and contain Cells.
 */
public class Row extends Element implements StyleTarget {
    /**
     * The list of cells in this row
     */
    private List<Cell> cells;

    /**
     * True if this is a header row
     */
    private boolean header;

    /**
     * Options to be applied to any future cell added to this row
     */
    private List<CellOption> options;

    /**
     * Constructs a new row with all empty data
     */
    public Row() {
        this.cells = new ArrayList<>();
        this.cssClasses = new ArrayList<>();
        this.header = false;
        this.style = "";
        this.options = new ArrayList<>();
    }

    /**
     * Adds a new cell to the end of this row. THe cell will be modified according
     * to the cell options set via setOptions if any are set.
     * @param c The cell
     * @throws GeneralException if any of the CellOptions specified throw an error
     */
    public void add(Cell c) throws GeneralException {
        if (!Util.isEmpty(this.options)) {
            for(CellOption option : this.options) {
                option.accept(c);
            }
        }
        this.cells.add(c);
    }

    public List<Cell> getCells() {
        return cells;
    }

    public boolean isHeader() {
        return header;
    }

    /**
     * Renders this row (and all of its Cells) to HTML, writing to the provided StringBuilder
     * @param builder The StringBuilder to write to
     */
    public void render(StringBuilder builder) {
        builder.append("<tr");
        if (!this.cssClasses.isEmpty()) {
            builder.append(" class=\"").append(getEscapedCssClassAttr()).append("\"");
        }
        if (Util.isNotNullOrEmpty(style)) {
            builder.append(" style=\"").append(getEscapedStyle()).append("\"");
        }
        builder.append(">");

        for(Cell c : cells) {
            if (c != null) {
                c.render(builder, this);
            }
        }

        builder.append("</tr>");
    }

    public void setCells(List<Cell> cells) {
        this.cells = cells;
    }

    public void setHeader(boolean header) {
        this.header = header;
    }

    public void setOptions(List<CellOption> options) {
        this.options = options;
    }
}
