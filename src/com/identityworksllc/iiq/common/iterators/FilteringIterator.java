package com.identityworksllc.iiq.common.iterators;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.DynamicValuator;
import sailpoint.api.SailPointContext;
import sailpoint.object.DynamicValue;
import sailpoint.object.Script;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Implements a Filtering Iterator that will return only items that match the
 * Predicate.
 * @param <T>
 */
public class FilteringIterator<T> implements Iterator<T> {

    /**
     * A predicate that returns true if a DynamicValue or a Script returns a
     * value that maps to Boolean true via {@link Util#otob(Object)}.
     * @param <T> The input type
     */
    public static class DynamicValuePredicate<T> implements Predicate<T> {

        private final SailPointContext context;
        private final Script dynamicScript;
        private final DynamicValue dynamicValue;

        public DynamicValuePredicate(SailPointContext context, DynamicValue dynamicValue) {
            Objects.requireNonNull(dynamicValue, "DynamicValue must not be null");
            this.context = context;
            this.dynamicValue = dynamicValue;
            this.dynamicScript = null;
        }

        public DynamicValuePredicate(SailPointContext context, Script dynamicScript) {
            Objects.requireNonNull(dynamicScript, "Script must not be null");
            this.context = context;
            this.dynamicScript = dynamicScript;
            this.dynamicValue = null;
        }

        /**
         * Returns true if the input item causes the DynamicValue or Script to
         * return a value mapped to true.
         *
         * @param t The input item to test
         * @return True if the script evaluates to true
         */
        @Override
        public boolean test(T t) {
            try {
                Map<String, Object> scriptArgs = new HashMap<>();
                scriptArgs.put("item", t);
                scriptArgs.put("value", t);
                scriptArgs.put("context", context);

                Object result = null;

                if (dynamicValue != null) {
                    DynamicValuator dynamicValuator = new DynamicValuator(dynamicValue);
                    result = dynamicValuator.evaluate(context, scriptArgs);
                } else if (dynamicScript != null) {
                    result = context.runScript(dynamicScript, scriptArgs);
                }
                return Util.otob(result);
            } catch(GeneralException e) {
                logger.debug("Caught an exception evaluating a dynamic value predicate", e);
            }
            return false;
        }
    }
    private static final Log logger = LogFactory.getLog(FilteringIterator.class);
    private final Iterator<T> base;
    private final Predicate<T> filter;
    private T nextObject;

    public FilteringIterator(Iterator<T> base, Predicate<T> filter) {
        this.base = base;
        this.filter = filter;
    }

    /**
     * Returns true if the wrapped iterator contains an element that matches
     * the Predicate
     *
     * @return True if there is a matching element, false otherwise
     */
    @Override
    public boolean hasNext() {
        boolean result = base.hasNext();
        if (result) {
            nextObject = base.next();
            if (!filter.test(nextObject)) {
                boolean found = false;
                while(base.hasNext()) {
                    nextObject = base.next();
                    if (filter.test(nextObject)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    result = false;
                }
            }
        }
        return result;
    }

    /**
     * Returns the next object from the feed
     * @return the next object
     */
    @Override
    public T next() {
        return nextObject;
    }

    /**
     * Throws an UnsupportedOperationException because this iterator looks ahead
     * and cannot retroactively support deletion of records
     */
    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }
}
