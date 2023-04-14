package com.identityworksllc.iiq.common.minimal.table;

import org.apache.commons.lang.StringEscapeUtils;
import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * The root class of Table, Row, and Cell, which allows common functions
 * for style, CSS class, and other common HTML elements
 */
public abstract class Element {
    /**
     * The list of CSS classes to append to this cell
     */
    protected List<String> cssClasses;
    /**
     * The value of the 'style' attribute for this cell
     */
    protected String style;

    protected Element() {
        this.cssClasses = new ArrayList<>();
        this.style = "";
    }

    public List<String> getCssClasses() {
        return cssClasses;
    }

    protected String getEscapedCssClassAttr() {
        StringBuilder output = new StringBuilder();
        for(String cls : Util.safeIterable(this.cssClasses)) {
            output.append(StringEscapeUtils.escapeHtml(cls.trim()));
            output.append(" ");
        }
        return output.toString().trim();
    }

    public String getStyle() {
        return style;
    }

    protected String getEscapedStyle() {
        if (Util.isNullOrEmpty(style)) {
            return style;
        }
        return StringEscapeUtils.escapeHtml(style.trim());
    }

    public void setCssClasses(List<String> cssClasses) {
        this.cssClasses = cssClasses;
    }

    public void setStyle(String style) {
        this.style = style;
    }
}
