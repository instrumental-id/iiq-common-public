package com.identityworksllc.iiq.common.vo;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * A custom Jackson serializer for transforming a Throwable into a standard
 * Map format, including type, message, and stack trace.
 *
 * This is primarily intended for REST API usage.
 */
public class ThrowableSerializer extends StdSerializer<Throwable> {
    /**
     * Constructs a new StampedMessageSerializer
     */
    public ThrowableSerializer() {
        this(null);
    }

    /**
     * Constructs a new serializer
     * @param t The input type
     */
    public ThrowableSerializer(Class<Throwable> t) {
        super(t);
    }

    /**
     * Serializes the given Throwable object, if not null, into a map containing
     * a string type (the class name), the string message, and a stack trace.
     *
     * @param value The value to serialize
     * @param gen The JsonGenerator supplied by Jackson
     * @param provider The Jackson serialization provider
     * @throws IOException if serialization fails, especially of the stack trace
     */
    @Override
    public void serialize(Throwable value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        if (value != null) {
            gen.writeStartObject();
            gen.writeStringField("type", value.getClass().getName());
            gen.writeStringField("message", value.getMessage());
            try (StringWriter target = new StringWriter()) {
                try (PrintWriter printWriter = new PrintWriter(target)) {
                    value.printStackTrace(printWriter);
                }
                gen.writeStringField("stacktrace", target.toString());
            }
            gen.writeEndObject();
        } else {
            gen.writeNull();
        }
    }
}
