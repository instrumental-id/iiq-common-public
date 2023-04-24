package com.identityworksllc.iiq.common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.identityworksllc.iiq.common.vo.IIQObject;
import sailpoint.object.IdentitySelector;
import sailpoint.object.Rule;
import sailpoint.object.Script;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * This is the implementation of the Common Security configuration object, as expected
 * by {@link ThingAccessUtils}. The intention is that {@link ObjectMapper} be used
 * to decode an instance of this class.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommonSecurityConfig implements Serializable, MapDecodable {
    /**
     * The cached object mapper to use for this decoding. Note that due to classloader
     * weirdness, plugins may each have their own, potentially obfuscated copy that is
     * different than the "base" code version.
     */
    private final static ObjectMapper<CommonSecurityConfig> objectMapper = ObjectMapper.get(CommonSecurityConfig.class);

    /**
     * Decodes the given Map into an instance of this class using {@link ObjectMapper}.
     *
     * @param input The input map
     * @return An instance of this class decoded from the Map
     * @throws GeneralException if any decoding failures occur
     */
    public static CommonSecurityConfig decode(Map<String, Object> input) throws GeneralException {
        try {
            return objectMapper.decode(input, true);
        } catch(ObjectMapper.ObjectMapperException e) {
            throw SailpointObjectMapper.unwrap(e);
        }
    }

    private static void handleNested(Map<String, Object> map, String name, List<? extends CommonSecurityConfig> list) {
        if (Utilities.isNotEmpty(list)) {
            map.put(name, list.stream().map(CommonSecurityConfig::toMap).collect(Collectors.toCollection(ArrayList::new)));
        }
    }

    /**
     * Creates a new {@link CommonSecurityConfig} that inverts the inputs. If any of
     * the nested checks passes, the inverted check will fail.
     *
     * @param others The other security configs to invert
     * @return The inverted security config
     * @throws GeneralException if any failures occur
     */
    public static CommonSecurityConfig not(CommonSecurityConfig... others) throws GeneralException {
        if (others == null || others.length == 0) {
            throw new IllegalArgumentException("At least one 'not' security string must be provided");
        }
        List<CommonSecurityConfig> configs = new ArrayList<>(Arrays.asList(others));
        CommonSecurityConfig masterConfig = new CommonSecurityConfig();
        masterConfig.not = configs;

        return masterConfig;
    }

    /**
     * Mainly intended for the test scripts, constructs a common security config
     * based on the single given field name and value. All other values in the
     * configuration will be empty or defaulted.
     *
     * @param field The field name
     * @param value The field value
     * @return A common security config as though a map were passed with that field set only
     * @throws GeneralException if any failures occur during parsing
     */
    public static CommonSecurityConfig simple(String field, Object value) throws GeneralException {
        Map<String, Object> input = new HashMap<>();
        input.put(field, value);
        return decode(input);
    }
    /**
     * The original map, or a reconstructed one, captured on decode() or
     * constructed on the first call to toMap().
     */
    @ObjectMapper.Ignore
    @JsonIgnore
    private final Map<String, Object> _originalMap;
    /**
     * The check will pass if the subject Identity matches the given Filter
     */
    /*package*/ String accessCheckFilter;
    /**
     * The check will pass if this rule returns Boolean true. The subject and target
     * will be passed to the script as 'subject' and 'target', respectively.
     */
    @IIQObject
    /*package*/ Rule accessCheckRule;
    /**
     * The check will pass if this script returns Boolean true. The subject and target
     * will be passed to the script as 'subject' and 'target', respectively.
     */
    @IIQObject
    /*package*/ Script accessCheckScript;
    /**
     * The check will pass if the subject Identity matches this IdentitySelector
     */
    @IIQObject
    /*package*/ IdentitySelector accessCheckSelector;
    /**
     * The check will pass if all of the configurations in this list pass
     */
    @ObjectMapper.Nested(CommonSecurityConfig.class)
    /*package*/ List<CommonSecurityConfig> allOf;
    /**
     * The description of this security config, which can be output in debug messages
     */
    private String description;
    /**
     * The check will always fail because it is disabled
     */
    private boolean disabled;
    /**
     * The check will fail if the subject Identity has any of the listed capabilities
     */
    /*package*/ List<String> excludedCapabilities;
    /**
     * The check will fail if the subject Identity has any of the listed rights.
     */
    /*package*/ List<String> excludedRights;
    /**
     * The check will fail if the subject Identity is a member of any of the listed workgroups
     */
    /*package*/ List<String> excludedWorkgroups;
    /**
     * The check will fail if the target matches the given Filter
     */
    /*package*/ String invalidTargetFilter;
    /**
     * The check will pass if the given Quicklink Population would allow access for the subject and target
     */
    /*package*/ String mirrorQuicklinkPopulation;
    /**
     * True if we should NOT cache the results of this check
     */
    private boolean noCache;
    /**
     * The check will pass if none of the configurations in this list pass
     */
    @ObjectMapper.Nested(CommonSecurityConfig.class)
    /*package*/ List<CommonSecurityConfig> not;
    /**
     * The check will pass if any of the configurations in this list passes
     */
    @ObjectMapper.Nested(CommonSecurityConfig.class)
    /*package*/ List<CommonSecurityConfig> oneOf;
    /**
     * The check will pass if the subject Identity has any of the listed capabilities
     */
    /*package*/ List<String> requiredCapabilities;
    /**
     * The check will pass if the subject Identity has any of the listed SPRights
     */
    /*package*/ List<String> requiredRights;
    /**
     * The check will pass if the subject Identity is a member of any of the listed workgroups
     */
    /*package*/ List<String> requiredWorkgroups;
    /**
     * If this Plugin setting is TRUE, the security check will always fail
     */
    /*package*/ String settingOffSwitch;
    /**
     * The check will pass if the target Identity has any of the listed capabilities
     */
    /*package*/ List<String> validTargetCapabilities;
    /**
     * The check will fail if the target Identity has any of the listed capabilities
     */
    /*package*/ List<String> validTargetExcludedCapabilities;
    /**
     * The check will fail if the target Identity has any of the listed SPRights
     */
    /*package*/ List<String> validTargetExcludedRights;
    /**
     * The check will pass if the target Identity matches the given Filter
     */
    /*package*/ String validTargetFilter;
    /**
     * The check will pass if the target Identity matches this IdentitySelector
     */
    @IIQObject
    /*package*/ IdentitySelector validTargetSelector;
    /**
     * The check will pass if the target Identity is a member of any of the listed workgroups
     */
    /*package*/ List<String> validTargetWorkgroups;

    /**
     * Basic constructor, which will initialize the various lists (because Jackson)
     */
    public CommonSecurityConfig() {
        this.not = new ArrayList<>();
        this.oneOf = new ArrayList<>();
        this.allOf = new ArrayList<>();
        this.excludedCapabilities = new ArrayList<>();
        this.excludedRights = new ArrayList<>();
        this.excludedWorkgroups = new ArrayList<>();
        this.validTargetCapabilities = new ArrayList<>();
        this.validTargetExcludedCapabilities = new ArrayList<>();
        this.validTargetExcludedRights = new ArrayList<>();
        this.validTargetWorkgroups = new ArrayList<>();
        this.requiredCapabilities = new ArrayList<>();
        this.requiredRights = new ArrayList<>();
        this.requiredWorkgroups = new ArrayList<>();
        this._originalMap = new HashMap<>();
    }

    public String getAccessCheckFilter() {
        return accessCheckFilter;
    }

    public Rule getAccessCheckRule() {
        return accessCheckRule;
    }

    public Script getAccessCheckScript() {
        return accessCheckScript;
    }

    public IdentitySelector getAccessCheckSelector() {
        return accessCheckSelector;
    }

    public List<CommonSecurityConfig> getAllOf() {
        return allOf;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getExcludedCapabilities() {
        return excludedCapabilities;
    }

    public List<String> getExcludedRights() {
        return excludedRights;
    }

    public List<String> getExcludedWorkgroups() {
        return excludedWorkgroups;
    }

    public String getInvalidTargetFilter() {
        return invalidTargetFilter;
    }

    public String getMirrorQuicklinkPopulation() {
        return mirrorQuicklinkPopulation;
    }

    public List<CommonSecurityConfig> getNot() {
        return not;
    }

    public List<CommonSecurityConfig> getOneOf() {
        return oneOf;
    }

    public List<String> getRequiredCapabilities() {
        return requiredCapabilities;
    }

    public List<String> getRequiredRights() {
        return requiredRights;
    }

    public List<String> getRequiredWorkgroups() {
        return requiredWorkgroups;
    }

    public String getSettingOffSwitch() {
        return settingOffSwitch;
    }

    public List<String> getValidTargetCapabilities() {
        return validTargetCapabilities;
    }

    public List<String> getValidTargetExcludedCapabilities() {
        return validTargetExcludedCapabilities;
    }

    public List<String> getValidTargetExcludedRights() {
        return validTargetExcludedRights;
    }

    public String getValidTargetFilter() {
        return validTargetFilter;
    }

    public IdentitySelector getValidTargetSelector() {
        return validTargetSelector;
    }

    public List<String> getValidTargetWorkgroups() {
        return validTargetWorkgroups;
    }

    /**
     * Stores the original map from which this entry was decoded, if possible.
     *
     * @param input The input
     * @throws ObjectMapper.ObjectMapperException if the decoding fails
     */
    @Override
    public void initializeFromMap(Map<String, Object> input) throws ObjectMapper.ObjectMapperException {
        if (this._originalMap.isEmpty()) {
            this._originalMap.putAll(input);
            handleNested(this._originalMap, "oneOf", this.oneOf);
            handleNested(this._originalMap, "allOf", this.allOf);
            handleNested(this._originalMap, "not", this.not);
        }
    }

    public boolean isDisabled() {
        return disabled;
    }

    public boolean isNoCache() {
        return noCache;
    }

    public void setAccessCheckFilter(String accessCheckFilter) {
        this.accessCheckFilter = accessCheckFilter;
    }

    public void setAccessCheckRule(Rule accessCheckRule) {
        this.accessCheckRule = accessCheckRule;
    }

    public void setAccessCheckScript(Script accessCheckScript) {
        this.accessCheckScript = accessCheckScript;
    }

    public void setAccessCheckSelector(IdentitySelector accessCheckSelector) {
        this.accessCheckSelector = accessCheckSelector;
    }

    public void setAllOf(List<CommonSecurityConfig> allOf) {
        this.allOf = allOf;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public void setExcludedCapabilities(List<String> excludedCapabilities) {
        this.excludedCapabilities = excludedCapabilities;
    }

    public void setExcludedRights(List<String> excludedRights) {
        this.excludedRights = excludedRights;
    }

    public void setExcludedWorkgroups(List<String> excludedWorkgroups) {
        this.excludedWorkgroups = excludedWorkgroups;
    }

    public void setInvalidTargetFilter(String invalidTargetFilter) {
        this.invalidTargetFilter = invalidTargetFilter;
    }

    public void setMirrorQuicklinkPopulation(String mirrorQuicklinkPopulation) {
        this.mirrorQuicklinkPopulation = mirrorQuicklinkPopulation;
    }

    public void setNoCache(boolean noCache) {
        this.noCache = noCache;
    }

    public void setNot(List<CommonSecurityConfig> not) {
        this.not = not;
    }

    public void setOneOf(List<CommonSecurityConfig> oneOf) {
        this.oneOf = oneOf;
    }

    public void setRequiredCapabilities(List<String> requiredCapabilities) {
        this.requiredCapabilities = requiredCapabilities;
    }

    public void setRequiredRights(List<String> requiredRights) {
        this.requiredRights = requiredRights;
    }

    public void setRequiredWorkgroups(List<String> requiredWorkgroups) {
        this.requiredWorkgroups = requiredWorkgroups;
    }

    public void setSettingOffSwitch(String settingOffSwitch) {
        this.settingOffSwitch = settingOffSwitch;
    }

    public void setValidTargetCapabilities(List<String> validTargetCapabilities) {
        this.validTargetCapabilities = validTargetCapabilities;
    }

    public void setValidTargetExcludedCapabilities(List<String> validTargetExcludedCapabilities) {
        this.validTargetExcludedCapabilities = validTargetExcludedCapabilities;
    }

    public void setValidTargetExcludedRights(List<String> validTargetExcludedRights) {
        this.validTargetExcludedRights = validTargetExcludedRights;
    }

    public void setValidTargetFilter(String validTargetFilter) {
        this.validTargetFilter = validTargetFilter;
    }

    public void setValidTargetSelector(IdentitySelector validTargetSelector) {
        this.validTargetSelector = validTargetSelector;
    }

    public void setValidTargetWorkgroups(List<String> validTargetWorkgroups) {
        this.validTargetWorkgroups = validTargetWorkgroups;
    }

    /**
     * Returns a Map representation of this {@link CommonSecurityConfig}.
     *
     * If it was originally constructed via ObjectMapper, the returned Map will be
     * a copy of the original input.
     *
     * @return The Map representation
     */
    public Map<String, Object> toMap() {
        if (this._originalMap.isEmpty()) {
            // TODO build a map where one doesn't exist
            final Map<String, Object> map = new HashMap<>();

            // Consumer to simplify taking a list of strings and adding it to the Map if not empty
            BiConsumer<String, List<String>> handleStringList = (name, list) -> {
                if (Utilities.isNotEmpty(list)) {
                    map.put(name, new ArrayList<>(list));
                }
            };

            handleNested(map, "oneOf", this.oneOf);
            handleNested(map, "allOf", this.allOf);
            handleNested(map, "not", this.not);

            handleStringList.accept("requiredRights", this.requiredRights);
            handleStringList.accept("requiredCapabilities", this.requiredCapabilities);
            handleStringList.accept("excludedWorkgroups", this.excludedWorkgroups);
            handleStringList.accept("requiredWorkgroups", this.requiredWorkgroups);
            handleStringList.accept("validTargetCapabilities", this.validTargetCapabilities);
            handleStringList.accept("validTargetExcludedRights", this.validTargetExcludedRights);
            handleStringList.accept("validTargetWorkgroups", this.validTargetWorkgroups);
            handleStringList.accept("validTargetExcludedCapabilities", this.validTargetExcludedCapabilities);
            handleStringList.accept("validTargetWorkgroups", this.validTargetWorkgroups);

            if (Util.isNotNullOrEmpty(this.description)) {
                map.put("description", this.description);
            }

            if (this.disabled) {
                map.put("disabled", true);
            }

            if (this.noCache) {
                map.put("noCache", true);
            }

            if (Util.isNotNullOrEmpty(this.settingOffSwitch)) {
                map.put("settingOffSwitch", this.settingOffSwitch);
            }

            if (this.validTargetSelector != null) {
                map.put("validTargetSelector", this.validTargetSelector);
            }

            if (Util.isNotNullOrEmpty(this.accessCheckFilter)) {
                map.put("accessCheckFilter", this.accessCheckFilter);
            }

            if (this.accessCheckScript != null) {
                map.put("accessCheckScript", this.accessCheckScript);
            }

            if (this.accessCheckRule != null) {
                map.put("accessCheckRule", this.accessCheckRule.getName());
            }

            if (this.accessCheckSelector != null) {
                map.put("accessCheckSelector", this.accessCheckSelector);
            }

            if (Util.isNotNullOrEmpty(this.invalidTargetFilter)) {
                map.put("invalidTargetFilter", this.invalidTargetFilter);
            }

            if (Util.isNotNullOrEmpty(this.mirrorQuicklinkPopulation)) {
                map.put("mirrorQuicklinkPopulation", this.mirrorQuicklinkPopulation);
            }

            this._originalMap.putAll(map);
        }

        return Collections.unmodifiableMap(this._originalMap);

    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(", ", CommonSecurityConfig.class.getSimpleName() + "[", "]");
        if ((accessCheckFilter) != null) {
            joiner.add("accessCheckFilter='" + accessCheckFilter + "'");
        }
        if ((accessCheckRule) != null) {
            joiner.add("accessCheckRule=" + accessCheckRule);
        }
        if ((accessCheckScript) != null) {
            joiner.add("accessCheckScript=" + accessCheckScript);
        }
        if ((accessCheckSelector) != null) {
            joiner.add("accessCheckSelector=" + accessCheckSelector);
        }
        if ((allOf) != null) {
            joiner.add("allOf=" + allOf);
        }
        if ((description) != null) {
            joiner.add("description='" + description + "'");
        }
        joiner.add("disabled=" + disabled);
        if ((excludedCapabilities) != null) {
            joiner.add("excludedCapabilities=" + excludedCapabilities);
        }
        if ((excludedRights) != null) {
            joiner.add("excludedRights=" + excludedRights);
        }
        if ((excludedWorkgroups) != null) {
            joiner.add("excludedWorkgroups=" + excludedWorkgroups);
        }
        if ((invalidTargetFilter) != null) {
            joiner.add("invalidTargetFilter='" + invalidTargetFilter + "'");
        }
        if ((mirrorQuicklinkPopulation) != null) {
            joiner.add("mirrorQuicklinkPopulation='" + mirrorQuicklinkPopulation + "'");
        }
        joiner.add("noCache=" + noCache);
        if ((not) != null) {
            joiner.add("not=" + not);
        }
        if ((oneOf) != null) {
            joiner.add("oneOf=" + oneOf);
        }
        if ((requiredCapabilities) != null) {
            joiner.add("requiredCapabilities=" + requiredCapabilities);
        }
        if ((requiredRights) != null) {
            joiner.add("requiredRights=" + requiredRights);
        }
        if ((requiredWorkgroups) != null) {
            joiner.add("requiredWorkgroups=" + requiredWorkgroups);
        }
        if ((settingOffSwitch) != null) {
            joiner.add("settingOffSwitch='" + settingOffSwitch + "'");
        }
        if ((validTargetCapabilities) != null) {
            joiner.add("validTargetCapabilities=" + validTargetCapabilities);
        }
        if ((validTargetExcludedCapabilities) != null) {
            joiner.add("validTargetExcludedCapabilities=" + validTargetExcludedCapabilities);
        }
        if ((validTargetExcludedRights) != null) {
            joiner.add("validTargetExcludedRights=" + validTargetExcludedRights);
        }
        if ((validTargetFilter) != null) {
            joiner.add("validTargetFilter='" + validTargetFilter + "'");
        }
        if ((validTargetSelector) != null) {
            joiner.add("validTargetSelector=" + validTargetSelector);
        }
        if ((validTargetWorkgroups) != null) {
            joiner.add("validTargetWorkgroups=" + validTargetWorkgroups);
        }
        return joiner.toString();
    }
}
