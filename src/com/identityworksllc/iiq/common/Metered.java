package com.identityworksllc.iiq.common;

import com.identityworksllc.iiq.common.annotation.CoreStable;
import sailpoint.api.Meter;
import sailpoint.tools.GeneralException;

/**
 * Starts a {@link Meter} with the given name, then invokes the callback, then
 * finally stops the meter before returning the output.
 *
 * Usage example:
 *
 * Metered.meter("Meter name", () -> {
 *     // your code here
 * })
 *
 */
@CoreStable
public class Metered {
    /**
     * An instance of this class can be used in a try-with-resources block or in Beanshell
     * with a try/finally block.
     */
    public static final class CloseableMeter implements AutoCloseable {
        /**
         * The name of the meter to stop
         */
        private final String meterName;

        /**
         * Starts the meter and stores the name for later
         * @param meterName The name of the meter to start
         */
        private CloseableMeter(String meterName) {
            Meter.enterByName(meterName);
            this.meterName = meterName;
        }

        /**
         * Stops the meter
         */
        @Override
        public void close() {
            Meter.exitByName(meterName);
        }
    }

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

    /**
     * Starts a meter with the given name, returning a CloseableMeter that can be used to
     * stop the meter when closed. This can be used in a try-with-resources block or in Beanshell
     * with a try/finally block.
     *
     * @param meterName The name of the meter to start
     * @return A CloseableMeter that can be used to stop the meter when closed
     */
    public static CloseableMeter start(String meterName) {
        return new CloseableMeter(meterName);
    }

    /**
     * Private utility constructor
     */
    private Metered() {

    }
}
