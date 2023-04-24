package com.identityworksllc.iiq.common;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.CompoundFilter;
import sailpoint.object.Configuration;
import sailpoint.object.Custom;
import sailpoint.object.Filter;
import sailpoint.object.IdentitySelector;
import sailpoint.object.Reference;
import sailpoint.object.SailPointObject;
import sailpoint.object.Script;
import sailpoint.plugin.PluginsCache;
import sailpoint.server.Environment;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;

import java.util.Collection;
import java.util.Date;
import java.util.Map;

public class SailpointObjectMapper<T> extends ObjectMapper<T> {

    /**
     * Overrides the mapping from Class to class name to include the plugin cache
     * version. When a plugin is redeployed, this value will change, invalidating
     * the cached objects. This should prevent weirdness where the new version
     * of a class isn't strictly compatible with the old version.
     */
    protected static class SailPointTypeNamer extends DefaultTypeNamer {
        @Override
        public String getTypeName(Class<?> type) {
            return super.getTypeName(type) + ":" + getPluginVersion();
        }
    }

    /**
     * Gets the current plugin version, or "NA" if plugins ar not enabled
     * @return The plugin version number
     */
    private static String getPluginVersion() {
        String version = "NA";
        if (Environment.getEnvironment() != null) {
            PluginsCache cache = Environment.getEnvironment().getPluginsCache();
            if (cache != null) {
                version = String.valueOf(cache.getVersion());
            }
        }
        return version;
    }

    /**
     * Returns the object mapped from the given Configuration
     * @param context The context to use to get the Configuration object
     * @param configName The configuration name
     * @param type the output type
     * @param <T> The output type
     * @return An instance of the mapped configuration object
     * @throws GeneralException if any failures occur
     */
    public static <T> T fromConfiguration(SailPointContext context, String configName, Class<T> type) throws GeneralException, ObjectMapperException {
        return get(type).decode(context.getObject(Configuration.class, configName));
    }

    /**
     * Transform the object mapper exception into a GeneralException as needed
     * @param e The exception to unwrap or wrap
     * @return an appropriate GeneralException
     */
    public static GeneralException unwrap(ObjectMapper.ObjectMapperException e) {
        if (e.getCause() != null && e.getCause() instanceof GeneralException) {
            return (GeneralException) e.getCause();
        } else {
            return new GeneralException(e);
        }
    }

    /**
     * Basic constructor. You should prefer {@link ObjectMapper#get(Class)} to this.
     *
     * @param targetClass the target class
     */
    public SailpointObjectMapper(Class<T> targetClass) {
        super(targetClass);
    }

    /**
     * Converts the given object to the expected type. If the input is null, a null
     * will be returned. If the input is already compatible with the expected type,
     * the existing object will be returned. If the input cannot be converted, an
     * exception will be thrown.
     *
     * @param value The input value
     * @param expectedType The expected type of the input
     * @return The converted object
     */
    public Object convertObject(Object value, Class<?> expectedType) throws ObjectMapperException {
        if (value == null) {
            return null;
        }
        if (expectedType.isAssignableFrom(value.getClass())) {
            return value;
        }
        if (expectedType.equals(Boolean.TYPE) || expectedType.equals(Boolean.class)) {
            value = Util.otob(value);
        } else if (expectedType.equals(String.class)) {
            value = Util.otoa(value);
        } else if (expectedType.equals(Long.TYPE) || expectedType.equals(Long.class)) {
            value = Long.parseLong(Util.otoa(value));
        } else if (expectedType.equals(Integer.TYPE) || expectedType.equals(Integer.class)) {
            value = Integer.parseInt(Util.otoa(value));
        } else if (expectedType.equals(Date.class)) {
            if (value instanceof Long) {
                value = new Date((Long) value);
            } else if (value instanceof java.sql.Date) {
                value = new Date(((java.sql.Date)value).getTime());
            } else {
                throw new IllegalArgumentException("Cannot convert " + value.getClass().getName() + " to " + expectedType.getName());
            }
        } else if (Collection.class.isAssignableFrom(expectedType)) {
            value = Util.otol(value);
        } else if (IdentitySelector.class.isAssignableFrom(expectedType)) {
            if (value instanceof String) {
                IdentitySelector identitySelector = new IdentitySelector();
                CompoundFilter compoundFilter = new CompoundFilter();
                compoundFilter.setFilter(Filter.compile(Util.otoa(value)));
                identitySelector.setFilter(compoundFilter);
                value = identitySelector;
            }
        } else if (Filter.class.isAssignableFrom(expectedType)) {
            if (value instanceof String) {
                value = Filter.compile(otoa(value));
            }
        } else if (Script.class.isAssignableFrom(expectedType)) {
            Script script = Utilities.getAsScript(value);
            if (script == null) {
                throw new IllegalArgumentException("Cannot convert " + value.getClass().getName() + " to " + expectedType.getName());
            }
            value = script;
        } else if (SailPointObject.class.isAssignableFrom(expectedType)) {
            try {
                @SuppressWarnings("unchecked")
                Class<SailPointObject> spoClass = (Class<SailPointObject>) expectedType;
                if (value instanceof String) {
                    String string = (String) value;
                    if (string.startsWith("<")) {
                        // Parse the string as XML if it looks like XML
                        value = AbstractXmlObject.parseXml(SailPointFactory.getCurrentContext(), string);
                    } else {
                        // Otherwise assume it's an identifier and look up the object
                        value = SailPointFactory.getCurrentContext().getObject(spoClass, string);
                    }
                } else if (value instanceof Reference) {
                    Reference ref = (Reference) value;
                    value = ref.resolve(SailPointFactory.getCurrentContext());
                } else {
                    throw new IllegalArgumentException("Cannot convert " + value.getClass().getName() + " to " + expectedType.getName());
                }
            } catch(GeneralException e) {
                throw new ObjectMapperException(e);
            }
        } else {
            throw new IllegalArgumentException("Cannot convert " + value.getClass().getName() + " to " + expectedType.getName());
        }
        return value;
    }


    /**
     * Decodes the given Custom object into an instance of the mapped type.
     *
     * @param configuration The Custom object to convert
     * @return An object of the expected type
     * @throws GeneralException if any failure occur
     */
    public T decode(Custom configuration) throws GeneralException, ObjectMapperException {
        return decode(configuration, true);
    }

    /**
     * Decodes the given Custom object into an instance of the mapped type.
     *
     * @param configuration The Custom object to convert
     * @param cache If true, the cached value will be returned if possible
     * @return An object of the expected type
     * @throws GeneralException if any failure occur
     */
    public T decode(Custom configuration, boolean cache) throws GeneralException, ObjectMapperException {
        Map<String, Object> input = null;
        if (configuration != null) {
            input = configuration.getAttributes();
        }
        return decode(input, cache);
    }


    /**
     * Decodes the given Configuration object into an instance of the mapped type.
     *
     * @param configuration The Configuration object to convert
     * @return An object of the expected type
     * @throws GeneralException if any failure occur
     */
    public T decode(Configuration configuration) throws GeneralException, ObjectMapperException {
        return decode(configuration, true);
    }

    /**
     * Decodes the given Configuration object into an instance of the mapped type.
     *
     * @param configuration The Configuration object to convert
     * @param cache If true, the cached value will be returned if possible
     * @return An object of the expected type
     * @throws GeneralException if any failure occur
     */
    public T decode(Configuration configuration, boolean cache) throws GeneralException, ObjectMapperException {
        Map<String, Object> input = null;
        if (configuration != null) {
            input = configuration.getAttributes();
        }
        return decode(input, cache);
    }


}
