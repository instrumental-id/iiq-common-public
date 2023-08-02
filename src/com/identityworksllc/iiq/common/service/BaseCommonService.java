package com.identityworksllc.iiq.common.service;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.SailPointContext;
import sailpoint.server.BasePluginService;
import sailpoint.server.Service;
import sailpoint.tools.GeneralException;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Abstract super-class for services. This class provides some minimal
 * services, such as tracking execution counts and last execution times,
 * then delegates to the {@link #implementation(SailPointContext)} method.
 */
public abstract class BaseCommonService extends Service implements BaseServiceImplementation {

    /**
     * The logger for use by this class
     */
    private static final Log _bcpsLogger = LogFactory.getLog(BaseCommonService.class);

    /**
     * The execution count for this service, available to sub-classes
     */
    protected AtomicBoolean executedOnce;

    /**
     * The execution count for this service, available to sub-classes
     */
    protected AtomicLong executionCount;

    /**
     * The last start time, stored by default in {@link #beforeExecution(SailPointContext)}
     */
    private final AtomicLong lastStartTime;

    /**
     * The actual callback to the user's implementation for this service
     */
    protected ServiceImplementationInvoker invoker;

    /**
     * Base common plugin service constructor
     */
    protected BaseCommonService() {
        this.executedOnce = new AtomicBoolean();
        this.executionCount = new AtomicLong();
        this.lastStartTime = new AtomicLong();
        this.invoker = this::implementation;
    }

    /**
     * @see BaseServiceImplementation#afterExecution(SailPointContext) 
     */
    @Override
    public void afterExecution(SailPointContext context) throws GeneralException {
        this.incrementExecutions();

        ServiceUtils.storeTimestamps(context, getDefinition(), this.lastStartTime.get());
    }

    /**
     * @see BaseServiceImplementation#beforeExecution(SailPointContext) (SailPointContext)
     */
    @Override
    public void beforeExecution(SailPointContext context) throws GeneralException {
        this.lastStartTime.set(System.currentTimeMillis());
    }

    /**
     * The main entry point of the service
     * 
     * @see Service#execute(SailPointContext) 
     * @param context The IIQ context for this service run
     * @throws GeneralException if the service execution failed
     */
    @Override
    public final void execute(SailPointContext context) throws GeneralException {
        beforeExecution(context);

        if (this.executionCount.get() >= skipExecutionCount()) {
            this.invoker.invokeImplementation(context);
        } else {
            _bcpsLogger.info("Skipping execution (count = " + this.executionCount.get() + ") of service " + getDefinition().getName());
        }

        afterExecution(context);
    }

    /**
     * Returns the elapsed runtime of this service, in milliseconds. Services
     * should use this method to stop after a certain timeout period has been
     * reached to avoid bogging down the {@link sailpoint.server.Servicer}.
     *
     * @return The elapsed runtime of this service, in milliseconds
     */
    protected long getElapsedRuntimeMillis() {
        return (System.currentTimeMillis() - this.lastStartTime.get());
    }
    
    /**
     * Increments the execution count and sets the executed-once flag to true
     */
    @Override
    public final void incrementExecutions() {
        this.executedOnce.set(true);
        this.executionCount.incrementAndGet();
    }

}
