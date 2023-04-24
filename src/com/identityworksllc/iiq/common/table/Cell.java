package com.identityworksllc.iiq.common.table;

import org.apache.commons.lang.StringEscapeUtils;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * A class representing a single cell in an HTML table. Cells can have differing
 * row and column spans, can have CSS classes or styles, and have content. The
 * content can be either a list or a string, which will be rendered differently.
 *
 * You can also specify that string content is HTML, which will be inserted as-is
 * without escaping. Be careful to not present an opportunity for script injection
 * if you use this.
 */
public class Cell extends Element implements StyleTarget {

    /**
     * Constructs a new Cell that can be passed to {@link Table#cell(Cell)}
     * @param content The content of the cell
     * @param options The cell options
     * @return The constructed Cell
     */
    public static Cell of(Object content, CellOption... options) throws GeneralException {
        return Cell.of(content, options == null ? Collections.emptyList() : Arrays.asList(options));
    }

    /**
     * Constructs a new Cell that can be passed to {@link Table#cell(Cell)}
     * @param content The content of the cell
     * @param options The cell options
     * @return The constructed Cell
     */
    public static Cell of(Object content, List<CellOption> options) throws GeneralException {
        if (!(content instanceof String || content instanceof Collection || content == null)) {
            throw new IllegalArgumentException("Cell values must be either String or Collection types");
        }

        Cell theCell = new Cell();
        if (content == null) {
            theCell.setContent("");
        } else {
            theCell.setContent(content);
        }
        if (options != null) {
            for (CellOption option : options) {
                option.accept(theCell);
            }
        }
        return theCell;
    }

    /**
     * The value of the 'colspan' attribute
     */
    private int colspan;

    /**
     * The content of the cell, which must be a string or list
     */
    private Object content;

    /**
     * If true, this will be a 'th' and not a 'td'
     */
    private boolean header;

    /**
     * If true, the content will be rendered as-is, rather than being escaped. Be
     * careful with this option, as it can allow HTML injection.
     */
    private boolean html;

    /**
     * The value of the 'rowspan' attribute for this cell
     */
    private int rowspan;

    /*package*/ Cell() {
        super();
        this.colspan = 1;
        this.rowspan = 1;
        this.html = false;
        this.header = false;
    }

    /**
     * Figures out how to append the string contents, whether HTML or not
     * @param builder The builder being used to construct the string
     * @param value The value
     */
    private void appendStringContents(StringBuilder builder, String value) {
        if (this.html) {
            builder.append(value);
        } else {
            builder.append(StringEscapeUtils.escapeHtml(value));
        }
    }

    public int getColspan() {
        return colspan;
    }

    public Object getContent() {
        return content;
    }

    public int getRowspan() {
        return rowspan;
    }

    public boolean isHeader() {
        return header;
    }

    public boolean isHtml() {
        return html;
    }

    /**
     * Renders this cell to HTML, inserting it into the builder
     */
    public void render(StringBuilder builder, Row parent) {
        String tag = "td";
        if (this.isHeader() || parent.isHeader()) {
            tag = "th";
        }
        builder.append("<").append(tag);
        if (!this.getCssClasses().isEmpty()) {
            builder.append(" class=\"").append(getEscapedCssClassAttr()).append("\"");
        }
        if (Util.isNotNullOrEmpty(getStyle())) {
            builder.append(" style=\"").append(getEscapedStyle()).append("\"");
        }
        if (this.colspan > 1) {
            builder.append(" colspan=\"").append(colspan).append("\"");
        }
        if (this.rowspan > 1) {
            builder.append(" rowspan=\"").append(rowspan).append("\"");
        }
        if (this.isHeader() || parent.isHeader()) {
            if (parent.isHeader()) {
                builder.append(" scope=\"col\"");
            } else if (this.isHeader()) {
                builder.append(" scope=\"row\"");
            }
        }
        builder.append(">");

        if (this.content instanceof String) {
            appendStringContents(builder, (String)this.content);
        } else if (this.content instanceof Collection) {
            for(Object o : (Collection<?>)this.content) {
                if (o instanceof String) {
                    builder.append(StringEscapeUtils.escapeHtml((String)o));
                    builder.append("<br/>");
                }
            }
        }

        builder.append("</").append(tag).append(">");
    }

    public void setColspan(int colspan) {
        this.colspan = colspan;
    }

    public void setContent(Object content) {
        this.content = content;
    }

    public void setHeader(boolean header) {
        this.header = header;
    }

    public void setHtml(boolean html) {
        this.html = html;
    }

    public void setRowspan(int rowspan) {
        this.rowspan = rowspan;
    }

}
