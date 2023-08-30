package com.identityworksllc.iiq.common;

import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Some constants that will be useful in many IIQ scenarios.
 */
public final class CommonConstants {
    /**
     * The Unix timestamp value in milliseconds of January 1 1900, midnight, UTC
     */
    public static final long EPOCH_1900 = -2208988800000L;

    /**
     * The Unix timestamp value in milliseconds of January 1 1950, midnight, UTC
     */
    public static final long EPOCH_1950 = -631152000000L;


    /**
     * A standard timestamp format
     */
    public static final String STANDARD_TIMESTAMP = "yyyy-MM-dd HH:mm:ss Z";

    /**
     * The CSV parser API as a function
     */
    public static final Function<String, List<String>> FUNC_CSV_PARSER = Util::csvToList;

    /**
     * The null or empty API as a function
     */
    public static final Function<String, Boolean> FUNC_STR_EMPTY = Util::isNullOrEmpty;

    /**
     * The not null or empty API as a function
     */
    public static final Function<String, Boolean> FUNC_STR_NOT_EMPTY = Util::isNotNullOrEmpty;

    /**
     * A List intended to be passed to context.search to retrieve ID only
     */
    public static final List<String> SEARCH_ID = listOf("id");

    /**
     * A List intended to be passed to context.search to retrieve id and name only
     */
    public static final List<String> SEARCH_ID_NAME = listOf("id", "name");

    /**
     * A List intended to be passed to context.search to retrieve useful fields specific to Links
     */
    public static final List<String> SEARCH_LINK_FIELDS = listOf("id", "application.id", "nativeIdentity", "identity.id");

    /**
     * The field number in the {@link #SEARCH_LINK_FIELDS} indicating the application ID
     */
    public static final int SEARCH_LINK_FIELD_APP_ID = 1;

    /**
     * The field number in the {@link #SEARCH_LINK_FIELDS} indicating the Link ID
     */
    public static final int SEARCH_LINK_FIELD_ID = 0;

    /**
     * The field number in the {@link #SEARCH_LINK_FIELDS} indicating the owning Identity ID
     */
    public static final int SEARCH_LINK_FIELD_IDENTITY = 3;

    /**
     * The field number in the {@link #SEARCH_LINK_FIELDS} indicating the Link Native Identity
     */
    public static final int SEARCH_LINK_FIELD_NATIVE_ID = 2;

    /**
     * A List intended to be passed to context.search to retrieve name only
     */
    public static final List<String> SEARCH_NAME = listOf("name");

    /**
     * The common attribute used for threading across multiple requests and tasks
     */
    public static final String THREADS_ATTR = "threads";

    /**
     * The common attribute name to use for filters
     */
    public static final String FILTER_ATTR = "filter";

    /**
     * Private method to simulate List.of in pre-10 versions of Java
     * @param values The values to add to the list
     * @param <T> The type of the values
     * @return The resulting unmodifiable list
     */
    @SafeVarargs
    private static <T> List<T> listOf(T... values) {
        List<T> list = new ArrayList<>();
        if (values != null) {
            Collections.addAll(list, values);
        }
        return Collections.unmodifiableList(list);
    }

    private CommonConstants() {
        /* This class is not intended to be instantiated */
    }
}
