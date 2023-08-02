package com.identityworksllc.iiq.common.plugin;

import com.identityworksllc.iiq.common.service.BaseCommonService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.SailPointContext;
import sailpoint.plugin.PluginBaseHelper;
import sailpoint.plugin.PluginContext;
import sailpoint.tools.GeneralException;

import java.sql.Connection;

/**
 * Abstract class to easily implement a Service that will only run on the
 * alphabetically lowest Server name. This allows background execution
 * of a task more reliably than the scheduler, but still prevents clashing
 * if more than one server runs the task at once.
 *
 * Extensions should implement {@link #implementation(SailPointContext)}.
 */
public abstract class SingleServerService extends BaseCommonService implements PluginContext {

    /**
     * Constructs a new service object, providing the single-service invoker
     * as an execution model.
     */
    public SingleServerService() {
        // This determines how our service is actually invoked by the
        // superclass. Wraps the default call to this::implementation.
        this.invoker = (context) -> {
            CommonPluginUtils.singleServerExecute(context, getDefinition(), this::implementation);
            context.commitTransaction();
        };
    }

    /**
     * Required by the PluginContext interface. Implementing this here so that
     * we don't have to implement it in every sub-class of this class.
     *
     * @return The plugin database connection
     * @throws GeneralException if a DB connection failure occurs
     */
    @Override
    public Connection getConnection() throws GeneralException {
        return PluginBaseHelper.getConnection();
    }
}
