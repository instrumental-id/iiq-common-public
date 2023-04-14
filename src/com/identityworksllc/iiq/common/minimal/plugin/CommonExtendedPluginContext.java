package com.identityworksllc.iiq.common.minimal.plugin;

/**
 * A superclass to ensure that the BaseCommonPlugin* classes all implement the same methods
 */
public interface CommonExtendedPluginContext {

	/**
	 * Gets the configuration setting from the default plugin Configuration object or else from the plugin settings
	 * @param settingName The setting to retrieve
	 * @return The setting value
	 */
	boolean getConfigurationBool(String settingName);

	/**
	 * Gets the configuration setting from the default plugin Configuration object or else from the plugin settings
	 * @param settingName The setting to retrieve
	 * @return The setting value
	 */
	int getConfigurationInt(String settingName);

	/**
	 * Gets the given configuration setting as an Object
	 * @param settingName The setting to retrieve as an Object
	 * @return The object
	 */
	<T> T getConfigurationObject(String settingName);

	/**
	 * Gets the configuration setting from the default plugin Configuration object or else from the plugin settings
	 * @param settingName The setting to retrieve
	 * @return The setting value
	 */
	String getConfigurationString(String settingName);

}