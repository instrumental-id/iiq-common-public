package com.identityworksllc.iiq.common.minimal;

import sailpoint.object.SailPointObject;
import sailpoint.tools.Util;

import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * A comparator to sort SPOs in a reliable way: first by date, and if the dates are
 * identical, by ID. SPOs may have a null modified date, in which case the created
 * date is checked.
 */
public final class SailPointObjectDateSorter implements Comparator<SailPointObject> {

    /**
     * Returns the latest date for the given SPO, including modified if the flag
     * is set to true. If the object has no created or modified date (which will
     * happen if it's not yet saved), returns null.
     *
     * @param sailPointObject The SPO to get the date
     * @return The modified or created date, or null if none
     */
    public static Date latestDate(SailPointObject sailPointObject, boolean includeModified) {
        if (includeModified && sailPointObject.getModified() != null) {
            return sailPointObject.getModified();
        } else if (sailPointObject.getCreated() != null) {
            return sailPointObject.getCreated();
        } else {
            return null;
        }
    }

    /**
     * Sorts the given list of SPOs using this comparator
     * @param list The list to sort
     */
    public static void sort(List<? extends SailPointObject> list) {
        list.sort(new SailPointObjectDateSorter());
    }
    /**
     * If true, the modified date will be considered in addition to create
     */
    private final boolean includeModified;

    /**
     * SPO date sorter defaulting to checking modified dates
     */
    public SailPointObjectDateSorter() {
        this(true);
    }

    /**
     * SPO date sorter allowing you to specify whether you want to include modified dates
     */
    public SailPointObjectDateSorter(boolean includeModified) {
        this.includeModified = includeModified;
    }

    /**
     * @see Comparator#compare(Object, Object)
     */
    @Override
    public int compare(SailPointObject o1, SailPointObject o2) {
        // Ensures that nulls move to the start of the sorted list
        if (o1 == null && o2 == null) {
            return 0;
        } else if (o1 == null) {
            return 1;
        } else if (o2 == null) {
            return -1;
        }

        Date d1 = latestDate(o1, includeModified);
        Date d2 = latestDate(o2, includeModified);

        int compare = 0;

        // This is convoluted to account for the weird chance that one or both of the dates is null.
        // A null date will always go last in the list.
        if (!Util.nullSafeEq(d1, d2, true)) {
            if (d1 == null || d1.after(d2)) {
                compare = 1;
            } else if (d2 == null || d2.after(d1)) {
                compare = -1;
            }
        }

        if (compare == 0 && Util.isNotNullOrEmpty(o1.getId()) && Util.isNotNullOrEmpty(o2.getId())) {
            compare = o1.getId().compareTo(o2.getId());
        }

        return compare;
    }

}