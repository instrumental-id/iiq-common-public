package com.identityworksllc.iiq.common.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

/**
 * Message body reader that can be reused across plugins. As it turns out, Jersey has a hard
 * time when more than one message body reader can read the same input. This class will allow
 * any installed plugin's message body reader to deserialize any plugin's VO classes.
 */
public abstract class PluginJacksonMessageBodyReader implements MessageBodyReader<Object> {
    /**
     * Logger
     */
    private static final Log log = LogFactory.getLog(PluginJacksonMessageBodyReader.class);

    /**
     * The package prefix, used to decide whether this class is decodable
     */
    private final String packagePrefix;

    /**
     * Sets the package prefix
     * @param packagePrefix The package prefix for your implementation
     */
    protected PluginJacksonMessageBodyReader(String packagePrefix) {
        if (Util.isNullOrEmpty(packagePrefix)) {
            throw new IllegalArgumentException("Package prefix must be non-empty");
        }

        // TODO read additional prefixes from a system property

        this.packagePrefix = packagePrefix;
    }

    /**
     * Determines whether the input is readable
     *
     * @see MessageBodyReader#isReadable(Class, Type, Annotation[], MediaType) 
     */
    @Override
    public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        if (log.isTraceEnabled()) {
            log.trace("Checking whether we can decode into type " + type.getName());
        }
        return type.getName().startsWith(packagePrefix);
    }

    /**
     * Reads the input in the context of the requested type's classloader
     * 
     * @see MessageBodyReader#readFrom(Class, Type, Annotation[], MediaType, MultivaluedMap, InputStream) 
     */
    @Override
    public Object readFrom(Class<Object> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, String> httpHeaders, InputStream entityStream) throws IOException, WebApplicationException {
        if (log.isTraceEnabled()) {
            log.trace("Reading message body into type " + type.getName());
        }

        try {
            try {
                String json = Util.readInputStream(entityStream);

                ObjectMapper om = new ObjectMapper();
                TypeFactory tf = TypeFactory.defaultInstance().withClassLoader(type.getClassLoader());
                om.setTypeFactory(tf);

                return om.readValue(json, type);
            } catch(IOException e) {
                if (log.isDebugEnabled()) {
                    log.debug("Caught an error reading JSON object into type " + type.getName(), e);
                }
                throw e;
            }
        } catch(GeneralException e) {
            throw new IOException(e);
        }
    }
}
