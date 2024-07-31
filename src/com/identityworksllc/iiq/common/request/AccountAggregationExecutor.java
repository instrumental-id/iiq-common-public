package com.identityworksllc.iiq.common.request;

import com.identityworksllc.iiq.common.AccountUtilities;
import com.identityworksllc.iiq.common.AggregationOutcome;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.Aggregator;
import sailpoint.api.Identitizer;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Request;
import sailpoint.object.ResourceObject;
import sailpoint.request.AbstractRequestExecutor;
import sailpoint.request.RequestPermanentException;
import sailpoint.request.RequestTemporaryException;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.HashMap;
import java.util.Map;

/**
 * Request handler to do an async execution of a single aggregation event. This can
 * be one of three types, corresponding to different inputs:
 *
 * If type is null or 'provided', we expect the Request to contain a Map called 'data',
 * which will contain the attributes of the account. The account name will be derived
 * from the account schema on the application.
 *
 * If type is 'resource', we expect the Request to contain a ResourceObject called
 * 'resource'. It must be fully populated.
 *
 * If type is 'fetch', we expect the Request to contain a String 'accountName',
 * containing the native ID of the object to pass to getObject.
 *
 * For 'data' and 'resource' requests, the application's Customization Rule(s) will
 * be invoked against the inputs. In particular, be aware that if you fetched the
 * ResourceObject using getObject before passing it here, the Customization Rule will
 * have already been run by the Connector and thus will be invoked twice.
 *
 * In all cases, you can supply a Map of 'aggregateOptions', which will be provided
 * to the Aggregator API. The arguments 'promoteAttributes' and 'noOptimizeReaggregation'
 * will always be provided and set to true.
 */
public class AccountAggregationExecutor extends AbstractRequestExecutor {
    /**
     * The name of the Request input containing the account name. This is used
     * when the type is 'fetch'.
     */
    public static final String INPUT_ACCOUNT_NAME = "accountName";

    /**
     * The name of the Request input containing the application name.
     */
    public static final String INPUT_APPLICATION_NAME = "application";

    /**
     * The name of the Request input containing the Map of account data
     */
    public static final String INPUT_DATA = "data";

    /**
     * The name of the Request input containing a ResourceObject
     */
    public static final String INPUT_RESOURCE = "resource";

    /**
     * The name of the Request input containing the aggregation type
     */
    public static final String INPUT_TYPE = "type";

    /**
     * The name of the default request definition associated with this executor
     */
    public static final String REQUEST_DEFINITION = "IDW Account Aggregation Request";

    /**
     * A type string indicating that we should retrieve the object from the source
     */
    public static final String TYPE_FETCH = "fetch";

    /**
     * A type string indicating that the object is provided as a Map
     */
    public static final String TYPE_PROVIDED = "provided";

    /**
     * A type string indicating that the object is provided as a ResourceObject
     */
    public static final String TYPE_RESOURCE = "resource";

    /**
     * Logger
     */
    private static final Log log = LogFactory.getLog(AccountAggregationExecutor.class);

    /**
     * An API that can be used to aggregate a record according to the contract imposed on
     * Requests of this type. This method can be invoked with an arbitrary map, apart from
     * a Request, such as in a workflow.
     *
     * @param context The context
     * @param requestOptions The request options, as a Map
     * @throws RequestPermanentException if the aggregation fails for some reason
     */
    public static void aggregateRecord(SailPointContext context, Map<String, Object> requestOptions) throws RequestPermanentException {
        String applicationName = Util.otoa(Util.get(requestOptions, INPUT_APPLICATION_NAME));
        String type = Util.otoa(Util.get(requestOptions, INPUT_TYPE));

        AccountUtilities.AggregateOptions options;

        if (Util.isNullOrEmpty(applicationName)) {
            throw new RequestPermanentException("Application name is required");
        }

        if (type == null || Util.nullSafeEq(type, TYPE_PROVIDED)) {
            Map<String, Object> data = Util.otom(Util.get(requestOptions, INPUT_DATA));

            if (data == null || data.isEmpty()) {
                throw new RequestPermanentException("For aggregations of type '" + TYPE_PROVIDED + "', an input 'data' (type Map, containing account information) must be supplied");
            }
            options = new AccountUtilities.AggregateOptions(applicationName, data);
            options.setRunAppCustomization(true);

            if (log.isDebugEnabled()) {
                log.debug("Submitting aggregation for application " + applicationName + ", data = " + data);
            }
        } else if (Util.nullSafeEq(type, TYPE_RESOURCE)) {
            ResourceObject ro = (ResourceObject) Util.get(requestOptions, INPUT_RESOURCE);
            if (ro != null) {
                options = new AccountUtilities.AggregateOptions();
                options.setApplicationName(applicationName);
                options.setResourceObject(ro);
                options.setRunAppCustomization(true);
                if (log.isDebugEnabled()) {
                    try {
                        log.debug("Submitting aggregation for application " + applicationName + ", resource = " + ro.toXml());
                    } catch (GeneralException e) {
                        throw new RequestPermanentException(e);
                    }
                }
            } else {
                throw new RequestPermanentException("For aggregations of type '" + TYPE_RESOURCE + "', a ResourceObject 'resource' is required");
            }
        } else if (Util.nullSafeEq(type, TYPE_FETCH)) {
            String accountName = Util.otoa(Util.get(requestOptions, INPUT_ACCOUNT_NAME));
            if (Util.isNullOrEmpty(accountName)) {
                throw new RequestPermanentException("For aggregations of type '" + TYPE_FETCH + "', a String 'accountName' is required");
            }

            options = new AccountUtilities.AggregateOptions();
            options.setAccountName(accountName);
            options.setApplicationName(applicationName);

            if (log.isDebugEnabled()) {
                log.debug("Submitting aggregation for application " + applicationName + ", accountName = " + accountName);
            }
        } else {
            throw new RequestPermanentException("Unsupported aggregation type: " + type);
        }

        Map<String, Object> aggOptions = new HashMap<>();

        Map<String, Object> additionalAggregateOptions = Util.otom(Util.get(requestOptions,"aggregateOptions"));
        if (additionalAggregateOptions != null) {
            aggOptions.putAll(additionalAggregateOptions);
        }

        aggOptions.put(Identitizer.ARG_PROMOTE_ATTRIBUTES, "true");
        aggOptions.put(Aggregator.ARG_NO_OPTIMIZE_REAGGREGATION, "true");

        options.setAggregateOptions(aggOptions);

        try {
            AccountUtilities accountUtilities = new AccountUtilities(context);
            try (AggregationOutcome outcome = accountUtilities.aggregateAccount(options)) {
                if (log.isTraceEnabled()) {
                    log.trace("Aggregation outcome: " + outcome.toString());
                }
            }
        } catch(GeneralException e) {
            log.error("Caught an error aggregating an account from application " + applicationName, e);
            throw new RequestPermanentException(e);
        }
    }

    @Override
    public void execute(SailPointContext context, Request request, Attributes<String, Object> attributes) throws RequestPermanentException, RequestTemporaryException {
        if (log.isDebugEnabled()) {
            log.debug("Starting aggregation request " + request.getName());
        }
        Map<String, Object> requestOptions = request.getAttributes();

        aggregateRecord(context, requestOptions);
    }
}
