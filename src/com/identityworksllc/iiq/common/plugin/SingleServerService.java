package com.identityworksllc.iiq.common.plugin;

import sailpoint.api.SailPointContext;
import sailpoint.server.BasePluginService;
import sailpoint.tools.GeneralException;

/**
 * Abstract class to easily implement a Service that will only run on the
 * alphabetically lowest Server name. This allows background execution
 * of a task more reliably than the scheduler, but still prevents clashing
 * if more than one server runs the task at once.
 *
 * Extensions should implement {@link #implementation(SailPointContext)}.
 */
public abstract class SingleServerService extends BasePluginService {
    /**
     * Main method called by the Servicer, does some setup and passes off to the
     * single-server executor and ultimately {@link #implementation(SailPointContext)}.
     * @param context The IIQ context
     * @throws GeneralException on failures
     */
    @Override
    public final void execute(SailPointContext context) throws GeneralException {
        CommonPluginUtils.SingleServerExecute executor = this::implementation;
        if (getDefinition().getBoolean("trackTimestamps")) {
            executor = executor.andSaveTimestamps(getDefinition());
        }
        CommonPluginUtils.singleServerExecute(context, getDefinition(), executor);
        context.commitTransaction();
    }

    /**
     * Your code goes here and will run on exactly one server at a time.
     *
     * @param context IIQ context
     * @throws GeneralException if any failures occur
     */
    public abstract void implementation(SailPointContext context) throws GeneralException;
}
