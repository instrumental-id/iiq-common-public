package com.identityworksllc.iiq.common.service;

import sailpoint.api.SailPointContext;
import sailpoint.object.ServiceDefinition;
import sailpoint.tools.GeneralException;

/**
 * Interface to ensure that nobody breaks the contract of {@link BaseCommonService}
 */
public interface BaseServiceImplementation {

    /**
     * Hook to execute after execution of the service implementation. If you
     * override this, be sure to invoke super.afterExecution() so that
     * the default behaviors still occur.
     *
     * @param context The IIQ context
     * @throws GeneralException if anything goes wrong
     */
    default void afterExecution(SailPointContext context) throws GeneralException {

    }

    /**
     * Hook to execute before execution of the service implementation. If you
     * override this, be sure to invoke super.afterExecution() so that
     * the default behaviors still occur.
     *
     * @param context The IIQ context
     * @throws GeneralException if anything goes wrong
     */
    default void beforeExecution(SailPointContext context) throws GeneralException {

    }

    /**
     * Gets the service definition
     * @return The service definition, supplied by the Servicer at runtime
     */
    ServiceDefinition getDefinition();

    /**
     * The main entry point of the service, invoked by the Servicer
     * @param context The IIQ context for this service run
     * @throws GeneralException if the service execution failed
     */
    void execute(SailPointContext context) throws GeneralException;

    /**
     * Your code goes here and will be invoked by {@link #execute(SailPointContext)} after some setup and validation
     *
     * @param context IIQ context
     * @throws GeneralException if any failures occur
     */
    void implementation(SailPointContext context) throws GeneralException;

    /**
     * Increments the execution count and sets the executed-once flag to true
     */
    void incrementExecutions();

    /**
     * Return the number of executions that should be skipped on service
     * startup. This can allow the IIQ server to start up without having
     * to worry about this service doing weird stuff during initialization
     * or heavy startup load.
     *
     * The subclass can override this, or the ServiceDefinition can specify
     * 'skipExecutionCount' as an attribute.
     *
     * @return The number of executions that should be skipped, default 0
     */
    default int skipExecutionCount() {
        ServiceDefinition self = getDefinition();
        if (self != null) {
            if (self.getAttributes().containsKey("skipExecutionCount")) {
                int skipExecutionCount = self.getInt("skipExecutionCount");
                if (skipExecutionCount > 0) {
                    return skipExecutionCount;
                }
            }
        }
        return 0;
    }

}
