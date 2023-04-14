package com.identityworksllc.iiq.common.minimal.iterators;

import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An iterator that keeps track of its index, allowing you to get the current
 * element count.
 *
 * @param <In> The input iterator type
 */
public class IndexingIterator<In> implements Iterator<Index<In>> {
    /**
     * The index counter
     */
    private final AtomicInteger index;

    /**
     * The input iterator
     */
    private final Iterator<? extends In> input;

    /**
     * Wrapper constructor
     * @param input The input iterator
     */
    public IndexingIterator(Iterator<? extends In> input) {
        this.input = Objects.requireNonNull(input);
        this.index = new AtomicInteger(-1);
    }

    /**
     * @see Iterator#hasNext()
     */
    @Override
    public boolean hasNext() {
        return input.hasNext();
    }

    /**
     * @see Iterator#next()
     */
    @Override
    public Index<In> next() {
        return new Index<>(input.next(), index.incrementAndGet());
    }

    /**
     * Removes the current item from the list and decrements the index. The
     * next item returned will have the same index as the previous one. If
     * the wrapped iterator does not support removals, this method will throw
     * an appropriate exception without decrementing the counter.
     */
    @Override
    public void remove() {
        input.remove();
        index.decrementAndGet();
    }
}
