package com.identityworksllc.iiq.common.service;

import com.identityworksllc.iiq.common.Utilities;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.IncrementalObjectIterator;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Custom;
import sailpoint.object.Filter;
import sailpoint.object.PersistenceOptions;
import sailpoint.object.QueryOptions;
import sailpoint.object.Server;
import sailpoint.object.ServiceDefinition;
import sailpoint.server.Service;
import sailpoint.server.ServicerUtil;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A service to retain the last aggregation timestamps and other cache data
 * for Applications in a Custom object called "Aggregation Date Storage".
 *
 * Since this data is stored in the Application XML itself, it is often inadvertently
 * overwritten on deployment with an earlier version, or with null data. This will
 * cause delta tasks to run long or fail entirely. With this service installed,
 * the Application's dates will be restored as soon as possible after deployment.
 *
 * The data will be copied as follows:
 *
 * - If there is no data on the Application, but data exists in the Custom object,
 *   the values on the Application will be replaced by the values in the Custom.
 *
 * - If there is no cache for the given Application in the Custom object, the values
 *   in the Custom will always be set to the Application's values.
 *
 * - If the Application's acctAggregationEnd timestamp is newer than the values in the
 *   Custom object, the Application's values will be copied to the Custom.
 *
 * - If the Custom's acctAggregationEnd timestamp is newer than the value in the
 *   Application object, the Application's values will be replaced by the value
 *   in the Custom.
 *
 * The following attributes are retained in the Custom for each Application:
 *
 * - acctAggregationEnd
 * - acctAggregationStart
 * - deltaAggregation
 * - lastAggregationDate
 *
 * Three of the four are millisecond timestamps, while deltaAggregation often
 * contains metadata, like cookies from Azure.
 */
public class AggregationDateRetentionService extends Service {

    /**
     * The field indicating aggregation end (a Date)
     */
    public static final String ACCT_AGGREGATION_END = "acctAggregationEnd";

    /**
     * The field indicating aggregation start (a Date)
     */
    public static final String ACCT_AGGREGATION_START = "acctAggregationStart";

    /**
     * The name of the Custom object storing application aggregation dates
     */
    private static final String CUSTOM_NAME = "Aggregation Date Storage";

    /**
     * The field containing delta aggregation details (e.g., dirSync data in AD)
     */
    public static final String DELTA_AGGREGATION = "deltaAggregation";

    /**
     * The last aggregation date column (used in, e.g., Workday)
     */
    public static final String LAST_AGGREGATION_DATE = "lastAggregationDate";

    private static final Log log = LogFactory.getLog(AggregationDateRetentionService.class);

    @Override
    public void execute(SailPointContext context) throws GeneralException {
        ServiceDefinition self = getDefinition();
        Server target = null;
        QueryOptions qo = new QueryOptions();
        qo.setDirtyRead(false);
        qo.setCacheResults(false);
        qo.setCloneResults(true);
        qo.addOrdering("name", true);
        if (!Util.nullSafeCaseInsensitiveEq(self.getHosts(), "global")) {
            qo.addFilter(Filter.in("name", Util.csvToList(self.getHosts())));
        }
        List<Server> servers = context.getObjects(Server.class, qo);
        for(Server s : Util.safeIterable(servers)) {
            if (!s.isInactive() && ServicerUtil.isServiceAllowedOnServer(context, self, s.getName())) {
                target = s;
                break;
            }
        }
        if (target == null) {
            // This would be VERY strange, since we are, in fact, running the service
            // right now, in this very code
            log.warn("There does not appear to be an active server allowed to run service " + self.getName());
        }
        String hostname = Util.getHostName();
        if (target == null || target.getName().equals(hostname)) {
            Utilities.withPrivateContext((privateContext) -> {
                PersistenceOptions po = new PersistenceOptions();
                po.setExplicitSaveMode(true);

                privateContext.setPersistenceOptions(po);

                implementation(privateContext);
            });
        }
    }

    /**
     * Main method for the service, invoked if we are the alphabetically lowest host.
     *
     * Queries each application's last aggregation timestamps. If the application's
     * data is missing or outdated, and there is a retained value, this method restores
     * the retained value to the Application. If there is no retained value, or if the
     * retained data is older than the Application data, the retention Custom is updated.
     *
     * @param context IIQ context
     * @throws GeneralException if any failures occur
     */
    private void implementation(SailPointContext context) throws GeneralException {
        Custom custom = context.getObjectByName(Custom.class, CUSTOM_NAME);
        if (custom == null) {
            custom = new Custom();
            custom.setName(CUSTOM_NAME);
        }

        QueryOptions qo = new QueryOptions();
        qo.addOrdering("name", true);

        IncrementalObjectIterator<Application> iterator = new IncrementalObjectIterator<>(context, Application.class, qo);

        while(iterator.hasNext()) {
            Application application = iterator.next();

            boolean updateCustom = false;
            boolean updateApplication = false;

            Map<String, Object> existingRecord = (Map<String, Object>) custom.get(application.getName());
            Date lastAggregationEnd = (Date) application.getAttributeValue(ACCT_AGGREGATION_END);

            if (lastAggregationEnd != null) {
                if (existingRecord != null) {
                    Date existingLastRunEnd = (Date) existingRecord.get(ACCT_AGGREGATION_END);
                    if (existingLastRunEnd.before(lastAggregationEnd)) {
                        updateCustom = true;
                    } else {
                        updateApplication = true;
                    }
                } else {
                    updateCustom = true;
                }
            } else if (existingRecord != null) {
                updateApplication = true;
            }

            if (updateCustom) {
                if (log.isDebugEnabled()) {
                    log.debug("Updating Date Retention Custom object for " + application.getName());
                }
                Map<String, Object> appData = new HashMap<>();
                appData.put(ACCT_AGGREGATION_END, application.getAttributeValue(ACCT_AGGREGATION_END));
                appData.put(ACCT_AGGREGATION_START, application.getAttributeValue(ACCT_AGGREGATION_START));
                appData.put(DELTA_AGGREGATION, application.getAttributeValue(DELTA_AGGREGATION));
                appData.put(LAST_AGGREGATION_DATE, application.getAttributeValue(LAST_AGGREGATION_DATE));
                custom.put(application.getName(), appData);
            }

            if (updateApplication) {
                if (log.isDebugEnabled()) {
                    log.debug("Updating Application data for " + application.getName());
                }
                application.setAttribute(ACCT_AGGREGATION_END, existingRecord.get(ACCT_AGGREGATION_END));
                application.setAttribute(ACCT_AGGREGATION_START, existingRecord.get(ACCT_AGGREGATION_START));
                application.setAttribute(DELTA_AGGREGATION, existingRecord.get(DELTA_AGGREGATION));
                application.setAttribute(LAST_AGGREGATION_DATE, existingRecord.get(LAST_AGGREGATION_DATE));
                context.saveObject(application);
            }
        }

        context.saveObject(custom);

        context.commitTransaction();
    }
}
