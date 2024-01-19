package com.identityworksllc.iiq.common.task;

import com.identityworksllc.iiq.common.Functions;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.tools.GeneralException;

import java.util.Iterator;
import java.util.function.Consumer;

/**
 * An interface allowing different implementations of object retrievers to be used
 * with the abstract object iterator task model.
 *
 * By default, the task will use {@link BasicObjectRetriever}, which handles a variety
 * of Sailpoint-friendly retrieval options.
 *
 * @param <ItemType>
 */
public interface ObjectRetriever<ItemType> {
    /**
     * Returns an iterator over the expected type. The objects returned by the iterator
     * should be free of association with the top-level context, as they will be invoked
     * by child contexts.
     *
     * @param context The context used to load the objects if needed
     * @param arguments The arguments to the retrieval
     * @return An iterator over the desired objects
     * @throws GeneralException if retrieval fails for some reason
     */
    Iterator<ItemType> getObjectIterator(SailPointContext context, Attributes<String, Object> arguments) throws GeneralException;

    /**
     * A hook installer method allowing an object retriever and its calling class to
     * handle termination events together. By default, this does nothing.
     *
     * The expected API dance is:
     *
     *  1) The calling class invokes this method, passing its own custom consumer.
     *  2) The calling class invokes getObjectIterator().
     *  3) While retrieving objects, the object retriever may make one or more calls to the Consumer to register a termination handler.
     *  4) If the calling class is terminated, all handlers passed to the Consumer should be invoked.
     *
     * @param registar The registration hook, if any
     */
    default void setTerminationRegistrar(Consumer<Functions.GenericCallback> registar) {

    }
}
