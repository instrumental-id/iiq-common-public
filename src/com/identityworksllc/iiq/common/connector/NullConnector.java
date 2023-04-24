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

public class NullConnector extends AbstractConnector {
    public NullConnector(Application application) {
        super(application);
    }

    public NullConnector(Application application, String instance) {
        super(application, instance);
    }

    @Override
    public ResourceObject getObject(String nativeIdentity, String filter, Map<String, Object> options) throws ConnectorException {
        return null;
    }

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

    @Override
    public ProvisioningResult provision(ProvisioningPlan plan) throws ConnectorException, GeneralException {
        ProvisioningResult result = new ProvisioningResult();
        result.setStatus(ProvisioningResult.STATUS_COMMITTED);
        return result;
    }

    @Override
    public void testConfiguration() throws ConnectorException {

    }
}
