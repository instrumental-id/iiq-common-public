package com.identityworksllc.iiq.common.minimal.iterators;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An indexing class for associating items in a list with their index in the list
 *
 * @param <T> The indexed type
 */
public final class Index<T> {
    /**
     * Implements an equivalent to Javascript's eachWithIndex. This is a shortcut
     * to {@link IndexingIterator}. If the input is null, a constant empty iterator
     * is returned.
     *
     * @param iterator The input list to iterate over
     * @param <In> The type of the values in the list
     */
    public static <In> Iterator<Index<In>> with(Iterator<? extends In> iterator) {
        if (iterator == null) {
            return Collections.emptyIterator();
        }

        return new IndexingIterator<>(iterator);
    }

    /**
     * Implements an equivalent to Javascript's eachWithIndex. If the input is
     * null or empty, a constant empty Iterable is returned.
     *
     * The output of this method is not itself a List.
     *
     * @param iterable The input list to iterate over
     * @param <In> The type of the values in the list
     */
    public static <In> Iterable<Index<In>> with(List<? extends In> iterable) {
        if (iterable == null || iterable.isEmpty()) {
            // Why does this syntax work? It's because the compiler is just looking for
            // something to substitute in as Iterable.iterator(), and this will
            // do just fine.
            return Collections::emptyIterator;
        }

        return () -> {
            final Iterator<? extends In> wrappedIterator = iterable.iterator();
            return with(wrappedIterator);
        };
    }

    /**
     * The integer index, starting with 0
     */
    private final int index;

    /**
     * The value at this index
     */
    private final T value;

    /**
     * Create a new indexed value
     *
     * @param value The value
     * @param index The index
     */
    public Index(T value, int index) {
        this.value = value;
        this.index = index;
    }

    /**
     * Gets the integer index value
     *
     * @return The integer index value
     */
    public int getIndex() {
        return index;
    }

    /**
     * The value at that position in the list
     *
     * @return The value at the list position
     */
    public T getValue() {
        return value;
    }
}
