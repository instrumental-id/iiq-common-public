package com.identityworksllc.iiq.common.minimal.iterators;

import com.identityworksllc.iiq.common.minimal.Functions;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.tools.CloseableIterator;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Function;

/**
 * A class that applies a transformation function to each item of an Iterator
 * before returning it from {@link Iterator#next()}. Basically the Iterator 
 * equivalent of {@link java.util.stream.Stream#map(Function)}.
 *
 * If you suppress nulls, all null transformed values will be skipped.
 *
 * This is a read-only iterator and does not support remove().
 *
 * @param <In> The input class type
 * @param <Out> The output class type
 */
public class TransformingIterator<In, Out> implements AutoCloseable, CloseableIterator<Out>, Iterator<Out> {
    /**
     * A functional interface similar to {@link java.util.function.Function}, except throwing an exception
     * @param <In> The input class type
     * @param <Out> The output class type
     */
    @FunctionalInterface
    public interface TransformerFunction<In, Out> {
        /**
         * Applies the transformation to the given input object, returning an object of the output type
         * @param input The input object to transform
         * @return The output object
         * @throws GeneralException if any failures occur
         */
        Out apply(In input) throws GeneralException;
    }
    /**
     * True after the finalizer is invoked, either on close() or the last hasNext()
     */
    private boolean finalized;
    /**
     * Something to invoke after the last item is read
     */
    protected Runnable finalizer;
    /**
     * If true, nulls output from the transformation will be dropped. This requires
     * lookahead behavior which will slightly alter the way this iterator works.
     */
    private boolean ignoreNulls;
    /**
     * The input iterator
     */
    private final Iterator<? extends In> input;
    /**
     * Tracks the last element if ignoreNulls is enabled
     */
    private Out lastElement;
    /**
     * Logger
     */
    private final Log log;
    /**
     * True after the first call to next() or hasNext()
     */
    private boolean started;
    /**
     * The output transformation function
     */
    protected TransformerFunction<In, Out> transformation;

    /**
     * Constructor
     * @param input The iterator being wrapped by this transformer
     */
    public TransformingIterator(Iterator<? extends In> input) {
        this.input = input;
        this.log = LogFactory.getLog(this.getClass());
    }

    /**
     * Constructor
     * @param input The iterator being wrapped by this transformer
     * @param transformation The transformation to apply to each element of the input iterator
     */
    public TransformingIterator(Iterator<? extends In> input, TransformerFunction<In, Out> transformation) {
        this.input = input;
        this.transformation = transformation;
        this.log = LogFactory.getLog(this.getClass());
    }

    /**
     * @see CloseableIterator#close()
     */
    @Override
    public void close() {
        /* Do nothing */
        if (input != null) {
            Util.flushIterator(input);
        }
        runFinalizer();
    }

    /**
     * @see Iterator#hasNext()
     */
    @Override
    public boolean hasNext() {
        boolean result;
        Objects.requireNonNull(transformation);
        started = true;
        if (ignoreNulls) {
            if (input.hasNext()) {
                In nextItem = input.next();
                try {
                    Out transformed = transformation.apply(nextItem);
                    while (transformed == null && input.hasNext()) {
                        nextItem = input.next();
                        transformed = transformation.apply(nextItem);
                    }
                    if (transformed == null) {
                        result = false;
                    } else {
                        lastElement = transformed;
                        result = true;
                    }
                } catch (GeneralException e) {
                    log.error("Caught an error doing the transform in TransformingIterator", e);
                    throw new IllegalStateException(e);
                }
            } else {
                result = false;
            }
        } else {
            result = input.hasNext();
        }

        if (!result) {
            runFinalizer();
        }

        return result;
    }

    /**
     * Sets the ignore nulls flag to true
     */
    public TransformingIterator<In, Out> ignoreNulls() {
        if (started) {
            throw new IllegalStateException("ignoreNulls() changes iterator behavior and must be called before any call to next() or hasNext()");
        }
        this.ignoreNulls = true;
        return this;
    }

    /**
     * @see Iterator#next()
     */
    @Override
    public Out next() {
        Objects.requireNonNull(transformation);

        started = true;
        if (Thread.interrupted()) {
            throw new IllegalStateException(new InterruptedException("Thread interrupted"));
        }
        if (ignoreNulls) {
            return lastElement;
        } else {
            In value = input.next();
            try {
                return transformation.apply(value);
            } catch (GeneralException e) {
                log.error("Caught an error doing the transform in TransformingIterator", e);
                throw new IllegalStateException(e);
            }
        }
    }

    /**
     * Runs the finalizer if it has not already been run
     */
    private synchronized void runFinalizer() {
        if (!finalized && this.finalizer != null) {
            this.finalizer.run();
            this.finalized = true;
        }
    }

    public TransformingIterator<In, Out> setFinalizer(Runnable finalizer) {
        this.finalizer = finalizer;
        return this;
    }
}
