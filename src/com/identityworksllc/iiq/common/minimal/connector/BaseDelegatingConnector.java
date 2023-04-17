package com.identityworksllc.iiq.common.minimal.connector;

import openconnector.ConnectorServices;
import openconnector.ConnectorStateChangeListener;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.connector.AbstractConnector;
import sailpoint.connector.AuthenticationFailedException;
import sailpoint.connector.Connector;
import sailpoint.connector.ConnectorException;
import sailpoint.connector.ExpiredPasswordException;
import sailpoint.connector.InvalidConfigurationException;
import sailpoint.connector.ObjectNotFoundException;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Filter;
import sailpoint.object.Partition;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningResult;
import sailpoint.object.ResourceObject;
import sailpoint.object.Schema;
import sailpoint.plugin.PluginsUtil;
import sailpoint.tools.CloseableIterator;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A straightforward "delegating" connector that will allow loading of connector
 * classes from a plugin.
 */
public class BaseDelegatingConnector extends AbstractConnector implements ConnectorStateChangeListener {
    /**
     * The real connector to which the operation is being delegated
     */
    private Connector _realConnector;

    /**
     * The log
     */
    private final Log log;

    public BaseDelegatingConnector(Application application) {
        super(application, null);
        log = LogFactory.getLog(this.getClass());
    }

    @Override
    public ResourceObject authenticate(String s, String s1) throws ConnectorException, ObjectNotFoundException, AuthenticationFailedException, ExpiredPasswordException {
        return getRealConnector().authenticate(s, s1);
    }

    @Override
    public ResourceObject authenticate(String s, Map<String, Object> map) throws ConnectorException, ObjectNotFoundException, AuthenticationFailedException, ExpiredPasswordException {
        return getRealConnector().authenticate(s, map);
    }

    @Override
    public ProvisioningResult checkStatus(String s) throws ConnectorException, GeneralException {
        return getRealConnector().checkStatus(s);
    }

    @Override
    public void destroy(Map<String, Object> map) throws ConnectorException {
        getRealConnector().destroy(map);
    }

    @Override
    public Map<String, Object> discoverApplicationAttributes(Map<String, Object> map) throws ConnectorException {
        return getRealConnector().discoverApplicationAttributes(map);
    }

    @Override
    public Schema discoverSchema(String s, Map<String, Object> map) throws ConnectorException {
        return getRealConnector().discoverSchema(s, map);
    }

    @Override
    public Map doHealthCheck(Map<String, Object> map) throws ConnectorException {
        return getRealConnector().doHealthCheck(map);
    }

    @Override
    public Application getApplication() {
        return getRealConnector().getApplication();
    }

    @Override
    public ConnectorServices getConnectorServices() {
        return getRealConnector().getConnectorServices();
    }

    /** @deprecated */
    @Override
    @Deprecated
    public String getConnectorType() {
        return getRealConnector().getConnectorType();
    }

    @Override
    public List<AttributeDefinition> getDefaultAttributes() {
        Connector realConnector = getRealConnector();
        if (realConnector instanceof AbstractConnector) {
            return ((AbstractConnector)realConnector).getDefaultAttributes();
        }
        return super.getDefaultAttributes();
    }

    @Override
    public List<Schema> getDefaultSchemas() {
        Connector realConnector = getRealConnector();
        if (realConnector instanceof AbstractConnector) {
            return ((AbstractConnector)realConnector).getDefaultSchemas();
        }
        return super.getDefaultSchemas();
    }

    @Override
    public Map<String, Object> getDependencyData() throws ConnectorException {
        return getRealConnector().getDependencyData();
    }

    @Override
    public String getInstance() {
        return getRealConnector().getInstance();
    }

    @Override
    public List<Partition> getIteratorPartitions(String s, int i, Filter filter, Map<String, Object> map) throws ConnectorException {
        return getRealConnector().getIteratorPartitions(s, i, filter, map);
    }

    @Override
    public ResourceObject getObject(String s, String s1, Map<String, Object> map) throws ConnectorException {
        return getRealConnector().getObject(s, s1, map);
    }

    @Override
    public Application getProxiedApplication(String s, Map<String, Object> map) throws ConnectorException {
        return getRealConnector().getProxiedApplication(s, map);
    }

    /**
     * Retrieves the actual Connector to delegate to, optionally loading from
     * a plugin if needed. If no plugin is specified, this will use the ConnectorFactory
     * to get the real connector class using the built-in functionality used by such
     * things as logical connectors.
     *
     * The new connector instance will be cached and returned on subsequent calls.
     *
     * @return The connector class
     * @throws IllegalArgumentException if any failures to get the connector occur
     */
    protected Connector getRealConnector() throws IllegalArgumentException {
        if (_realConnector != null) {
            return _realConnector;
        }
        synchronized(this) {
            if (_realConnector != null) {
                return _realConnector;
            }
            try {
                String plugin = super.getStringAttribute("pluginName");
                String connectorClass = super.getObligatoryStringAttribute("realConnectorClass");

                Connector connector;
                if (Util.isNotNullOrEmpty(plugin)) {
                    if (log.isDebugEnabled()) {
                        log.debug("Constructing delegated connector from plugin = " + plugin + ", className = " + connectorClass);
                    }
                    connector = PluginsUtil.instantiate(plugin, connectorClass, new Object[] { getApplication() }, new Class[] { Application.class});
                    if (connector == null) {
                        throw new InvalidConfigurationException("Could not find connector class " + connectorClass + " in plugin " + plugin);
                    }
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("Constructing delegated connector with className = " + connectorClass);
                    }
                    connector = super.getRealConnector(connectorClass);
                }

                connector.setInstance(super.getInstance());
                connector.setConnectorServices(super.getConnectorServices());
                connector.setStatisticsCollector(super.getStatisticsCollector());

                _realConnector = connector;
                return connector;
            } catch (ConnectorException e) {
                throw new IllegalArgumentException(e);
            }
        }
    }

    @Override
    public List<Application.Feature> getSupportedFeatures() {
        Connector realConnector = getRealConnector();
        if (realConnector instanceof AbstractConnector) {
            return ((AbstractConnector)realConnector).getSupportedFeatures();
        }
        return super.getSupportedFeatures();
    }

    @Override
    public String getSystemIdentity() {
        return getRealConnector().getSystemIdentity();
    }

    @Override
    public Application getTargetApplication() {
        return getRealConnector().getTargetApplication();
    }

    @Override
    public String getTargetInstance() {
        return getRealConnector().getTargetInstance();
    }

    @Override
    public CloseableIterator<ResourceObject> iterateObjects(String s, Filter filter, Map<String, Object> map) throws ConnectorException {
        return getRealConnector().iterateObjects(s, filter, map);
    }

    @Override
    public CloseableIterator<ResourceObject> iterateObjects(Partition partition) throws ConnectorException {
        return getRealConnector().iterateObjects(partition);
    }

    @Override
    public ProvisioningResult provision(ProvisioningPlan provisioningPlan) throws ConnectorException, GeneralException {
        return getRealConnector().provision(provisioningPlan);
    }

    @Override
    public void saveConnectorState() {
        Connector rc = getRealConnector();
        if (rc instanceof AbstractConnector) {
            ((AbstractConnector) rc).saveConnectorState();
        }
    }

    @Override
    public void saveConnectorState(Map<String, Object> stateMap) {
        Connector rc = getRealConnector();
        if (rc instanceof AbstractConnector) {
            ((AbstractConnector) rc).saveConnectorState(stateMap);
        }
    }

    @Override
    public void setApplication(Application application) {
        getRealConnector().setApplication(application);
    }

    @Override
    public void setConnectorServices(ConnectorServices connectorServices) {
        getRealConnector().setConnectorServices(connectorServices);
    }

    @Override
    public void setInstance(String s) {
        getRealConnector().setInstance(s);
    }

    @Override
    public void setSystemIdentity(String s) {
        getRealConnector().setSystemIdentity(s);
    }

    @Override
    public void setTargetApplication(Application application) {
        getRealConnector().setTargetApplication(application);
    }

    @Override
    public void setTargetInstance(String s) {
        getRealConnector().setTargetInstance(s);
    }

    @Override
    public boolean shouldRetry(Exception ex, String error, ProvisioningResult result) {
        boolean shouldRetry = super.shouldRetry(ex, error, result);
        if (shouldRetry) {
            return true;
        }
        Connector realConnector = getRealConnector();
        if (realConnector instanceof AbstractConnector) {
            shouldRetry = ((AbstractConnector)realConnector).shouldRetry(ex, error, result);
        }
        if (shouldRetry) {
            return true;
        }
        String retryDetectionRule = getApplication().getStringAttributeValue("retryDetectionRule");
        if (Util.isNotNullOrEmpty(retryDetectionRule)) {
            if (log.isDebugEnabled()) {
                log.debug("A retry detection rule " + retryDetectionRule + " is defined");
            }

            try {
                Map<String, Object> inputs = new HashMap<>();
                inputs.put("application", getApplication());
                inputs.put("connector", this);
                inputs.put("error", error);
                inputs.put("result", result);
                inputs.put("exception", ex);
                inputs.put("connectorServices", getConnectorServices());
                inputs.put("log", log);
                Object output = getConnectorServices().runRule(retryDetectionRule, inputs);
                if (output instanceof Boolean) {
                    if (log.isDebugEnabled()) {
                        log.debug("The retry detection rule " + retryDetectionRule + " says we should retry this operation");
                    }
                    return (Boolean)output;
                }
            } catch(Exception e) {
                log.error("Caught an error trying to determine if we should retry an operation with former outcome " + ex, e);
            }
        }
        return false;
    }

    @Override
    public boolean supportsPartitionedDeltaAggregation() {
        return getRealConnector().supportsPartitionedDeltaAggregation();
    }

    @Override
    public void testConfiguration() throws ConnectorException {
        getRealConnector().testConfiguration();
    }

    @Override
    public void updateConnectorState(Map<String, Object> map) {
        Connector rc = getRealConnector();
        if (rc instanceof ConnectorStateChangeListener) {
            ((ConnectorStateChangeListener) rc).updateConnectorState(map);
        }
    }
}
