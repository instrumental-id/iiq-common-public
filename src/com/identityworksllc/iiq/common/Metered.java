package com.identityworksllc.iiq.common;

import sailpoint.api.Meter;
import sailpoint.tools.GeneralException;

/**
 * Starts a {@link Meter} with the given name, then invokes the callback, then
 * finally stops the meter before returning the output.
 */
public class Metered {
    /**
     * Callback interface where no output is required
     */
    @FunctionalInterface
    public interface MeterCallback {
        /**
         * Performs the action wrapped by the Meter
         * @throws GeneralException if anything goes wrong
         */
        void run() throws GeneralException;
    }

    /**
     * Callback interface where an output is required
     * @param <T> The output type
     */
    @FunctionalInterface
    public interface MeterCallbackWithOutput<T> {
        /**
         * Performs the action wrapped by the Meter, returning any output
         * @return The output of the action
         * @throws GeneralException if anything goes wrong
         */
        T run() throws GeneralException;
    }

    /**
     * Meters the invocation of the callback, including an output
     *
     * @param meterName The meter name
     * @param callback The callback to invoke
     * @param <T> The output type
     * @return The output of the callback
     * @throws GeneralException on any errors in the callback
     */
    public static <T> T meter(String meterName, MeterCallbackWithOutput<T> callback) throws GeneralException {
        Meter.enterByName(meterName);
        try {
            return callback.run();
        } finally {
            Meter.exitByName(meterName);
        }
    }

    /**
     * Meters the invocation of the callback, without an output
     *
     * @param meterName The meter name
     * @param callback The callback to invoke
     * @throws GeneralException on any errors in the callback
     */
    public static void meter(String meterName, MeterCallback callback) throws GeneralException {
        Meter.enterByName(meterName);
        try {
            callback.run();
        } finally {
            Meter.exitByName(meterName);
        }
    }
}
