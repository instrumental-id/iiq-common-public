package com.identityworksllc.iiq.common;

import sailpoint.tools.GeneralException;

import java.io.PrintStream;
import java.io.PrintWriter;

/**
 * This exception is to be used whenever an exception is caught inside of a catch
 * block. It will display details of both exceptions on printStackTrace and other
 * methods.
 *
 * This particular version extends GeneralException in order to allow throwing
 * via Sailpoint's API. All methods are delegated to an internal {@link PairedException}.
 */
public class PairedGeneralException extends GeneralException {
    /**
     * The delegated exception
     */
    private final PairedException exception;

    /**
     * Constructs a new PairedException from the two throwables
     * @param t1 The first throwable
     * @param t2 The second throwable
     */
    public PairedGeneralException(Throwable t1, Throwable t2) {
        exception = new PairedException(t1, t2);
    }

    public PairedGeneralException(String message, Throwable t1, Throwable t2) {
        exception = new PairedException(message, t1, t2);
    }

    @Override
    public Throwable fillInStackTrace() {
        return exception.fillInStackTrace();
    }

    @Override
    public Throwable getCause() {
        return exception.getCause();
    }

    @Override
    public String getLocalizedMessage() {
        return exception.getLocalizedMessage();
    }

    @Override
    public String getMessage() {
        return exception.getMessage();
    }

    /**
     * Gets the parent of the second exception
     * @return The stack trace for the second exception, via {@link PairedException#getSecondCause()} ()}
     */
    public Throwable getSecondCause() {
        return exception.getSecondCause();
    }

    /**
     * Gets the second stack trace
     * @return The stack trace for the second exception, via {@link PairedException#getSecondStackTrace()}
     */
    public StackTraceElement[] getSecondStackTrace() {
        return exception.getSecondStackTrace();
    }

    @Override
    public StackTraceElement[] getStackTrace() {
        return exception.getStackTrace();
    }

    @Override
    public Throwable initCause(Throwable cause) {
        return exception.initCause(cause);
    }

    @Override
    public void printStackTrace(PrintWriter s) {
        exception.printStackTrace(s);
    }

    @Override
    public void printStackTrace() {
        exception.printStackTrace();
    }

    @Override
    public void printStackTrace(PrintStream s) {
        exception.printStackTrace(s);
    }

    @Override
    public void setStackTrace(StackTraceElement[] stackTrace) {
        exception.setStackTrace(stackTrace);
    }

    @Override
    public String toString() {
        return exception.toString();
    }
}
