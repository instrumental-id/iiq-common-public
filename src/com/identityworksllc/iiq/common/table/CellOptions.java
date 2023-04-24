package com.identityworksllc.iiq.common.table;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A class implementing a variety of static cell options. These can be passed to
 * a Cell constructed using {@link Cell#of(Object, CellOption...)} or to the builder
 * in {@link Table#cell(Object, CellOption...)}.
 */
public class CellOptions {
    /**
     * Modifies the given Cell to add the classes listed to the existing list
     */
    public static CellOption addCssClasses(String... classes) {
        if (classes != null) {
            List<String> classList = new ArrayList<>(Arrays.asList(classes));
            return (cell) -> cell.getCssClasses().addAll(classList);
        } else {
            return (cell) -> {
            };
        }
    }

    /**
     * Modifies the given Cell to have a column span of the given value
     *
     * @param span The column span
     */
    public static CellOption colspan(int span) {
        return (cell) -> cell.setColspan(span);
    }

    /**
     * Modifies the given cell to set it as a header (th vs td)
     */
    public static CellOption header() {
        return (cell) -> cell.setHeader(true);
    }

    /**
     * Modifies the given cell to set it as an HTML value
     */
    public static CellOption html() {
        return (cell) -> cell.setHtml(true);
    }

    /**
     * Modifies the given cell to set its rowspan
     *
     * @param span The row span value
     */
    public static CellOption rowspan(int span) {
        return (cell) -> cell.setRowspan(span);
    }

    /**
     * Modifies the given Cell to replace the class list with the ones listed
     */
    public static CellOption setCssClasses(String... classes) {
        if (classes != null) {
            List<String> classList = new ArrayList<>(Arrays.asList(classes));
            return (cell) -> cell.setCssClasses(classList);
        } else {
            return (cell) -> {
            };
        }
    }

    /**
     * Modifies the given cell to set its CSS style attribute
     *
     * @param style The CSS style string
     */
    public static CellOption style(String style) {
        return (cell) -> cell.setStyle(style);
    }
}
