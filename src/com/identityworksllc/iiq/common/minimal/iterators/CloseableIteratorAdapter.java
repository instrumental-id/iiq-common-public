package com.identityworksllc.iiq.common.minimal.iterators;

import sailpoint.tools.CloseableIterator;

import java.util.Iterator;

/**
 * A wrapper class for IIQ's CloseableIterator that still implements CloseableIterator,
 * but also implement the broader Iterator and AutoClosable interfaces to allow regular
 * Java stuff to interact with it.
 *
 * @param <T> The type being iterated (usually ResourceObject)
 */
public class CloseableIteratorAdapter<T> implements Iterator<T>, CloseableIterator<T>, AutoCloseable {

    /**
     * The internally wrapped iterator
     */
    private final CloseableIterator<T> iterator;

    /**
     * Construct a new iterator wrapper.
     *
     * @param iterator The iterator to wrap. If null is provided, a {@link NullCloseableIterator} is substituted
     */
    public CloseableIteratorAdapter(CloseableIterator<T> iterator) {
        if (iterator == null) {
            this.iterator = new NullCloseableIterator<>();
        } else {
            this.iterator = iterator;
        }
    }

    /**
     * Invokes close on the wrapped iterator
     */
    @Override
    public void close() {
        this.iterator.close();
    }

    /**
     * Returns true if the wrapped iterator's hasNext() returns true
     * @return True if the iterator has more elements
     */
    @Override
    public boolean hasNext() {
        return this.iterator.hasNext();
    }

    /**
     * Returns the next element from the iterator
     * @return The next element from the iterator
     * @throws java.util.NoSuchElementException if the wrapped iterator was null or is exhausted
     */
    @Override
    public T next() {
        return this.iterator.next();
    }
}
