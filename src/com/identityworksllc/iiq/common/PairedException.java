package com.identityworksllc.iiq.common;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * This exception is to be used whenever an exception is caught inside of a catch
 * block. It will display details of both exceptions on printStackTrace and other
 * methods.
 *
 * The first exception will be treated like any other "cause" passed to an
 * exception, so it will be returned by {@link Throwable#getCause()}, while
 * the second exception will be stored separately as {@link #t2}.
 */
public final class PairedException extends Throwable {
    /**
     * The internal second exception
     */
    private final Throwable t2;

    /**
     * Constructs a new {@link PairedException} from the two throwables
     * @param t1 The first throwable
     * @param t2 The second throwable
     */
    public PairedException(Throwable t1, Throwable t2) {
        super(t1);
        Objects.requireNonNull(t2);
        this.t2 = t2;
    }

    /**
     * Constructs a new {@link PairedException} from the two throwables
     * @param t1 The first throwable
     * @param t2 The second throwable
     */
    public PairedException(String message, Throwable t1, Throwable t2) {
        super(message, t1);
        Objects.requireNonNull(t2);
        this.t2 = t2;
    }

    @Override
    public String getLocalizedMessage() {
        return new StringJoiner(",", PairedException.class.getSimpleName() + "[", "]")
                .add("m1=" + super.getLocalizedMessage())
                .add("m2=" + t2.getLocalizedMessage())
                .toString();
    }

    @Override
    public String getMessage() {
        return new StringJoiner(",", PairedException.class.getSimpleName() + "[", "]")
                .add("m1=" + super.getMessage())
                .add("m2=" + t2.getMessage())
                .toString();
    }

    /**
     * Gets the second cause in this paired exception
     * @return The second cause
     */
    public Throwable getSecondCause() {
        return t2;
    }

    /**
     * Gets the second cause's stack trace in this paired exception
     * @return The second cause's stack trace
     */
    public StackTraceElement[] getSecondStackTrace() {
        Objects.requireNonNull(t2);
        return t2.getStackTrace();
    }

    @Override
    public void printStackTrace(PrintWriter s) {
        super.printStackTrace(s);
        if (t2 != null) {
            t2.printStackTrace(s);
        }
    }

    @Override
    public void printStackTrace() {
        super.printStackTrace();
        if (t2 != null) {
            t2.printStackTrace();
        }
    }

    @Override
    public void printStackTrace(PrintStream s) {
        super.printStackTrace(s);
        if (t2 != null) {
            t2.printStackTrace(s);
        }
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", PairedGeneralException.class.getSimpleName() + "[", "]")
                .add("t1=" + getCause().toString())
                .add("t2=" + getSecondCause().toString())
                .toString();
    }
}
