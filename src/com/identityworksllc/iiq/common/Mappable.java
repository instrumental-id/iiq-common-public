package com.identityworksllc.iiq.common;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.fasterxml.jackson.databind.type.MapType;
import sailpoint.tools.GeneralException;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * An interface implementing a {@link #toMap()} default method to transform
 * any object into a Map using Jackson. Simply implement this interface and
 * enjoy the use of toMap() without any boilerplate code.
 *
 * TODO Add the ability to implement a customer-specific set of mixins.
 */
public interface Mappable {

    /**
     * Sneaky mixin to enable this class to use its custom filter on classes
     * that don't support it
     */
    @JsonFilter("mappableFilter")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    final class FilterMixin {
        // Deliberately empty because this is only used for the annotations
    }

    /**
     * Returns a non-null Map representation of the given object using
     * Jackson as a transformation engine.
     *
     * If the input is null, an empty HashMap will be returned.
     *
     * @param whatever The object to transform
     * @param exclusions A set of fields to exclude from serialization, or null if none
     * @return The resulting map
     */
    static Map<String, Object> toMap(Object whatever, Set<String> exclusions) {
        if (whatever == null) {
            return new HashMap<>();
        }

        ObjectMapper mapper = new ObjectMapper();
        mapper.addMixIn(whatever.getClass(), FilterMixin.class);
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        SimpleFilterProvider filterProvider;
        if (exclusions != null && !exclusions.isEmpty()) {
            filterProvider = new SimpleFilterProvider().addFilter("mappableFilter", SimpleBeanPropertyFilter.serializeAllExcept(exclusions));
        } else {
            filterProvider = new SimpleFilterProvider().addFilter("mappableFilter", SimpleBeanPropertyFilter.serializeAll());
        }
        mapper.setFilterProvider(filterProvider);
        MapType javaType = mapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class);
        return mapper.convertValue(whatever, javaType);
    }

    /**
     * Returns a non-null Map representation of this object.
     *
     * Only non-null values will be included by default.
     *
     * @return A Map representation of this object.
     * @throws GeneralException if Map conversion fails for any reason
     */
    default Map<String, Object> toMap() throws GeneralException {
        return Mappable.toMap(this, toMapFieldExclusions());
    }

    /**
     * Optionally returns a list of fields to exclude from serialization. This
     * will be used in addition to any fields annotated with JsonIgnore.
     *
     * @return Returns a list of fields to exclude from serialization
     */
    default Set<String> toMapFieldExclusions() {
        return Collections.emptySet();
    }
}
