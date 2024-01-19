package com.identityworksllc.iiq.common.task;

import com.identityworksllc.iiq.common.iterators.TransformingIterator;
import com.identityworksllc.iiq.common.task.BasicObjectRetriever.RetrievalType;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.ResourceObject;
import sailpoint.tools.CloseableIterator;
import sailpoint.tools.GeneralException;

import java.util.Iterator;
import java.util.Map;

/**
 * A more advanced {@link AbstractThreadedTask} that implements a "do this to these"
 * pattern. The task will retrieve its list of items via a rule, script, flat file, or
 * search filter. Subclasses are still responsible for implementing the
 * {@link #threadExecute(SailPointContext, Map, Object)} method.
 *
 * A 'retrievalType' must be specified that is one of rule, script, sql, file, connector,
 * provided, or filter, along with the appropriate arguments for that type of retrieval.
 *
 * The other arguments are the ones expected by {@link BasicObjectRetriever}. See that class
 * for details.
 *
 * If the output of your retrieval is not the type you want passed to your threadExecute, you must
 * override {@link #convertObject(Object)} and return the appropriately translated object. This class
 * assumes that the output of convertObject is the correct type. Returning a value of the incorrect
 * type will result in ClassCastExceptions.
 *
 * If you only want to support a subset of outputs, you may override {@link #supportsRetrievalType(RetrievalType)}
 * and return false for those you do not want to support.
 *
 * Subclasses must invoke super.parseArgs() if they override it to extract their own inputs.
 *
 * @param <ItemType> the type of object being iterated over
 */
public abstract class AbstractThreadedObjectIteratorTask<ItemType> extends AbstractThreadedTask<ItemType> {
    /**
     * Proxy to convert a Closeable iterator into an iterator
     */
    public static class CloseableIteratorWrapper implements Iterator<ResourceObject> {

        private final CloseableIterator<ResourceObject> closeableIterator;

        public CloseableIteratorWrapper(CloseableIterator<ResourceObject> closeableIterator) {
            this.closeableIterator = closeableIterator;
        }

        @Override
        public boolean hasNext() {
            return closeableIterator.hasNext();
        }

        @Override
        public ResourceObject next() {
            return closeableIterator.next();
        }
    }

    /**
     * Wraps the output iterators for transformation purposes.
     * This is how {@link #convertObject(Object)} gets called.
     */
    public class ResultTransformingIterator extends TransformingIterator<Object, ItemType> {

        /**
         * Constructor
         *
         * @param input The iterator being wrapped by this transformer
         */
        public ResultTransformingIterator(Iterator<?> input) {
            super(input, AbstractThreadedObjectIteratorTask.this::convertObject);
        }
    }

    /**
     * The ObjectRetriever to use to get the inputs
     */
    private ObjectRetriever<ItemType> retriever;

    /**
     * Converts the input to the expected type T. By default, this just
     * returns the input as-is. If you know what you're doing, you can leave
     * this implementation intact, but you probably want to convert things.
     *
     * If the result is null, the object will be ignored.
     *
     * @param input The input
     * @return The output object
     */
    @SuppressWarnings("unchecked")
    protected ItemType convertObject(Object input) {
        return (ItemType) input;
    }

    /**
     * Gets the iterator over the given object type
     * @param context Sailpoint context
     * @param args The task arguments
     * @return The iterator of items retrieved from whatever source
     * @throws GeneralException if any failures occur
     */
    protected Iterator<ItemType> getObjectIterator(SailPointContext context, Attributes<String, Object> args) throws GeneralException {
        retriever.setTerminationRegistrar(this::addTerminationHandler);
        return retriever.getObjectIterator(context, args);
    }

    /**
     * Gets the ObjectRetriever associated with this task execution
     *
     * @param context The context of the object retriever
     * @param args The arguments to the task
     * @return The object retriever
     * @throws GeneralException if any failure occurs
     */
    protected ObjectRetriever<ItemType> getObjectRetriever(SailPointContext context, Attributes<String, Object> args) throws GeneralException {
        return new BasicObjectRetriever<>(context, args, ResultTransformingIterator::new, taskResult);
    }

    /**
     * Termination indicator to be used by the subclasses
     * @return True if this task has been terminated
     */
    protected final boolean isTerminated() {
        return terminated.get();
    }

    /**
     * Parses the task arguments, determining the retrieval type and its arguments
     * @param args The task arguments
     * @throws Exception if any failures occur parsing the arguments
     */
    protected void parseArgs(Attributes<String, Object> args) throws Exception {
        super.parseArgs(args);

        this.retriever = getObjectRetriever(context, args);

        if (retriever instanceof BasicObjectRetriever) {
            RetrievalType retrievalType = ((BasicObjectRetriever<ItemType>)retriever).getRetrievalType();

            if (!supportsRetrievalType(retrievalType)) {
                throw new IllegalArgumentException("This task does not support retrieval type " + retrievalType);
            }
        }
    }

    /**
     * This method should return false if this task executor does not want to support the
     * given retrieval type.
     *
     * @param type The type of the retrieval
     */
    protected boolean supportsRetrievalType(RetrievalType type) {
        return true;
    }

}
