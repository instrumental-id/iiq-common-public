package com.identityworksllc.iiq.common.table;

import java.util.List;

/**
 * An interface that classes can use to indicate that they can hold
 * styles
 */
public interface StyleTarget {

    public List<String> getCssClasses();

    public void setCssClasses(List<String> cssClasses);

    public void setStyle(String style);

    public String getStyle();

}
