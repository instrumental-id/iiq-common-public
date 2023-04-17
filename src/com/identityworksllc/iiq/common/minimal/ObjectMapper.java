package com.identityworksllc.iiq.common.minimal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.tools.GeneralException;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * A utility to decode a Map structure into the fields of a POJO object. This makes
 * configurations type-safe and allows code accessing them to be verified.
 *
 * ----
 *
 * TYPE MAPPINGS:
 *
 * This class can coerce the following field types natively:
 *
 *  - Boolean
 *  - String
 *  - Numeric values
 *  - List of strings
 *  - List of nested objects
 *  - Map from string to nested objects
 *  - Date
 *
 * The {@link SailpointObjectMapper} subclass (the default) also handles:
 *
 *  - IdentitySelector
 *  - Filter
 *  - Script
 *  - A Reference object
 *  - Any SailPointObject type that can be looked up via a string ID or name
 *
 * Inaccessible fields and extra Map keys will be safely ignored, but a debug message
 * will be logged if debug logging is enabled for this class.
 * ----
 *
 * NESTING and ANNOTATIONS:
 *
 * You must use the @Nested annotation to indicate that a field is to be ObjectMapped
 * itself. If the field is a list of objects or a map, you must specify the type of
 * objects in the list or the map values as the argument to @Nested.
 *
 *   \@Nested(SomeType.class)
 *   private Map<String, SomeType> map;
 *
 *   \@Nested(ListType.class)
 *   private List<ListType> list;
 *
 * Maps with key types other than strings are not supported. (TODO)
 *
 * If the field's type is itself annotated with @{@link javax.xml.bind.annotation.XmlRootElement}
 * (from JAXB), it will be assumed to be @Nested automatically.
 *
 * Fields annotated with {@link Ignore} will always be unmapped. For example, a field
 * that you set in the default constructor may be ignored.
 *
 * If a class is annotated with {@link IgnoreSuper}, all superclass fields will be
 * ignored above that class in the hierarchy. This allows you to extend provided
 * classes without having to worry about the ObjectMapper scanning their fields.
 *
 * By default, a field X is set via the broadest method named setX that is compatible
 * with the field's type. For example, if a field is of type Collection, a setX that
 * takes a List will be preferred over a setX that takes an ArrayList. You may also
 * specify a different setter method name using @SetterName on the field.
 *
 * ----
 *
 * CACHING:
 *
 * If the second parameter to any of the decode() methods is true, a cached copy of
 * the decoded object will be returned. That is, two calls to decode with an identical
 * Map input will produce the same (==) object output.
 *
 * The default for the Configuration and Custom decode() methods is to true (yes do
 * use the cache), and for the Map input it is false.
 *
 * Cached decoded objects will be retained forever.
 *
 * ----
 *
 * SHARING:
 *
 * By default, this class has a strong dependency on its subclass, {@link SailpointObjectMapper},
 * in that an instance of that class is always returned from {@link #get(Class)}. However, it is
 * designed so that it can be fairly easily detached with only minimal modifications. It has no
 * other external dependencies except Apache Commons Logging.
 *
 * @param <T> The type of object being mapped
 */
public class ObjectMapper<T> {

    /**
     * Implementing this interface allows a class to coerce itself from one or more
     * input types. When converting an object of this type, a new instance will be
     * constructed using the no-args constructor. Then, {@link #canCoerce(Object)}
     * will be invoked to verify that the object is supported. If so, the object
     * will be initialized via {@link #initializeFrom(Object)}.
     */
    public interface Convertible {
        /**
         * Invoked by the ObjectMapper to ensure that the given input is appropriate
         * for coercion
         *
         * @param input The input object for testing
         * @return True if the input can be coerced into this type
         */
        boolean canCoerce(Object input);

        /**
         * Initializes this object from the given input
         * @param input The input object
         */
        void initializeFrom(Object input) throws ObjectMapperException;
    }

    /**
     * The method annotated with this annotation will be invoked after all mapped attributes
     * are set. This can be used to initialize the mapping.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface AfterMapperMethod {

    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Aliases {
        String[] value();
    }

    /**
     * Annotation to indicate that the given field should be ignored and
     * not mapped.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Ignore {

    }

    /**
     * Indicates that the annotated class should be the stopping point for
     * walking up the class hierarchy to find setters. This is important if you
     * are extending out of box classes you have no control over.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface IgnoreSuper {

    }

    /**
     * Annotation to indicate that the given element is a nested instance of the
     * mapped class, such as an identity with a list of identities.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD})
    public @interface Nested {
        Class<?> value() default Nested.class;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface RawMap {

    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface SetterMethod {
        String value() default "";
    }

    /**
     * A functional interface to translate a Class into its name. This may just
     * be Class::getName, but may be a more complex behavior as needed.
     */
    @FunctionalInterface
    public interface TypeNamer  {
        String getTypeName(Class<?> type);
    }

    /**
     * The exception type thrown by all mapper methods
     */
    public static class ObjectMapperException extends Exception {
        public ObjectMapperException() {
            super();
        }

        public ObjectMapperException(String message) {
            super(message);
        }

        public ObjectMapperException(String message, Throwable cause) {
            super(message, cause);
        }

        public ObjectMapperException(Throwable cause) {
            super(cause);
        }
    }

    /**
     * Translates from a Class type to a name. By default, just retrieves the name of the class.
     * Subclasses may extend this behavior if additional behavior is required. For example,
     * the SailPointTypeNamer also takes plugin classloader refreshes into account.
     */
    protected static class DefaultTypeNamer implements TypeNamer {
        public String getTypeName(Class<?> type) {
            return type.getName();
        }
    }

    /**
     * The type-to-name converter to use, namely the Sailpoint one
     */
    private static final TypeNamer TYPE_NAMER = new SailpointObjectMapper.SailPointTypeNamer();

    /**
     * The list of cached mapper objects
     */
    private static final Map<String, ObjectMapper<?>> cachedMappers = new ConcurrentHashMap<>();

    /**
     * Gets a predefined mapper for the given type. Mappers will be recalculated when
     * the plugin cache is updated, allowing mapped classes to be changed at runtime.
     *
     * @param type The type of class to map
     * @param <T> The type parameter
     * @return A mapper for that type
     */
    @SuppressWarnings("unchecked")
    public static <T> SailpointObjectMapper<T> get(Class<T> type) {
        String typeKey = TYPE_NAMER.getTypeName(type);
        if (!cachedMappers.containsKey(typeKey)) {
            synchronized(cachedMappers) {
                if (!cachedMappers.containsKey(typeKey)) {
                    SailpointObjectMapper<T> mapper = new SailpointObjectMapper<>(type);
                    cachedMappers.put(typeKey, mapper);
                }
            }
        }
        return (SailpointObjectMapper<T>)cachedMappers.get(typeKey);
    }

    /**
     * Returns true if the input string is not null or empty. This is a replica of the
     * IIQ version of this class so that this class can be separated from IIQ code
     * if needed.
     *
     * @param in The input string
     * @return True if the input string is not null or empty
     */
    public static boolean isNotNullOrEmpty(String in) {
        return !(in == null || in.trim().isEmpty());
    }

    /**
     * Returns a string representation of the input. If the input is null, returns null.
     * If the input is a string, returns the input. If the input is not a string, returns
     * the input as passed through {@link String#valueOf(Object)}.
     * @param in The input object
     * @return The input object converted to a string
     */
    public static String otoa(Object in) {
        if (in == null) {
            return null;
        } else if (in instanceof String) {
            return (String)in;
        } else {
            return String.valueOf(in);
        }
    }

    /**
     * Returns a Boolean reprsentation of the object. If the object is a Boolean,
     * it will be returned as-is. If the object is a String, the result will be the
     * outcome of {@link Boolean#parseBoolean(String)}. Otherwise, the result is false.
     * @param in The input object
     * @return The output as described above
     */
    public static boolean otob(Object in) {
        if (in == null) {
            return false;
        } else if (in instanceof Boolean) {
            return (Boolean) in;
        } else if (in instanceof String) {
            return Boolean.parseBoolean((String)in);
        }
        return false;
    }

    /**
     * Transforms an arbitrary object into a List. If the input is null, or if none
     * of the below conditions match, the output is null. If the input is a List, it
     * is returned as-is. If the input is another Collection, it is copied into a
     * List and returned. If the input is a String, it is split on commas as a CSV
     * and returned.
     *
     * This method uses {@link CommonConstants#FUNC_CSV_PARSER}, so if you copy it
     * outside of a Sailpoint environment, you'll want to change that.
     *
     * @param in The input object
     * @return The input converted to a list, or null if it could not be converted
     */
    @SuppressWarnings("unchecked")
    public static List<String> otol(Object in) {
        if (in == null) {
            return null;
        } else if (in instanceof List) {
            return (List<String>)in;
        } else if (in instanceof Collection) {
            Collection<String> strings = (Collection<String>)in;
            return new ArrayList<>(strings);
        } else if (in instanceof String) {
            String str = (String)in;
            return CommonConstants.FUNC_CSV_PARSER.apply(str);
        }
        return null;
    }
    /**
     * Caches already-seen converted Maps so that we don't have to continually reconvert
     * the same maps into the same objects. Two subsequent calls to decode() with an identical
     * map should result in the same (==) object.
     */
    private final ConcurrentHashMap<Integer, T> cachedConfigs;
    /**
     * The initializer method (if defined) to invoke after all setup is done
     */
    private MethodHandle initializer;
    /**
     * Logger
     */
    private final Log log;
    /**
     * The lookup of field names that are nested types, indicated by the @Nested
     * annotation on the field.
     */
    private final ConcurrentHashMap<String, ObjectMapper<?>> nested;
    /**
     * The type to which each field value must be coerced
     */
    private final ConcurrentHashMap<String, Class<?>> setterTypes;
    /**
     * The setter method handles, equivalent to either obj.setX(v) or obj.x = v,
     * depending on what's available at scan time
     */
    private final ConcurrentHashMap<String, MethodHandle> setters;
    /**
     * The target class being mapped
     */
    private final Class<T> targetClass;

    /**
     * Basic constructor. You should prefer {@link ObjectMapper#get(Class)} to this.
     * @param targetClass the target class
     */
    public ObjectMapper(Class<T> targetClass) {
        this.targetClass = targetClass;
        this.cachedConfigs = new ConcurrentHashMap<>();
        this.setters = new ConcurrentHashMap<>();
        this.setterTypes = new ConcurrentHashMap<>();
        this.nested = new ConcurrentHashMap<>();
        this.log = LogFactory.getLog(this.getClass());
        this.initializer = null;
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
        if (Convertible.class.isAssignableFrom(expectedType)) {
            try {
                Convertible instance = (Convertible) expectedType.getConstructor().newInstance();
                if (instance.canCoerce(value)) {
                    instance.initializeFrom(value);
                    return instance;
                }
            } catch(Exception e) {
                throw new ObjectMapperException(e);
            }
        }
        if (expectedType.equals(Boolean.TYPE) || expectedType.equals(Boolean.class)) {
            value = otob(value);
        } else if (expectedType.equals(String.class)) {
            value = otoa(value);
        } else if (expectedType.equals(Long.TYPE) || expectedType.equals(Long.class)) {
            value = Long.parseLong(otoa(value));
        } else if (expectedType.equals(Integer.TYPE) || expectedType.equals(Integer.class)) {
            value = Integer.parseInt(otoa(value));
        } else if (expectedType.equals(Date.class)) {
            if (value instanceof Long) {
                value = new Date((Long) value);
            } else if (value instanceof java.sql.Date) {
                value = new Date(((java.sql.Date)value).getTime());
            } else {
                throw new IllegalArgumentException("Cannot convert " + value.getClass().getName() + " to " + expectedType.getName());
            }
        } else if (Collection.class.isAssignableFrom(expectedType)) {
            value = otol(value);
        } else {
            throw new IllegalArgumentException("Cannot convert " + value.getClass().getName() + " to " + expectedType.getName());
        }
        return value;
    }

    /**
     * Decodes the given Map object into an instance of the mapped type. If the introspection
     * code is not initialized, it will be initialized at this time.
     *
     * If a null map is passed, it will be swapped for an empty map.
     * 
     * Equivalent to decode(map, false)
     *
     * @param map The map object to convert
     * @return An object of the expected type
     * @throws ObjectMapperException if any failure occur
     */
    public T decode(Map<String, Object> map) throws ObjectMapperException {
        return decode(map, false);
    }

    /**
     * Decodes the given Map object into an instance of the mapped type. If the introspection
     * code is not initialized, it will be initialized at this time.
     *
     * If a null map is passed, it will be swapped for an empty map.
     *
     * @param map The map object to convert
     * @param cache If true, the cached value will be returned if possible
     * @return An object of the expected type
     * @throws ObjectMapperException if any failure occur
     */
    public T decode(Map<String, Object> map, boolean cache) throws ObjectMapperException {
        if (map == null) {
            map = new HashMap<>();
        }
        initSetters();
        T result = cache ? cachedConfigs.get(map.hashCode()) : null;
        if (result != null) {
            return result;
        }
        try {
            result = targetClass.newInstance();
        } catch(Exception e) {
            throw new ObjectMapperException(e);
        }
        for(String key : map.keySet()) {
            Object value = map.get(key);
            if (value != null) {
                Class<?> expectedType = setterTypes.get(key);
                if (log.isTraceEnabled()) {
                    log.trace("Decoding value " + value + " into field " + key + " with expected type " + expectedType);
                }
                if (expectedType != null) {
                    if (nested.containsKey(key)) {
                        if (log.isTraceEnabled()) {
                            log.trace("Field " + key + " is a nested field with type " + nested.get(key).getTargetClass().getName());
                        }
                        ObjectMapper<?> nestedMapper = nested.get(key);
                        if (value instanceof Iterable) {
                            value = decodeNestedIterable(key, value, expectedType, nestedMapper);
                        } else if (value instanceof Map) {
                            if (Hashtable.class.isAssignableFrom(expectedType)) {
                                value = decodeNestedMap(value, null, nestedMapper, Hashtable::new);
                            } else if (Map.class.isAssignableFrom(expectedType)) {
                                value = decodeNestedMap(value, expectedType, nestedMapper, null);
                            } else {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> m = (Map<String, Object>) value;
                                value = nestedMapper.decode(m, cache);
                            }
                        } else {
                            throw new IllegalArgumentException("The field " + key + " requires a map or a list of nested maps");
                        }
                    } else {
                        Object converted = convertObject(value, expectedType);
                        if (converted == null) {
                            throw new IllegalArgumentException("For field " + key + ", could not convert object of type " + value.getClass().getName() + " to type " + expectedType.getName());
                        }
                        value = converted;
                    }
                    try {
                        MethodHandle mh = setters.get(key);
                        if (mh != null) {
                            Object[] params = new Object[2];
                            params[0] = result;
                            params[1] = value;
                            mh.invokeWithArguments(params);
                        }
                    } catch (Throwable t) {
                        throw new ObjectMapperException(t);
                    }
                } else {
                    if (log.isTraceEnabled()) {
                        log.trace("Ignoring unrecognized map key " + key);
                    }
                }
            }
        }

        if (initializer != null) {
            try {
                Object[] params = new Object[1];
                params[0] = result;
                initializer.invokeWithArguments(params);
            } catch(Throwable t) {
                throw new ObjectMapperException(t);
            }
        }

        if (result instanceof MapDecodable) {
            ((MapDecodable) result).initializeFromMap(map);
        }

        if (cache) {
            // Why am I copying this? Why didn't I comment on this when I wrote it?
            Map<String, Object> copy = new HashMap<>(map);
            cachedConfigs.put(copy.hashCode(), result);
        }

        return result;
    }

    /**
     * Decodes the input value into an Iterable of the expected type. This method supports
     * decoding Lists, Sets, and Queues.
     *
     * @param key The key, used only for logging purposes
     * @param value The value to decode
     * @param expectedType The expected type of the output, such as a List
     * @param nestedMapper The nested mapper to use to decode values, if any
     * @return The resulting Iterable object
     * @throws ObjectMapperException if any failures occur
     */
    private Object decodeNestedIterable(String key, Object value, Class<?> expectedType, ObjectMapper<?> nestedMapper) throws ObjectMapperException {
        Collection<Object> nested;
        if (expectedType.equals(Collection.class) || expectedType.equals(Iterable.class) || expectedType.equals(List.class) || expectedType.equals(ArrayList.class) || expectedType.equals(LinkedList.class)) {
            nested = new ArrayList<>();
        } else if (expectedType.equals(Set.class) || expectedType.equals(HashSet.class) || expectedType.equals(TreeSet.class)) {
            nested = new HashSet<>();
        } else if (Collection.class.isAssignableFrom(expectedType)) {
            try {
                @SuppressWarnings("unchecked")
                Collection<Object> exemplar = (Collection<Object>) expectedType.newInstance();
                if (exemplar instanceof Queue) {
                    nested = new LinkedList<>();
                } else if (exemplar instanceof List) {
                    nested = new ArrayList<>();
                } else if (exemplar instanceof Set) {
                    nested = new HashSet<>();
                } else {
                    throw new ObjectMapperException("Illegal destination type of a nested mapped list: " + expectedType);
                }
            } catch(Exception e) {
                throw new ObjectMapperException("Non-standard collection type must have a no-args constructor: " + expectedType);
            }
        } else {
            throw new ObjectMapperException("Illegal destination type of a nested mapped list: " + expectedType);
        }
        Class<?> newTargetClass = nestedMapper.targetClass;
        @SuppressWarnings("unchecked")
        Iterable<Object> input = (Iterable<Object>) value;
        for (Object member : input) {
            boolean handled = false;
            if (member == null) {
                nested.add(null);
            } else {
                if (newTargetClass.isAssignableFrom(member.getClass())) {
                    nested.add(member);
                    handled = true;
                } else if (Convertible.class.isAssignableFrom(newTargetClass)) {
                    try {
                        Convertible target = (Convertible) newTargetClass.getConstructor().newInstance();
                        if (target.canCoerce(member)) {
                            target.initializeFrom(member);
                            nested.add(target);
                            handled = true;
                        }
                    } catch (Exception e) {
                        throw new ObjectMapperException(e);
                    }
                }
                if (!handled) {
                    if (member instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> m = (Map<String, Object>) member;
                        Object sub = nestedMapper.decode(m);
                        nested.add(sub);
                    } else {
                        throw new IllegalArgumentException("The field " + key + " requires a nested map");
                    }
                }
            }
        }

        value = nested;
        return value;
    }

    private Object decodeNestedMap(Object value, Class<?> expectedType, ObjectMapper<?> nestedMapper, Supplier<? extends Map<String, Object>> mapCreator) throws ObjectMapperException {
        if (!(value instanceof Map)) {
            throw new ObjectMapperException("The value passed to decodeNestedMap() must, in fact, be a Map");
        }
        Map<String, Object> nestedMap = new HashMap<>();

        if (mapCreator != null) {
            nestedMap = mapCreator.get();
        } else if (expectedType != null) {
            Class<?>[] mapClasses = new Class[]{
                    HashMap.class,
                    TreeMap.class
            };
            for (Class<?> possibleClass : mapClasses) {
                if (expectedType.isAssignableFrom(possibleClass)) {
                    try {
                        nestedMap = (Map<String, Object>) possibleClass.newInstance();
                    } catch (Exception e) {
                        throw new ObjectMapperException(e);
                    }
                }
            }
        }
        Class<?> newTargetClass = nestedMapper.targetClass;
        @SuppressWarnings("unchecked")
        Map<String, Object> input = (Map<String, Object>) value;
        for (String nestedKey : input.keySet()) {
            Object encoded = input.get(nestedKey);
            if (encoded == null) {
                continue;
            }
            boolean handled = false;
            if (encoded instanceof Map) {
                Map<String, Object> encodedMap = (Map<String, Object>)encoded;
                Object decoded = nestedMapper.decode(encodedMap);
                nestedMap.put(nestedKey, decoded);
                handled = true;
            } else if (Convertible.class.isAssignableFrom(newTargetClass)) {
                try {
                    Convertible target = (Convertible) newTargetClass.getConstructor().newInstance();
                    if (target.canCoerce(encoded)) {
                        target.initializeFrom(encoded);
                        nestedMap.put(nestedKey, target);
                        handled = true;
                    }
                } catch(Exception e) {
                    throw new ObjectMapperException(e);
                }
            } else {
                Object decoded = convertObject(encoded, newTargetClass);
                nestedMap.put(nestedKey, decoded);
                handled = true;
            }
            if (!handled) {
                throw new ObjectMapperException("The encoded object of class " + encoded.getClass() + " could not be decoded to target type " + newTargetClass.getName());
            }
        }
        value = nestedMap;
        return value;
    }

    /**
     * Finds an annotation anywhere in the class hierarchy at or above this type
     * @param type The type to can
     * @param annotation The annotation to look for
     * @return True if the annotation is present on this class or any superclass
     */
    private boolean findAnnotation(Class<?> type, Class<? extends Annotation> annotation) {
        if (annotation == null || type == null) {
            return false;
        }
        Class<?> cur = type;
        while(cur.getSuperclass() != null) {
            if (cur.isAnnotationPresent(annotation)) {
                return true;
            }
            cur = cur.getSuperclass();
        }
        return false;
    }

    /**
     * Gets the field names and any of its aliases from the {@link Aliases} annotation
     * @param fieldName The field name
     * @param field The field being analyzed
     * @return The list of names for this field, including any aliases
     */
    private List<String> getNamesWithAliases(String fieldName, Field field) {
        List<String> names = new ArrayList<>();
        names.add(fieldName);

        if (field.isAnnotationPresent(Aliases.class)) {
            Aliases aliasAnnotation = field.getAnnotation(Aliases.class);
            if (aliasAnnotation != null && aliasAnnotation.value() != null) {
                names.addAll(Arrays.asList(aliasAnnotation.value()));
            }
        }

        return names;

    }

    /**
     * Gets the target class
     * @return The target class
     */
    public Class<T> getTargetClass() {
        return targetClass;
    }

    /**
     * Initializes the setter for the given field, locating the least narrow setter
     * method with the appropriate name. So, if there are several set methods:
     *
     *  setField(ArrayList)
     *  setField(List)
     *  setField(Collection)
     *
     * The final one, taking a Collection, will be selected here.
     *
     * @param setterMap The map to which the setters need to be added
     * @param lookupUtility The JDK MethodHandle lookup utility
     * @param field The field being analyzed
     * @throws NoSuchFieldException if there are any issues getting the field accessor
     */
    private void initSetterForField(Map<String, MethodHandle> setterMap, MethodHandles.Lookup lookupUtility, Field field) throws NoSuchFieldException {
        String fieldName = field.getName();
        try {
            // Find a legit setter method. We don't care about capitalization
            String setterName = "set" + field.getName();
            if (field.isAnnotationPresent(SetterMethod.class)) {
                SetterMethod setterAnnotation = field.getAnnotation(SetterMethod.class);
                if (isNotNullOrEmpty(setterAnnotation.value())) {
                    setterName = setterAnnotation.value();
                }
            }
            // Use reflection to find the setter method; using MethodType is not as
            // flexible because we would need to specify a return type. We only care
            // about the name and the parameter type. We loop over the methods from
            // 'targetClass' and not 'cls' because we want to call inherited methods
            // if they are available, rather than the superclass abstract ones.
            Method leastNarrow = null;
            for(Method m : targetClass.getMethods()) {
                if (m.getName().equalsIgnoreCase(setterName)) {
                    if (m.getParameterCount() == 1 && field.getType().isAssignableFrom(m.getParameterTypes()[0])) {
                        if (leastNarrow == null || m.getParameterTypes()[0].equals(field.getType())) {
                            leastNarrow = m;
                        } else {
                            Class<?> existingParamType = leastNarrow.getParameterTypes()[0];
                            Class<?> newParamType = m.getParameterTypes()[0];
                            if (!existingParamType.isAssignableFrom(newParamType) && newParamType.isAssignableFrom(existingParamType)) {
                                if (log.isTraceEnabled()) {
                                    log.trace("For field " + fieldName + " setter method with param type " + newParamType + " is more general than setter with param type " + existingParamType);
                                }
                                leastNarrow = m;
                            }
                        }
                    }
                }
            }
            if (leastNarrow == null) {
                // If we can't find a legit setter method, attempt a direct field write
                if (log.isTraceEnabled()) {
                    log.trace("For field " + fieldName + " could not find setter method " + setterName + ", attempting direct field write access");
                }
                insertDirectFieldWrite(setterMap, lookupUtility, field, fieldName);
            } else {
                insertSetterMethodCall(setterMap, lookupUtility, field, fieldName, leastNarrow);
            }
        } catch (IllegalAccessException e) {
            // Quietly log and continue
            if (log.isDebugEnabled()) {
                log.debug("For mapped type " + targetClass.getName() + ", no accessible setter or field found for name " + field.getName());
            }
        }
    }

    /**
     * Initializes the setters of this class and any of its superclasses in
     * a synchronized block on the first call to decode().
     *
     * TODO do we want to cache this?
     *
     * @throws ObjectMapperException If any failures occur looking up method handles
     */
    private void initSetters() throws ObjectMapperException {
        if (setters.isEmpty()) {
            synchronized (setters) {
                if (setters.isEmpty()) {
                    try {
                        Class<? extends Annotation> rootElemAnnotation = null;
                        try {
                            rootElemAnnotation = (Class<? extends Annotation>) Class.forName("javax.xml.bind.annotation.XmlRootElement");
                        } catch(Exception e) {
                            /* Ignore it */
                        }
                        Class<?> cls = targetClass;
                        Map<String, MethodHandle> tempSetters = new HashMap<>();
                        MethodHandles.Lookup lookup = MethodHandles.lookup();
                        while (cls.getSuperclass() != null) {
                            if (log.isTraceEnabled()) {
                                log.trace("Scanning fields in class " + cls.getName());
                            }
                            for (Field f : cls.getDeclaredFields()) {
                                initAnalyzeField(rootElemAnnotation, tempSetters, lookup, f);
                            } // end field loop
                            if (cls.isAnnotationPresent(IgnoreSuper.class)) {
                                if (log.isTraceEnabled()) {
                                    log.trace("Class " + cls.getName() + " specifies IgnoreSuper; stopping hierarchy scan here");
                                }
                                break;
                            }
                            cls = cls.getSuperclass();
                        } // walk up class hierarchy
                        setters.putAll(tempSetters);
                        for(Method m : targetClass.getMethods()) {
                            if (m.isAnnotationPresent(AfterMapperMethod.class)) {
                                initializer = lookup.unreflect(m);
                                break;
                            }
                        }
                    } catch(Exception e){
                        throw new ObjectMapperException(e);
                    }
                }
            }
        }
    }

    /**
     * For the given field, initializes the setter map if it's accessible and
     * is not ignored.
     *
     * @param rootElemAnnotation The cached XMLRootElement annotation, just for easier type checking
     * @param tempSetters The temporary setters
     * @param lookup The MethodHandle lookup utility from the JDK
     * @param field The field being analyzed
     * @throws NoSuchFieldException if there is a problem accessing the field
     */
    private void initAnalyzeField(Class<? extends Annotation> rootElemAnnotation, Map<String, MethodHandle> tempSetters, MethodHandles.Lookup lookup, Field field) throws NoSuchFieldException {
        if (Modifier.isStatic(field.getModifiers())) {
            if (log.isTraceEnabled()) {
                log.trace("Ignore field " + field.getName() + " because it is static");
            }
            return;
        }
        if (field.isAnnotationPresent(Ignore.class)) {
            if (log.isTraceEnabled()) {
                log.trace("Ignore field " + field.getName() + " because it specifies @Ignore");
            }
            return;
        }
        if (field.isAnnotationPresent(Nested.class) || (findAnnotation(field.getType(), rootElemAnnotation))) {
            Class<?> nestedType = field.getType();
            Nested nestedAnnotation = field.getAnnotation(Nested.class);
            if (nestedAnnotation != null) {
                if (nestedAnnotation.value().equals(Nested.class)) {
                    // This is the default if you leave the Nested annotation empty. You should probably not do this.
                    if (List.class.isAssignableFrom(field.getType())) {
                        nestedType = targetClass;
                    }
                } else {
                    // Explicit mapped type set
                    nestedType = nestedAnnotation.value();
                }
            }
            nested.put(field.getName(), get(nestedType));
        }
        initSetterForField(tempSetters, lookup, field);
    }

    /**
     * Inserts a MethodHandle directly setting the value for the given field, or
     * any of its alias names.
     *
     * @param setterMap The setter map
     * @param lookupUtility The lookup utility for getting the MethodHandle
     * @param field The field being evaluated
     * @param fieldName The field name
     * @throws IllegalAccessException if any failure occurs accessing the field
     */
    private void insertDirectFieldWrite(Map<String, MethodHandle> setterMap, MethodHandles.Lookup lookupUtility, Field field, String fieldName) throws NoSuchFieldException, IllegalAccessException {
        List<String> names = getNamesWithAliases(fieldName, field);

        for(String name: names) {
            setterTypes.put(name, field.getType());
            setterMap.put(name, lookupUtility.findSetter(targetClass, fieldName, field.getType()));
        }
    }

    /**
     * Inserts a MethodHandle invoking the setter method for the given field, or
     * any of its alias names.
     *
     * @param setterMap The setter map
     * @param lookupUtility The lookup utility for getting the MethodHandle
     * @param field The field being evaluated
     * @param fieldName The field name
     * @param setterMethod The setter method located
     * @throws IllegalAccessException if any failure occurs accessing the setter
     */
    private void insertSetterMethodCall(Map<String, MethodHandle> setterMap, MethodHandles.Lookup lookupUtility, Field field, String fieldName, Method setterMethod) throws IllegalAccessException {
        if (log.isTraceEnabled()) {
            log.trace("For field " + fieldName + " found most general setter method " + setterMethod.getName() + " with param type " + setterMethod.getParameterTypes()[0].getName());
        }

        List<String> names = getNamesWithAliases(fieldName, field);

        for(String name: names) {
            setterTypes.put(name, setterMethod.getParameterTypes()[0]);
            setterMap.put(name, lookupUtility.unreflect(setterMethod));
        }
    }

}
