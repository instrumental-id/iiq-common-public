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

public class IIQObjectDeserializer extends StdDeserializer<Object> {
    private static final Set<String> VALID_TYPES = new HashSet<>(Arrays.asList(
            "java.util.Map",
            "java.util.List",
            "java.lang.String",
            "java.lang.Boolean",
            "java.util.Date"
    ));

    protected IIQObjectDeserializer() {
        this(null);
    }

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
