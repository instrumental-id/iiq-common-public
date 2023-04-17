package com.identityworksllc.iiq.common.minimal.iterators;

import sailpoint.tools.CloseableIterator;
import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * An iterator that batches up the inputs into batches of size
 * 'batchSize', then returns them in chunks. The last batch may,
 * of course, be smaller than the batch size.
 * 
 * Similar to the Guava {@link com.google.common.collect.Iterators#partition(Iterator, int)}
 *
 * @param <ObjectType>
 */
public class BatchingIterator<ObjectType> implements AutoCloseable, CloseableIterator<List<ObjectType>>, Iterator<List<ObjectType>> {
    /**
     * The recommended batch size, which will be used for all but the final
     * batch (which may be smaller, obviously).
     */
    private final int batchSize;

    /**
     * The cached list of objects, which is calculated on hasNext() and then
     * returned on the next invocation of next().
     */
    private List<ObjectType> cachedList;

    /**
     * The list of inputs
     */
    private final Iterator<? extends ObjectType> input;

    /**
     * Initialized
     */
    private volatile boolean initialized;

    /**
     * Constructor
     *
     * @param input The iterator being wrapped by this transformer
     * @param batchSize The batch size
     */
    public BatchingIterator(Iterator<? extends ObjectType> input, int batchSize) {
        this.input = Objects.requireNonNull(input);
        this.batchSize = batchSize;
        this.initialized = false;
    }

    /**
     * Closes the iterator by flushing the input iterator
     */
    @Override
    public void close() {
        Util.flushIterator(input);
    }

    /**
     * Retrieves the next batch of items and returns true if the batch is not
     * empty. Note that if this iterator wraps something slow (e.g., an iterator
     * that is streaming from disk or something), hasNext() will take a while.
     *
     * You MUST call hasNext() before any call to next() will work properly.
     *
     * @return True if the batch is not empty and next() will return a value
     */
    @Override
    public boolean hasNext() {
        List<ObjectType> tempList = new ArrayList<>();
        int amount = 0;
        while (input.hasNext() && amount++ < batchSize) {
            tempList.add(input.next());
        }
        cachedList = tempList;
        this.initialized = true;
        return cachedList.size() > 0;
    }

    /**
     * Returns the next item, which is derived during hasNext(). The result
     * is always an immutable copy of the internal state of this class.
     *
     * @return The next item
     * @throws NoSuchElementException if hasNext() has not been invoked or if it returned false
     */
    @Override
    public List<ObjectType> next() {
        if (!this.initialized) {
            throw new IllegalStateException("You must call hasNext() first");
        }
        if (cachedList == null) {
            throw new NoSuchElementException("Iterator has been exhausted");
        }
        List<ObjectType> immutableCopy = Collections.unmodifiableList(new ArrayList<>(cachedList));
        cachedList = null;
        return immutableCopy;
    }
}
