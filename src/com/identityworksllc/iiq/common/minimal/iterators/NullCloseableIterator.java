package com.identityworksllc.iiq.common.minimal.iterators;

import sailpoint.tools.CloseableIterator;

import java.util.NoSuchElementException;

/**
 * A null class implementing CloseableIterator. The hasNext() method will always
 * return false and the next() method will always throw an exception.
 *
 * @param <T> The type being wrapped
 */
public class NullCloseableIterator<T> implements CloseableIterator<T> {

    /**
     * A blank NullCloseableIterator object
     */
    private static final NullCloseableIterator<?> INSTANCE = new NullCloseableIterator<>();

    /**
     * Returns a static singleton instance of NullCloseableIterator. Since
     * this class actually does nothing, this object is thread safe.
     *
     * @param <S> The type expected (which is irrelevant here)
     * @return The singleton iterator
     */
    @SuppressWarnings("unchecked")
    public static <S> NullCloseableIterator<S> getInstance() {
        return (NullCloseableIterator<S>) INSTANCE;
    }

    @Override
    public void close() {

    }

    @Override
    public boolean hasNext() {
        return false;
    }

    @Override
    public T next() {
        throw new NoSuchElementException();
    }
}
