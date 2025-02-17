package com.identityworksllc.iiq.common.connector;

import sailpoint.connector.Connector;
import sailpoint.connector.ConnectorFactory;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.object.Application;

import java.util.List;

/**
 * Workaround for some classloader problems with getRealConnector(). This will
 * create a temporary fake Application with the given implementation class and
 * construct that according to the configuration provided.
 */
public class ConnectorClassLoaderWorkaround {

    /**
     * Get a connector by class name. This will create a temporary fake Application.
     * @param applicationClassName The class name of the connector
     * @param optionalConnectorClassloader A list of connector-classpath entries to add to the connector classloader
     * @return The connector
     * @throws GeneralException if the connector cannot be created
     */
    public static Connector getConnector(String applicationClassName, List<String> optionalConnectorClassloader) throws GeneralException {
        Application tempApplication = new Application();
        tempApplication.setConnector(applicationClassName);
        tempApplication.setName(applicationClassName);

        if (!Util.isEmpty(optionalConnectorClassloader)) {
            tempApplication.setAttribute("connector-classpath", optionalConnectorClassloader);
        }

        return ConnectorFactory.createConnector(applicationClassName, tempApplication, null);
    }
}
