package com.identityworksllc.iiq.common.connector;

import connector.common.DiscoveredApplication;
import connector.common.statisticscollector.StatisticsCollector;
import openconnector.ConnectorServices;
import sailpoint.connector.AuthenticationFailedException;
import sailpoint.connector.Connector;
import sailpoint.connector.ConnectorException;
import sailpoint.connector.ExpiredPasswordException;
import sailpoint.connector.InsufficientPermissionException;
import sailpoint.connector.ObjectNotFoundException;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Filter;
import sailpoint.object.Partition;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningResult;
import sailpoint.object.ResourceObject;
import sailpoint.object.Schema;
import sailpoint.tools.CloseableIterator;
import sailpoint.tools.GeneralException;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A connector implementation that only throws {@link UnsupportedOperationException} for all operations
 */
public class UnsupportedConnector implements Connector {
    @Override
    public ResourceObject authenticate(String s, String s1) throws ConnectorException, ObjectNotFoundException, AuthenticationFailedException, ExpiredPasswordException {
        throw new UnsupportedOperationException("Connector operations are not available in this situation");
    }

    @Override
    public ResourceObject authenticate(String s, Map<String, Object> map) throws ConnectorException, ObjectNotFoundException, AuthenticationFailedException, ExpiredPasswordException {
        throw new UnsupportedOperationException("Connector operations are not available in this situation");
    }

    @Override
    public ProvisioningResult checkStatus(String s) throws ConnectorException, GeneralException {
        throw new UnsupportedOperationException("Connector operations are not available in this situation");
    }

    @Override
    public void destroy(Map<String, Object> map) throws ConnectorException {
        throw new UnsupportedOperationException("Connector operations are not available in this situation");
    }

    @Override
    public Map<String, Object> discoverApplicationAttributes(Map<String, Object> map) throws ConnectorException {
        throw new UnsupportedOperationException("Connector operations are not available in this situation");
    }

    @Override
    public CloseableIterator<DiscoveredApplication> discoverApplications() throws ConnectorException, UnsupportedOperationException {
        return null;
    }

    @Override
    public Schema discoverSchema(String s, Map<String, Object> map) throws ConnectorException {
        throw new UnsupportedOperationException("Connector operations are not available in this situation");
    }

    @Override
    public Map doHealthCheck(Map<String, Object> map) throws ConnectorException {
        throw new UnsupportedOperationException("Connector operations are not available in this situation");
    }

    @Override
    public Application getApplication() {
        throw new UnsupportedOperationException("Connector operations are not available in this situation");
    }

    @Override
    public Iterator<Map<String, Object>> getChangeLogExtract(Map<String, Object> map) throws ConnectorException {
        throw new UnsupportedOperationException("Connector operations are not available in this situation");
    }

    /**
     * New in 8.4p2
     *
     * @param var1 unknown
     * @return unknown
     * @throws ConnectorException if the connector cannot be used
     * @throws ObjectNotFoundException if the object is not found
     * @throws UnsupportedOperationException if the operation is not supported in this context
     * @see Connector
     */
    @SuppressWarnings({"unused"})
    public Map<String, Object> getConfigOptions(String var1) throws ConnectorException, ObjectNotFoundException, UnsupportedOperationException {
        throw new UnsupportedOperationException("Connector operations are not available in this situation");
    }

    @Override
    public ConnectorServices getConnectorServices() {
        throw new UnsupportedOperationException("Connector operations are not available in this situation");
    }

    @Override
    public String getConnectorType() {
        throw new UnsupportedOperationException("Connector operations are not available in this situation");
    }

    @Override
    public List<AttributeDefinition> getDefaultAttributes() {
        throw new UnsupportedOperationException("Connector operations are not available in this situation");
    }

    @Override
    public List<Schema> getDefaultSchemas() {
        throw new UnsupportedOperationException("Connector operations are not available in this situation");
    }

    @Override
    public Map<String, Object> getDependencyData() throws ConnectorException {
        throw new UnsupportedOperationException("Connector operations are not available in this situation");
    }

    @Override
    public List<Map<String, Object>> getExtractPartitions(Map<String, Object> map, String s) throws ConnectorException, InsufficientPermissionException {
        throw new UnsupportedOperationException("Connector operations are not available in this situation");
    }

    @Override
    public String getInstance() {
        throw new UnsupportedOperationException("Connector operations are not available in this situation");
    }

    @Override
    public List<Partition> getIteratorPartitions(String s, int i, Filter filter, Map<String, Object> map) throws ConnectorException {
        throw new UnsupportedOperationException("Connector operations are not available in this situation");
    }

    @Override
    public Application getLocalApplication() {
        throw new UnsupportedOperationException("Connector operations are not available in this situation");
    }

    @Override
    public ResourceObject getObject(String s, String s1, Map<String, Object> map) throws ConnectorException {
        throw new UnsupportedOperationException("Connector operations are not available in this situation");
    }

    @Override
    public Application getProxiedApplication(String s, Map<String, Object> map) throws ConnectorException {
        throw new UnsupportedOperationException("Connector operations are not available in this situation");
    }

    @Override
    public Iterator<Map<String, Object>> getSecurityExtract(Map<String, Object> map) throws ConnectorException {
        throw new UnsupportedOperationException("Connector operations are not available in this situation");
    }

    @Override
    public StatisticsCollector getStatisticsCollector() {
        throw new UnsupportedOperationException("Connector operations are not available in this situation");
    }

    @Override
    public List<Application.Feature> getSupportedFeatures() {
        throw new UnsupportedOperationException("Connector operations are not available in this situation");
    }

    @Override
    public String getSystemIdentity() {
        throw new UnsupportedOperationException("Connector operations are not available in this situation");
    }

    @Override
    public Application getTargetApplication() {
        throw new UnsupportedOperationException("Connector operations are not available in this situation");
    }

    @Override
    public String getTargetInstance() {
        throw new UnsupportedOperationException("Connector operations are not available in this situation");
    }

    @Override
    public Iterator<Map<String, Object>> getUtilizationExtract(Map<String, Object> map) throws ConnectorException {
        throw new UnsupportedOperationException("Connector operations are not available in this situation");
    }

    @Override
    public CloseableIterator<ResourceObject> iterateObjects(String s, Filter filter, Map<String, Object> map) throws ConnectorException {
        throw new UnsupportedOperationException("Connector operations are not available in this situation");
    }

    @Override
    public CloseableIterator<ResourceObject> iterateObjects(Partition partition) throws ConnectorException {
        throw new UnsupportedOperationException("Connector operations are not available in this situation");
    }

    @Override
    public ProvisioningResult provision(ProvisioningPlan provisioningPlan) throws ConnectorException, GeneralException {
        throw new UnsupportedOperationException("Connector operations are not available in this situation");
    }

    @Override
    public void setApplication(Application application) {
        throw new UnsupportedOperationException("Connector operations are not available in this situation");
    }

    @Override
    public void setConnectorServices(ConnectorServices connectorServices) {
        throw new UnsupportedOperationException("Connector operations are not available in this situation");
    }

    @Override
    public void setInstance(String s) {
        throw new UnsupportedOperationException("Connector operations are not available in this situation");
    }

    @Override
    public void setStatisticsCollector(StatisticsCollector statisticsCollector) {
        throw new UnsupportedOperationException("Connector operations are not available in this situation");
    }

    @Override
    public void setSystemIdentity(String s) {
        throw new UnsupportedOperationException("Connector operations are not available in this situation");
    }

    @Override
    public void setTargetApplication(Application application) {
        throw new UnsupportedOperationException("Connector operations are not available in this situation");
    }

    @Override
    public void setTargetInstance(String s) {
        throw new UnsupportedOperationException("Connector operations are not available in this situation");
    }

    @Override
    public boolean supportsPartitionedDeltaAggregation() {
        throw new UnsupportedOperationException("Connector operations are not available in this situation");
    }

    @Override
    public void testConfiguration() throws ConnectorException {
        throw new UnsupportedOperationException("Connector operations are not available in this situation");
    }

    @Override
    public void updateApplicationConfig() throws GeneralException {
        throw new UnsupportedOperationException("Connector operations are not available in this situation");
    }

}
