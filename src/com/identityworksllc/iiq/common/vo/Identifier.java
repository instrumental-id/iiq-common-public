package com.identityworksllc.iiq.common.vo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A simple class representing an identifier for a subject. This consists of an attribute name and a value.
 */
public class Identifier {
    /**
     * The name of the attribute that serves as an identifier for the subject. This could be something like
     * "employeeNumber", "email", or "uid".
     */
    @JsonProperty
    private String attribute;

    /**
     * The value of the identifier attribute. This is the actual identifier value, such as "12345"
     * for an employee number.
     */
    @JsonProperty
    private String value;

    /**
     * Constructor for the Identifier class. This is annotated with @JsonCreator to allow Jackson to create it
     * @param attribute the name of the attribute that serves as an identifier for the subject
     * @param value the value of the identifier attribute
     */
    @JsonCreator
    public Identifier(@JsonProperty("attribute") String attribute, @JsonProperty("value") String value) {
        this.attribute = attribute;
        this.value = value;
    }

    public String getAttribute() {
        return attribute;
    }

    public String getValue() {
        return value;
    }
}
