package com.identityworksllc.iiq.common.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import sailpoint.object.Identity;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an Identity in the system (or elsewhere in the environment). Fields are
 * somewhat arbitrary and depend on what you need to implement.
 *
 * This can be the source of an event, the target, etc.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class User {
    /**
     * The display name of the user
     */
    @JsonProperty
    private String displayName;
    /**
     * The internal Identity.id of the user
     */
    @JsonProperty
    private String id;
    /**
     * Any extra identifiers for this user, which can be used to identify them beyond what IIQ uses
     */
    @JsonProperty
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<Identifier> identifiers;
    /**
     * The Identity.name of the user
     */
    @JsonProperty
    private String name;

    /**
     * The type of the object (always "Identity" for now)
     */
    @JsonProperty
    private String type;

    /**
     * Default constructor for the User class. Initializes the identifiers list to an empty ArrayList.
     * This is necessary to avoid null pointer exceptions when adding identifiers to a User object
     * that was created using this constructor.
     */
    public User() {
        this.identifiers = new ArrayList<>();
    }

    /**
     * Constructor for the User class that takes an Identity object and a variable number of attribute
     * names to include as identifiers.
     * @param target the Identity object to create the User from
     * @param attributes a variable number of attribute names to include as identifiers for this User. For each attribute name provided, the constructor will attempt to retrieve the value of that attribute from the Identity object and add it as an Identifier to the User's identifiers list. If an attribute value is null, it will be skipped and not added to the identifiers list.
     */
    public User(Identity target, String... attributes) {
        this.displayName = target.getDisplayName();
        this.id = target.getId();
        this.name = target.getName();
        this.type = Identity.class.getSimpleName();
        this.identifiers = new ArrayList<>();
        for (String attribute : attributes) {
            Object value = target.getAttribute(attribute);
            if (value != null) {
                identifiers.add(new Identifier(attribute, value.toString()));
            }
        }
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getId() {
        return id;
    }

    public List<Identifier> getIdentifiers() {
        return identifiers;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setIdentifiers(List<Identifier> identifiers) {
        this.identifiers = identifiers;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setType(String type) {
        this.type = type;
    }
}
