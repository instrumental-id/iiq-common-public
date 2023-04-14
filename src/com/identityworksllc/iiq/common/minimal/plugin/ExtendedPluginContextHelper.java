package com.identityworksllc.iiq.common.minimal.plugin;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.Configuration;
import sailpoint.object.Plugin;
import sailpoint.plugin.PluginContext;
import sailpoint.plugin.Setting;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.function.Supplier;

/**
 * A helper class intended to be used by anybody implementing CommonExtendedPluginConfig.
 * It will retrieve values first from the configured {@link Configuration} object, then
 * from the plugin settings.
 *
 * You should construct a new instance of this class for each retrieval. Do not cache them
 * or your SailPointContext will go stale.
 */
public class ExtendedPluginContextHelper implements CommonExtendedPluginContext {

	@FunctionalInterface
	public interface ConfigurationNameProvider extends Supplier<String> {

	}

	/**
	 * The current sailpoint context
	 */
	private SailPointContext context;

	/**
	 * Provides the name of the configuration class
	 */
	private ConfigurationNameProvider nameProvider;
	
	/**
	 * The plugin context (provides plugin config)
	 */
	private PluginContext pluginContext;
	
	/**
	 * The plugin name
	 */
	private String pluginName;	

	/**
	 * Constructor
	 * @param pluginName The plugin name
	 * @param context The Sailpoint context
	 * @param pluginContext The plugin context class
	 */
	public ExtendedPluginContextHelper(String pluginName, SailPointContext context, PluginContext pluginContext, ConfigurationNameProvider nameProvider) {
		this.pluginName = pluginName;
		this.context = context;
		this.pluginContext = pluginContext;
		this.nameProvider = nameProvider;
	}

	
	/**
	 * Bootstraps the Configuration object from the plugin settings. This will be used from now on.
	 * @throws GeneralException if any failures occur
	 */
	private void bootstrapConfig() throws GeneralException {
		SailPointContext existing = context;
		SailPointContext privateContext = SailPointFactory.createPrivateContext();
		SailPointFactory.setContext(privateContext);
		try {
			Configuration config = new Configuration();
			config.setName(getSettingsConfigName());
			Plugin pluginObject = privateContext.getObjectByName(Plugin.class, pluginName);
			if (pluginObject != null) {
				if (pluginObject.getSettings() != null) {
					for(Setting setting : pluginObject.getSettings()) {
						String settingValue = Util.isNullOrEmpty(setting.getValue()) ? setting.getDefaultValue() : setting.getValue();
						config.put(setting.getName(), settingValue);
					}
				}
			}
			privateContext.saveObject(config);
			privateContext.commitTransaction();
			privateContext.decache();
		} finally {
			SailPointFactory.releasePrivateContext(privateContext);
			SailPointFactory.setContext(existing);
		}
	}
	

	@Override
	public boolean getConfigurationBool(String settingName) {
		try {
			Configuration config = context.getObjectByName(Configuration.class, getSettingsConfigName());
			if (config == null) {
				bootstrapConfig();
				context.decache();
				config = context.getObjectByName(Configuration.class, getSettingsConfigName());
			} 
			if (config != null && config.containsAttribute(settingName)) {
				return config.getBoolean(settingName);
			}
		} catch(GeneralException e) {
			// Ignore this, default to the plugin settings
		}
		return pluginContext.getSettingBool(settingName);
	}

	@Override
	public int getConfigurationInt(String settingName) {
		try {
			Configuration config = context.getObjectByName(Configuration.class, getSettingsConfigName());
			if (config == null) {
				bootstrapConfig();
				context.decache();
				config = context.getObjectByName(Configuration.class, getSettingsConfigName());
			} 
			if (config != null && config.containsAttribute(settingName)) {
				return config.getInt(settingName);
			}
		} catch(GeneralException e) {
			// Ignore this, default to the plugin settings
		}
		return pluginContext.getSettingInt(settingName);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T getConfigurationObject(String settingName) {
		try {
			Configuration config = context.getObjectByName(Configuration.class, getSettingsConfigName());
			if (config == null) {
				bootstrapConfig();
				context.decache();
				config = context.getObjectByName(Configuration.class, getSettingsConfigName());
			} 
			if (config != null && config.containsAttribute(settingName)) {
				return (T)config.get(settingName);
			}
		} catch(GeneralException e) {
			// Ignore this, default to the plugin settings
		}
		return null;
	}

	@Override
	public String getConfigurationString(String settingName) {
		try {
			Configuration config = context.getObjectByName(Configuration.class, getSettingsConfigName());
			if (config == null) {
				bootstrapConfig();
				context.decache();
				config = context.getObjectByName(Configuration.class, getSettingsConfigName());
			} 
			if (config != null && config.containsAttribute(settingName)) {
				return config.getString(settingName);
			}
		} catch(GeneralException e) {
			// Ignore this, default to the plugin settings
		}
		return pluginContext.getSettingString(settingName);
	}
	
	/**
	 * The settings configuration object name. If a name provider exists, it will be used to
	 * get the Configuration name. Otherwise, it will default to `Plugin Configuration [plugin name]`.
	 *
	 * @return The name of the Configuration object
	 */
	private String getSettingsConfigName() {
		if (nameProvider != null) {
			return nameProvider.get();
		} else {
			return "Plugin Configuration " + pluginName;
		}
	}
	
	

}
