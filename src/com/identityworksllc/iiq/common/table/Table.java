package com.identityworksllc.iiq.common.table;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Type-safe fluent API for creating HTML tables from data inputs. Usage examples:
 *
 * ```
 *  Table table = new Table();
 *
 *  (Populating a table with individual cell data)
 *  table
 *      .withCellClasses("cellClass")
 *      .row().header()
 *          .cell("Header A").withClass("abc")
 *          .cell("Header B")
 *      .row()
 *          .cell(val1)
 *          .cell(val2)
 *      .row()
 *          .cell(val3)
 *          .cell(val4);
 *
 *  (Populating a table with list data)
 *  table
 *      .row(headers).header()
 *      .row(list1).withClass("firstRowClass")
 *      .row(list2);
 *
 *  (Populating a table with lists of lists)
 *  List rowData = List.of(row1, row2, row3);
 *  table
 *      .row(headers).header()
 *      .rows(rowData);
 * ```
 *
 * @author Devin Rosenbauer
 * @author Instrumental Identity
 */
@SuppressWarnings("unused")
public class Table extends Element {
    private static class BuilderState {
        private final List<String> alwaysCellClasses;
        private final List<CellOption> alwaysCellOptions;
        private final List<String> alwaysRowClasses;
        private Cell currentCell;
        private Row currentRow;
        private boolean header;
        private boolean singleInsertCells;

        public BuilderState() {
            this.alwaysCellOptions = new ArrayList<>();
            this.alwaysCellClasses = new ArrayList<>();
            this.alwaysRowClasses = new ArrayList<>();
        }
    }

    /**
     * Table logger
     */
    private final static Log log = LogFactory.getLog(Table.class);

    /**
     * The current state of the table builder
     */
    private final transient BuilderState builderState;

    /**
     * The list of rows
     */
    private final List<Row> rows;

    /**
     * The table width (in percent). You should use CSS instead.
     */
    private int width;

    /**
     * Constructs a new table
     */
    public Table() {
        this.rows = new ArrayList<>();
        this.cssClasses = new ArrayList<>();
        this.builderState = new BuilderState();
        this.width = -1;
    }

    /**
     * Creates a new Table and populates it with the given row data and options
     * @param rows The row data to add
     * @param options The cell options
     * @throws GeneralException on failures applying the cell options
     */
    public Table(List<Object> rows, CellOption... options) throws GeneralException {
        this();
        this.rows(rows, options);
    }

    /**
     * Adds a new cell to the current row with the given value content
     * @param value The value to add
     */
    public Table cell(Object value, CellOption... options) throws GeneralException {
        if (!builderState.singleInsertCells) {
            throw new IllegalStateException("You cannot add a new cell() after invoking row(list) or rows(). Call row() first.");
        }
        if (!(value instanceof String || value instanceof Collection || value instanceof Cell || value == null)) {
            throw new IllegalArgumentException("Cell values must be either String or Collection types");
        }
        if (value instanceof Cell) {
            // Beanshell may not figure out which method to call, so we'll help it
            return cell((Cell)value);
        } else {
            if (this.builderState.currentRow == null) {
                throw new IllegalStateException("You must call row() before adding a cell");
            }
            Cell newCell = startCell();

            if (value == null) {
                newCell.setContent("");
            } else {
                newCell.setContent(value);
            }

            if (options != null) {
                for (CellOption option : options) {
                    option.accept(newCell);
                }
            }

            return this;
        }
    }

    /**
     * Adds a specified cell to the current row. The cell will be modified to add
     * the 'withCellClasses' classes, if any have been specified.
     *
     * @param cell The cell to add
     */
    public Table cell(Cell cell) throws GeneralException {
        if (!builderState.singleInsertCells) {
            throw new IllegalStateException("You cannot add a new cell() after invoking row(list) or rows(). Call row() first.");
        }
        if (this.builderState.currentRow == null) {
            throw new IllegalStateException("You must call row() before adding a cell");
        }

        // You may get here from Beanshell if you call cell(var), where var is null
        if (cell == null) {
            cell = startCell();
            cell.setContent("");
        }

        for(String c : Util.safeIterable(builderState.alwaysCellClasses)) {
            cell.getCssClasses().add(c);
        }

        this.builderState.currentRow.add(cell);

        for(CellOption cellOption : Util.safeIterable(builderState.alwaysCellOptions)) {
            cellOption.accept(cell);
        }

        this.builderState.currentCell = cell;
        return this;
    }

    /**
     * Clears the cell classes list set by {@link #withCellClasses(String...)}
     */
    public Table clearCellClasses() {
        this.builderState.alwaysCellClasses.clear();
        return this;
    }

    /**
     * Sets the current row or cell to be a header. If the current object
     * is a row, all cells in that row will be header cells.
     */
    public Table header() {
        if (this.builderState.currentCell != null) {
            this.builderState.currentCell.setHeader(true);
        } else if (this.builderState.currentRow != null) {
            this.builderState.header = true;
            this.builderState.currentRow.setHeader(true);
        } else {
            throw new IllegalStateException("You must call row() before setting the header flag");
        }
        return this;
    }

    /**
     * Creates a new header row and populates it with the given cell values
     * @param values The values to add
     */
    public Table header(List<Object> values) throws GeneralException {
        row();
        header();
        for(Object v : Util.safeIterable(values)) {
            cell(v);
        }
        return this;
    }

    /**
     * Adds a new HTML cell to the current row
     * @param value The HTML contents
     */
    public Table htmlCell(Object value) throws GeneralException {
        if (!(value instanceof String || value instanceof Collection)) {
            throw new IllegalArgumentException("Cell values must be either String or Collection types");
        }
        if (this.builderState.currentRow == null) {
            throw new IllegalStateException("You must call row() before adding a cell");
        }
        Cell newCell = startCell();
        newCell.setHtml(true);
        newCell.setContent(value);

        return this;
    }

    /**
     * Renders the table as HTML
     * @return The rendered HTML
     */
    public String render() {
        StringBuilder html = new StringBuilder();
        html.append("<table");
        if (!this.cssClasses.isEmpty()) {
            html.append(" class=\"").append(getEscapedCssClassAttr()).append("\"");
        }
        if (Util.isNotNullOrEmpty(this.style)) {
            html.append(" style=\"").append(getEscapedStyle()).append("\"");
        }
        if (width > 0) {
            html.append(" width=\"").append(width).append("%\"");
        }
        html.append(">");

        boolean inHeader = false;
        for(Row row : this.rows) {
            if (row.isHeader() && !inHeader) {
                html.append("<thead>");
                inHeader = true;
            } else if (!row.isHeader() && inHeader) {
                html.append("</thead><tbody>");
                inHeader = false;
            }
            row.render(html);
        }

        html.append("</tbody></table>");
        return html.toString();
    }

    /**
     * Starts a new row in the table
     */
    public Table row() {
        Row newRow = new Row();
        for(String c : Util.safeIterable(builderState.alwaysRowClasses)) {
            newRow.getCssClasses().add(c);
        }

        this.builderState.currentRow = newRow;
        this.builderState.currentCell = null;
        this.builderState.header = false;
        this.rows.add(newRow);
        this.builderState.singleInsertCells = true;
        return this;
    }

    /**
     * Creates a new (non-header) row and populates it with the given cell values.
     * This will NOT put the builder into cell mode, so all style/class operators will
     * apply to the row.
     *
     * If the value is a Cell object obtained via {@link Cell#of(Object, CellOption...)},
     * it will be inserted as-is. Otherwise, the provided CellOptions will be applied to
     * each cell as it is added.
     *
     * @param values The values to add
     * @param options Any cell options you wish to add to each cell
     */
    public Table row(List<Object> values, CellOption... options) throws GeneralException {
        row();
        for(Object v : Util.safeIterable(values)) {
            if (v instanceof Cell) {
                // Avoid an endless loop here
                cell((Cell)v);
            } else {
                cell(v, options);
            }
        }
        // We want 'withXX' operations after this to apply to the row
        this.builderState.currentCell = null;

        // We want cell() to fail until row() is called again
        this.builderState.singleInsertCells = false;
        return this;
    }

    /**
     * Accepts a set of row data and adds it to the output table. The input should
     * be a list of lists. Each item will be interpreted as input to {@link #row(List, CellOption...)}. All
     * non-list inputs will be quietly ignored.
     *
     * The current row and cell will remain blank afterwards, so you cannot use
     * builder syntax to modify the most recent cell or row.
     *
     * @param rowData The row data
     */
    public Table rows(List<Object> rowData, CellOption... cellOptions) throws GeneralException {
        for(Object row : Util.safeIterable(rowData)) {
            if (row instanceof Collection) {
                this.row();
                for(Object item : (Collection<?>)row) {
                    if (item instanceof Cell) {
                        this.cell((Cell)item);
                    } else {
                        this.cell(item, cellOptions);
                    }
                }
            } else {
                log.warn("Value passed to Table.rows() that is not a Collection");
            }
        }
        this.builderState.currentCell = null;
        this.builderState.currentRow = null;
        this.builderState.singleInsertCells = false;
        return this;
    }

    /**
     * To be used *after* populating the table: applies the given CellOption modifications
     * to the cells at the given column index in each row. If a given row does not have
     * enough cells, nothing will happen for that row.
     *
     * @param column The column index
     * @param options The cell options to apply
     */
    public Table setColumnCellOptions(int column, CellOption... options) throws GeneralException {
        if (options != null) {
            for (Row row : this.rows) {
                if (row.getCells().size() > column) {
                    Cell cell = row.getCells().get(column);
                    if (cell != null) {
                        for (CellOption option : options) {
                            option.accept(cell);
                        }
                    }
                }
            }
        }
        return this;
    }

    /**
     * To be used *after* populating the table: sets the given style to the
     * cells at the given column in each row.
     *
     * @param column The column index
     * @param style The style
     */
    public Table setColumnStyle(int column, String style) {
        for(Row row : this.rows) {
            if (row.getCells().size() > column) {
                Cell cell = row.getCells().get(column);
                if (cell != null) {
                    cell.setStyle(style);
                }
            }
        }
        return this;
    }

    /**
     * To be used *after* populating the table: appends the given style to the
     * cells at the given column in each row.
     *
     * @param column The column index
     * @param style The style
     */
    public Table setExtraColumnStyle(int column, String style) {
        for(Row row : this.rows) {
            if (row.getCells().size() > column) {
                Cell cell = row.getCells().get(column);
                if (cell != null) {
                    cell.setStyle(cell.getStyle() + " " + style);
                }
            }
        }
        return this;
    }

    /**
     * To be used *after* populating the table: sets the given style to the given
     * row, indexed starting from zero, including the header row.
     *
     * @param row The row index
     * @param options The options to apply to each cell in this row
     */
    public Table setRowCellOptions(int row, CellOption... options) throws GeneralException {
        if (this.rows.size() > row) {
            Row theRow = this.rows.get(row);
            if (options != null) {
                for(Cell cell : Util.safeIterable(theRow.getCells())) {
                    for (CellOption option : options) {
                        option.accept(cell);
                    }
                }
            }
        }
        return this;
    }

    /**
     * To be used *after* populating the table: sets the given style to the given
     * row, indexed starting from zero, including the header row.
     *
     * @param row The row index
     * @param style The style
     */
    public Table setRowStyle(int row, String style) {
        if (this.rows.size() > row) {
            Row theRow = this.rows.get(row);
            theRow.setStyle(style);
        }
        return this;
    }

    /**
     * Starts a new cell, moved here for re-use
     */
    private Cell startCell() throws GeneralException {
        Row currentRow = this.builderState.currentRow;
        Cell newCell = new Cell();
        for(String c : Util.safeIterable(builderState.alwaysCellClasses)) {
            newCell.getCssClasses().add(c);
        }
        currentRow.add(newCell);
        this.builderState.currentCell = newCell;
        if (this.builderState.header) {
            newCell.setHeader(true);
        }
        return newCell;
    }

    /**
     * Sets the table width to the given value, in percent
     * @param value The percentage
     */
    public Table width(int value) {
        this.width = value;
        return this;
    }

    /**
     * All future cells will have the given classes appended. Note that cells
     * already in the table will not have the classes added. You should call this
     * method before adding any cells.
     */
    public Table withCellClasses(String... classes) {
        this.builderState.alwaysCellClasses.addAll(Arrays.asList(classes));
        return this;
    }

    /**
     * Applies the given set of cell options to the current object.
     *
     * If applied to a row, it will apply the options to all cells
     * currently in the row and all cells added to the row in the future.
     *
     * If applied to a cell, it apply only to that cell.
     *
     * If applied to the table, it will apply to all cells in any row.
     *
     * @param options The options to apply to each relevant cell
     */
    public Table withCellOptions(List<CellOption> options) throws GeneralException {
        if (options != null) {
            if (this.builderState.currentCell != null) {
                for(CellOption option : options) {
                    option.accept(this.builderState.currentCell);
                }
            } else if (this.builderState.currentRow != null) {
                for(Cell cell : builderState.currentRow.getCells()) {
                    for(CellOption option : options) {
                        option.accept(cell);
                    }
                }
                this.builderState.currentRow.setOptions(new ArrayList<>(options));
            } else {
                this.builderState.alwaysCellOptions.clear();
                this.builderState.alwaysCellOptions.addAll(new ArrayList<>(options));
            }
        }
        return this;
    }

    /**
     * Applies the given set of cell options to either the current cell or the
     * current row. If applied to a row, it will apply the options to all cells
     * currently in the row and all cells added to the row in the future.
     *
     * @param options The options to apply to each relevant cell
     */
    public Table withCellOptions(CellOption... options) throws GeneralException {
        if (options != null) {
            withCellOptions(Arrays.asList(options));
        }
        return this;
    }

    /**
     * Adds the given CSS class to the current object
     * @param cssClasses The CSS class (or space-separated classes)
     */
    public Table withClass(String... cssClasses) {
        for(String cssClass : cssClasses) {
            if (this.builderState.currentCell != null) {
                this.builderState.currentCell.getCssClasses().add(cssClass);
            } else if (this.builderState.currentRow != null) {
                this.builderState.currentRow.getCssClasses().add(cssClass);
            } else {
                this.cssClasses.add(cssClass);
            }
        }
        return this;
    }

    /**
     * Resets the first row to be a header row, which will have its header flag
     * set and any given options applied to all cells in the row.
     *
     * @param options An optional list of CellOptions to apply to each cell in the row
     * @throws GeneralException On any failures applying the CellOptions
     */
    public Table withHeaderRow(CellOption... options) throws GeneralException {
        if (this.rows.size() > 0) {
            Row firstRow = this.rows.get(0);
            firstRow.setHeader(true);
            for(Cell c : Util.safeIterable(firstRow.getCells())) {
                c.setHeader(true);
                if (options != null) {
                    for(CellOption option : options) {
                        option.accept(c);
                    }
                }
            }
        }
        return this;
    }

    /**
     * Sets the style of the current item to the given value. If the current item is
     * not a row or cell, silently does nothing.
     * @param style The style
     */
    public Table withStyle(String style) {
        if (this.builderState.currentCell != null) {
            this.builderState.currentCell.setStyle(style);
        } else if (this.builderState.currentRow != null) {
            this.builderState.currentRow.setStyle(style);
        } else {
            this.setStyle(style);
        }
        return this;
    }
}
