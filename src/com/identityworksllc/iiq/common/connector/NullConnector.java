package com.identityworksllc.iiq.common.connector;

import sailpoint.connector.AbstractConnector;
import sailpoint.connector.ConnectorException;
import sailpoint.object.Application;
import sailpoint.object.Filter;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningResult;
import sailpoint.object.ResourceObject;
import sailpoint.tools.CloseableIterator;
import sailpoint.tools.GeneralException;

import java.util.Map;

/**
 * A connector that swallows all operations quietly, used for demo and testing purposes
 */
public class NullConnector extends AbstractConnector {
    /**
     * Constructs a new NullConnector of the given application type
     * @param application The application
     */
    public NullConnector(Application application) {
        super(application);
    }

    /**
     * Constructs a new NullConnector of the given application type and instance name
     * @param application The application type
     * @param instance The instance name (usually null)
     */

    public NullConnector(Application application, String instance) {
        super(application, instance);
    }

    /**
     * Returns null, because there is no real data here
     *
     * @param nativeIdentity The native ID of the object to retrieve
     * @param filter A filter to apply to the object query
     * @param options Any retrieval options
     * @return An alleged object, but actually always null
     * @throws ConnectorException on failures (never, in this case)
     */
    @Override
    public ResourceObject getObject(String nativeIdentity, String filter, Map<String, Object> options) throws ConnectorException {
        return null;
    }

    /**
     * Returns an empty iterator where hasNext() will always return false
     *
     * @param s The object type to query
     * @param filter The filter to iterate over
     * @param map A map of options
     * @return An empty iterator
     * @throws ConnectorException never
     */
    @Override
    public CloseableIterator<ResourceObject> iterateObjects(String s, Filter filter, Map<String, Object> map) throws ConnectorException {
        return new CloseableIterator<ResourceObject>() {
            @Override
            public boolean hasNext() {
                return false;
            }

            @Override
            public ResourceObject next() {
                return null;
            }

            @Override
            public void close() {

            }
        };
    }

    /**
     * Swallows the provisioning operation and returns a status of Committed
     * @param plan The provisioning plan to provision (or ignore, in this case)
     * @return The result of the provisioning operation, always committed
     * @throws ConnectorException on failures to provision
     * @throws GeneralException on failures to do IIQ stuff
     */
    @Override
    public ProvisioningResult provision(ProvisioningPlan plan) throws ConnectorException, GeneralException {
        ProvisioningResult result = new ProvisioningResult();
        result.setStatus(ProvisioningResult.STATUS_COMMITTED);
        return result;
    }

    /**
     * Tests the connector by silently succeeding
     * @throws ConnectorException never
     */
    @Override
    public void testConfiguration() throws ConnectorException {

    }
}
