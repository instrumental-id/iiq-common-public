package com.identityworksllc.iiq.common.vo;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.XMLObjectFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * A Jackson de-serializer for an IIQ object. The input is expected to
 * have a 'type' and an 'xml'. The XML will be parsed using Sailpoint's
 * default {@link sailpoint.tools.xml.XMLObjectFactory} parser into an
 * object of the given type.
 */
public class IIQObjectDeserializer extends StdDeserializer<Object> {
    /**
     * The list of valid types apart from `sailpoint.object.*` classes
     */
    private static final Set<String> VALID_TYPES = new HashSet<>(Arrays.asList(
            "java.util.Map",
            "java.util.List",
            "java.lang.String",
            "java.lang.Boolean",
            "java.util.Date"
    ));

    /**
     * The constructor expected by Jackson
     */
    protected IIQObjectDeserializer() {
        this(null);
    }

    /**
     * The constructor expected by Jackson, providing the type of this object
     * @param t This type
     */
    public IIQObjectDeserializer(Class<Object> t) {
        super(t);
    }

    @Override
    public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        JsonNode node = p.getCodec().readTree(p);
        if (node.isObject()) {
            JsonNode xml = node.get("xml");
            JsonNode type = node.get("type");

            String typeStr = type.asText();
            if (typeStr == null || typeStr.trim().isEmpty()) {
                throw new IOException("Unable to read a serialized IIQObject without a type");
            }
            if (typeStr.equals("null") || xml.isNull()) {
                return null;
            }
            if (typeStr.startsWith("sailpoint.object") || VALID_TYPES.contains(typeStr)) {
                String xmlStr = xml.asText();

                try {
                    SailPointContext context = SailPointFactory.getCurrentContext();
                    if (context == null) {
                        // TODO create a private context here?
                        throw new IOException("IIQObject JSON must be deserialized in a SailPointContext session");
                    }
                    return XMLObjectFactory.getInstance().parseXml(context, xmlStr, true);
                } catch(GeneralException e) {
                    throw new IOException(e);
                }
            } else {
                throw new IOException("Unexpected IIQObject type: " + typeStr);
            }
        } else if (node.isNull()) {
            return null;
        } else {
            throw new IOException("Unexpected JsonNode type: " + node.getNodeType());
        }
    }

}
