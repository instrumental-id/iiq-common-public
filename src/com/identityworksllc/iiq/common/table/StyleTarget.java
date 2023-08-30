package com.identityworksllc.iiq.common.table;

import java.util.List;

/**
 * An interface that classes can use to indicate that they can hold
 * styles. This is used by {@link Table}-related classes.
 */
public interface StyleTarget {

    /**
     * Retrieves the set of CSS Classes attached to this object
     * @return The set of CSS class names
     */
    List<String> getCssClasses();

    /**
     * Gets the CSS style string for this object
     * @return The CSS style string
     */
    String getStyle();

    /**
     * Stores the set of CSS classes attached to this object
     * @param cssClasses The set of CSS class names
     */
    void setCssClasses(List<String> cssClasses);

    /**
     * Sets the HTML element's style property of this object
     * @param style The CSS style string
     */
    void setStyle(String style);

}
