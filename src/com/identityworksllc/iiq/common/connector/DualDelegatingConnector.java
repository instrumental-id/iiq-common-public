package com.identityworksllc.iiq.common.connector;

import openconnector.ConnectorServices;
import openconnector.ConnectorStateChangeListener;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.connector.*;
import sailpoint.object.*;
import sailpoint.tools.CloseableIterator;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.*;

/**
 * A connector that will forward 'read' and 'write' operations to different connectors, usually
 * where reading from the source would be expensive or infeasible. Sort of the opposite of an
 * IntegrationConfig.
 *
 * Optionally, getObject() can be forwarded to the 'write' connector.
 */
public class DualDelegatingConnector  extends AbstractConnector implements ConnectorStateChangeListener {

    /**
     * The log
     */
    private final Log log;

    /**
     * The Connector to use for reading operations
     */
    private Connector readConnector;

    /**
     * The Connector to use for writing operations
     */
    private Connector writeConnector;

    /**
     * Constructs a new BaseDelegatingConnector of the given type
     * @param application The application to use for the connector
     */
    public DualDelegatingConnector(Application application) {
        super(application, null);
        log = LogFactory.getLog(this.getClass());
    }

    @Override
    public ProvisioningResult checkStatus(String id) throws ConnectorException, GeneralException {
        return getWriteConnector().checkStatus(id);
    }

    @Override
    public void destroy(Map<String, Object> optionsMap) throws ConnectorException {
        getWriteConnector().destroy(optionsMap);
        getReadConnector().destroy(optionsMap);
    }

    @Override
    public Map<String, Object> discoverApplicationAttributes(Map<String, Object> options) throws ConnectorException {
        return getWriteConnector().discoverApplicationAttributes(options);
    }

    @Override
    public Schema discoverSchema(String objectType, Map<String, Object> options) throws ConnectorException {
        return getReadConnector().discoverSchema(objectType, options);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Map doHealthCheck(Map<String, Object> options) throws ConnectorException, UnsupportedOperationException {
        return getReadConnector().doHealthCheck(options);
    }

    @Override
    @SuppressWarnings("deprecation")
    public String getConnectorType() {
        return getWriteConnector().getConnectorType();
    }

    @Override
    public List<AttributeDefinition> getDefaultAttributes() {
        if (getWriteConnector() instanceof AbstractConnector) {
            return ((AbstractConnector)getWriteConnector()).getDefaultAttributes();
        }
        return super.getDefaultAttributes();
    }

    @Override
    public List<Schema> getDefaultSchemas() {
        if (getWriteConnector() instanceof AbstractConnector) {
            return ((AbstractConnector)getWriteConnector()).getDefaultSchemas();
        }
        return super.getDefaultSchemas();
    }

    @Override
    public List<Partition> getIteratorPartitions(String objectType, int suggestedPartitionCount, Filter filter, Map<String, Object> ops) throws ConnectorException {
        String realConnector = getObligatoryStringAttribute("delegate_read_Application");
        log.info("Deciding partitions with read connector: " + realConnector);

        return getReadConnector().getIteratorPartitions(objectType, suggestedPartitionCount, filter, ops);
    }

    @Override
    public ResourceObject getObject(String s, String s1, Map<String, Object> map) throws ConnectorException {
        String whichConnectorForGetObject = getStringAttribute("delegate_read_GetObjectConnector");
        if (Util.isNullOrEmpty(whichConnectorForGetObject)) {
            whichConnectorForGetObject = "read";
        }

        if ("write".equalsIgnoreCase(whichConnectorForGetObject)) {
            String realConnector = getObligatoryStringAttribute("delegate_write_ConnectorClass");
            log.info("Fetching single object with write connector: " + realConnector);
            return getWriteConnector().getObject(s, s1, map);
        } else if ("read".equalsIgnoreCase(whichConnectorForGetObject)) {
            String realConnector = getObligatoryStringAttribute("delegate_read_Application");
            log.info("Fetching single object with read connector: " + realConnector);
            return getReadConnector().getObject(s, s1, map);
        } else {
            throw new ConnectorException("Invalid value for delegate_read_GetObjectConnector: " + whichConnectorForGetObject);
        }
    }

    private Connector getReadConnector() {
        if (this.readConnector != null) {
            return this.readConnector;
        }

        try {
            String readApplicationName = getObligatoryStringAttribute("delegate_read_Application");
            Application readApplication = (Application) getConnectorServices().getObject(Application.class, readApplicationName);
            this.readConnector = ConnectorFactory.getConnector(readApplication, null);
            this.readConnector.setConnectorServices(getConnectorServices());
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }

        return readConnector;
    }

    @Override
    public Schema getSchema(String objectType) throws SchemaNotDefinedException {
        if (getWriteConnector() instanceof AbstractConnector) {
            return ((AbstractConnector) getWriteConnector()).getSchema(objectType);
        } else {
            return null;
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public List<Application.Feature> getSupportedFeatures() {
        Set<Application.Feature> features = new HashSet<>();
        features.addAll(getReadConnector().getSupportedFeatures());
        features.addAll(getWriteConnector().getSupportedFeatures());
        return new ArrayList<>(features);
    }

    @Override
    public String getSystemIdentity() {
        return getWriteConnector().getSystemIdentity();
    }

    private Connector getWriteConnector() {
        if (this.writeConnector != null) {
            return this.writeConnector;
        }

        try {
            @SuppressWarnings("unchecked")
            List<String> connectorClasspath = getStringListAttribute("connector-classpath");
            this.writeConnector = ConnectorClassLoaderWorkaround.getConnector(getObligatoryStringAttribute("delegate_write_ConnectorClass"), connectorClasspath);
            this.writeConnector.setApplication(this.getApplication());
            this.writeConnector.setTargetApplication(this.getTargetApplication());
            this.writeConnector.setConnectorServices(getConnectorServices());
        } catch (ConnectorException | GeneralException e) {
            throw new IllegalArgumentException(e);
        }

        return this.writeConnector;
    }

    @Override
    public CloseableIterator<ResourceObject> iterateObjects(String s, Filter filter, Map<String, Object> map) throws ConnectorException {
        String realConnector = getObligatoryStringAttribute("delegate_read_Application");
        log.info("Iterating objects with read connector: " + realConnector);
        return getReadConnector().iterateObjects(s, filter, map);
    }

    @Override
    public CloseableIterator<ResourceObject> iterateObjects(Partition partition) throws ConnectorException {
        String realConnector = getObligatoryStringAttribute("delegate_read_Application");
        log.info("Iterating objects with read connector: " + realConnector);
        return getReadConnector().iterateObjects(partition);
    }

    @Override
    public ProvisioningResult provision(ProvisioningPlan plan) throws ConnectorException, GeneralException {
        String realConnector = getObligatoryStringAttribute("delegate_write_ConnectorClass");
        log.info("Provisioning with write connector: " + realConnector);

        return getWriteConnector().provision(plan);
    }

    @Override
    public void saveConnectorState() {
        Connector rc = getWriteConnector();
        if (rc instanceof AbstractConnector) {
            ((AbstractConnector) rc).saveConnectorState();
        }
    }

    @Override
    public void saveConnectorState(Map<String, Object> stateMap) {
        Connector rc = getWriteConnector();
        if (rc instanceof AbstractConnector) {
            ((AbstractConnector) rc).saveConnectorState(stateMap);
        }
    }

    @Override
    public void setApplication(Application application) {
        super.setApplication(application);
        getWriteConnector().setApplication(application);
    }

    @Override
    public void setConnectorServices(ConnectorServices connServices) {
        super.setConnectorServices(connServices);
        getReadConnector().setConnectorServices(connServices);
        getWriteConnector().setConnectorServices(connServices);
    }

    @Override
    public boolean shouldRetry(Exception ex, String error, ProvisioningResult result) {
        boolean shouldRetry = super.shouldRetry(ex, error, result);
        if (shouldRetry) {
            return true;
        }
        Connector realConnector = getWriteConnector();
        if (realConnector instanceof AbstractConnector) {
            return ((AbstractConnector)realConnector).shouldRetry(ex, error, result);
        }
        return false;
    }

    @Override
    public boolean supportsFeature(Application.Feature feature) {
        return getSupportedFeatures().contains(feature);
    }

    @Override
    public boolean supportsPartitionedDeltaAggregation() {
        return getReadConnector().supportsPartitionedDeltaAggregation();
    }

    @Override
    public void testConfiguration() throws ConnectorException {
        if (getReadConnector() == null) {
            throw new ConnectorException("Read connector is not set");
        }
        if (getWriteConnector() == null) {
            throw new ConnectorException("Write connector is not set");
        }

        getReadConnector().testConfiguration();
        getWriteConnector().testConfiguration();
    }

    @Override
    public void updateConnectorState(Map<String, Object> map) {
        if (getWriteConnector() instanceof ConnectorStateChangeListener) {
            ((ConnectorStateChangeListener) getWriteConnector()).updateConnectorState(map);
        }
        if (getReadConnector() instanceof ConnectorStateChangeListener) {
            ((ConnectorStateChangeListener) getReadConnector()).updateConnectorState(map);
        }
    }
}
