package com.identityworksllc.iiq.common.task;

import sailpoint.api.SailPointContext;
import sailpoint.tools.GeneralException;

import java.util.Map;

/**
 * A consumer interface for executing an operation on an item in a private thread
 * @param <T> The type of the object being consumed
 */
@FunctionalInterface
public interface PrivateContextObjectConsumer<T> {
    /**
     * Executes the operation on the item
     *
     * @param threadContext A private context for the current thread
     * @param parameters Any relevant input parameters for the operation
     * @param obj The item on which to operate
     * @return An arbitrary output, which can be ignored or not
     * @throws GeneralException if any failures occur during execution
     */
    Object threadExecute(SailPointContext threadContext, Map<String, Object> parameters, T obj) throws GeneralException;
}
