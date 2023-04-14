package com.identityworksllc.iiq.common.minimal.vo;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.identityworksllc.iiq.common.minimal.Utilities;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.Script;
import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLObjectFactory;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class IIQObjectSerializer extends StdSerializer<Object> {
    protected IIQObjectSerializer() {
        this(null);
    }

    public IIQObjectSerializer(Class<Object> t) {
        super(t);
    }

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        if (value == null || value instanceof AbstractXmlObject || value instanceof Script || value instanceof Map || value instanceof List || value instanceof String || value instanceof Date) {
            gen.writeStartObject();

            if (value != null) {
                String xml = XMLObjectFactory.getInstance().toXml(value);
                gen.writeStringField("xml", xml);
            } else {
                gen.writeStringField("xml", null);
            }

            String xmlClass;

            if (value == null || value instanceof AbstractXmlObject || value instanceof Script) {
                // Returns string 'null' if the input is null
                xmlClass = Utilities.safeClassName(value);
            } else if (value instanceof Map) {
                xmlClass = "java.util.Map";
            } else if (value instanceof List) {
                xmlClass = "java.util.List";
            } else if (value instanceof Date) {
                xmlClass = Date.class.getName();
            } else {
                xmlClass = String.class.getName();
            }

            gen.writeStringField("type", xmlClass);

            gen.writeEndObject();
        } else {
            throw new IOException("Input type must be serializable by IIQ XMLObjectFactory");
        }
    }
}
