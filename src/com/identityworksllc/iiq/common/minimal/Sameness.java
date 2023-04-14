package com.identityworksllc.iiq.common.minimal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Utility methods for detecting sameness in two objects, since IIQ is inconsistent about it.
 * The primary method in here is {@link Sameness#isSame(Object, Object, boolean)}. This class
 * is heavily used throughout the IIQCommon libraries as well as the IDW plugins.
 */
public class Sameness {
    private static final Log log = LogFactory.getLog(Sameness.class);

    /**
     * Populates two HashSet collections with the appropriate values, taking into
     * account case sensitivity, and then returns true if they are the same.
     *
     * @param ignoreCase True if the comparison should ignore case
     * @param newSet The set containing 'new' values
     * @param oldSet The set containing 'old' values
     * @return True if the values are the same
     */
    private static boolean checkSets(boolean ignoreCase, HashSet<Object> newSet, HashSet<Object> oldSet) {
        if (ignoreCase) {
            newSet = newSet.stream().map(e -> String.valueOf(e).toUpperCase()).collect(Collectors.toCollection(HashSet::new));
            oldSet = oldSet.stream().map(e -> String.valueOf(e).toUpperCase()).collect(Collectors.toCollection(HashSet::new));
        }

        return newSet.equals(oldSet);
    }

    /**
     * Returns true if the given thing is empty in the Sameness sense, i.e.,
     * if it is the same as null. These are values that are often optimized
     * out by SailPoint when serializing to XML.
     *
     * A thing is empty if it is null or is an empty string, list, or map.
     * Boolean false and integer zero are also empty.
     *
     * All other values are not empty.
     *
     * @param thing The thing to check for emptiness
     * @return True if the thing is empty; false otherwise
     */
    public static boolean isEmpty(Object thing) {
        if (thing == null) {
            return true;
        }
        if (thing instanceof String) {
            return thing.equals("");
        } else if (thing instanceof Boolean) {
            return !((Boolean)thing);
        } else if (thing instanceof Number) {
            // I am questioning this. If this causes problems, it can be removed.
            int intValue = ((Number) thing).intValue();
            return (intValue == 0);
        } else if (thing.getClass().isArray()) {
            assert thing instanceof Object[];
            return ((Object[])thing).length == 0;
        } else if (thing instanceof Collection) {
            return ((Collection<?>) thing).isEmpty();
        } else if (thing instanceof Map) {
            return ((Map<?, ?>) thing).isEmpty();
        }
        return false;
    }

    /**
     * A typo-friendly inversion of {@link #isSame(Object, Object, boolean)}.
     *
     * @param newValue The new value (can be null)
     * @param oldValue The old value (can be null)
     * @param ignoreCase True if strings and collections should be compared ignoring case. Maps are always compared case-insensitively.
     * @return True if the values are NOT "the same" according to our definition
     */
    public static boolean isNotSame(final Object newValue, final Object oldValue, boolean ignoreCase) {
        return !isSame(newValue, oldValue, ignoreCase);
    }

    /**
     * Implements a more advanced algorithm to decide whether two objects are the same.
     *
     * This can be an expensive check and so should be used in concert with existing .equals(), e.g. o1.equals(o2) || isSame(o1, o2).
     *
     * 1) Type differences: If the two values are a String and a Boolean (or a String and a Number), but will be treated the same by Hibernate, they will be viewed as the same here.
     * 2) Null and empty: Nulls and empty objects (strings, lists, maps, boolean false) will be considered the same.
     * 3) Dates and Longs: If one value is a long and one is a Date, they will be compared as timestamps.
     * 4) Collections: Two collections will be considered equal if they have the same elements in any order. If ignoreCase is true, elements will be converted to strings and compared case-insensitively.
     * 5) String case: Two strings will be compared case-insensitive if the flag is passed as true
     * 6) String vs. Collection case: Sometimes the old value will be a string and the new a collection, or vice versa, if a multi-value field only has one entry or if the connector is weird. (AD OUs can do this.)
     *
     * @param newValue The new value (can be null)
     * @param oldValue The old value (can be null)
     * @param ignoreCase True if strings and collections should be compared ignoring case. Maps are always compared case-insensitively.
     * @return True if the values are "the same" according to our definition
     */
    public static boolean isSame(final Object newValue, final Object oldValue, boolean ignoreCase) {
        if (log.isTraceEnabled()) {
            log.trace("isSame() called with: newValue = [" + newValue + "], oldValue = [" + oldValue + "], ignoreCase = [" + ignoreCase + "]");
        }
        if (newValue == null || oldValue == null) {
            return isEmpty(newValue) && isEmpty(oldValue);
        } else if (newValue == oldValue) {
            return true;
        } else if (newValue instanceof Boolean && oldValue instanceof Boolean) {
            return newValue.equals(oldValue);
        } else if (newValue instanceof String && oldValue instanceof String) {
            if (ignoreCase) {
                return ((String) newValue).equalsIgnoreCase((String) oldValue);
            }
            return newValue.equals(oldValue);
        } else if (newValue instanceof Date && oldValue instanceof Long) {
            Date oldDate = new Date((Long)oldValue);
            return newValue.equals(oldDate);
        } else if (newValue instanceof Long && oldValue instanceof Date) {
            Date newDate = new Date((Long) newValue);
            return oldValue.equals(newDate);
        } else if (newValue.getClass().isArray() && isEmpty(newValue)) {
            return isEmpty(oldValue);
        } else if (oldValue.getClass().isArray() && isEmpty(oldValue)) {
            return isEmpty(newValue);
        } else if (newValue instanceof Collection && oldValue instanceof Collection) {
            HashSet<Object> newSet = new HashSet<>((Collection<?>) newValue);
            HashSet<Object> oldSet = new HashSet<>((Collection<?>) oldValue);
            return checkSets(ignoreCase, newSet, oldSet);
        } else if (newValue instanceof Map && oldValue instanceof Map) {
            return newValue.equals(oldValue);
        } else if (newValue instanceof String && oldValue instanceof Collection) {
            HashSet<Object> newSet = new HashSet<>();
            HashSet<Object> oldSet = new HashSet<>((Collection<?>)oldValue);
            newSet.add(newValue);
            return checkSets(ignoreCase, newSet, oldSet);
        } else if (newValue instanceof Collection && oldValue instanceof String) {
            HashSet<Object> newSet = new HashSet<>((Collection<?>) newValue);
            HashSet<Object> oldSet = new HashSet<>();
            oldSet.add(oldValue);
            return checkSets(ignoreCase, newSet, oldSet);
        } else {
            String ns = String.valueOf(newValue);
            String os = String.valueOf(oldValue);
            if (ignoreCase) {
                return ns.equalsIgnoreCase(os);
            } else {
                return ns.equals(os);
            }
        }
    }
}
