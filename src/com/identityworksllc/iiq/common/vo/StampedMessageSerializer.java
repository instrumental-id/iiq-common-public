package com.identityworksllc.iiq.common.vo;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.util.Map;

/**
 * Custom serializer for StampedMessage outputs, to avoid having to let Jackson
 * figure out how to serialize the Throwable object
 */
public class StampedMessageSerializer extends StdSerializer<StampedMessage> {

    /**
     * Constructs a new StampedMessageSerializer
     */
    public StampedMessageSerializer() {
        this(null);
    }

    /**
     * Constructs a new serializer
     * @param t The input type
     */
    public StampedMessageSerializer(Class<StampedMessage> t) {
        super(t);
    }

    @Override
    public void serialize(StampedMessage value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        Map<String, String> map = value.toMap();
        gen.writeStartObject();
        gen.writeStringField("timestamp", map.get("timestamp"));
        gen.writeStringField("formattedDate", map.get("formattedDate"));
        gen.writeStringField("level", map.get("level"));
        gen.writeStringField("message", map.get("message"));

        if (map.containsKey("exception")) {
            gen.writeStringField("exception", map.get("exception"));
        }

        gen.writeEndObject();
    }
}