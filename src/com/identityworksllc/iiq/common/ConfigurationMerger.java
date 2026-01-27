package com.identityworksllc.iiq.common;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.object.Attributes;
import sailpoint.tools.Util;

import java.util.*;

/**
 * A utility for merging multiple Map objects into a single Attributes-equivalent
 * object. This can be used to stack configurations, e.g., overlaying an Application
 * level configuration onto a global system configuration.
 * 
 * The utility offers two "modes": overlay and merge. An overlay will simply overwrite
 * values from the later inputs with values from the earlier inputs. Merge is more subtle
 * and will combine values as needed.
 * 
 * To overlay maps, invoke {@link #overlayConfigurations(boolean, Map...)}.
 * 
 * To merge maps, invoke {@link #mergeConfigurations(Map...)}.
 */
public class ConfigurationMerger {

    /**
     * The token used to indicate that a value should be removed from the
     * result, rather than merged.
     */
	public static final String NULL_TOKEN = "(null)";

    /**
     * The token used to replace a value in the target Map
     */
    public static final String REPLACE_TOKEN = "_replace";
	/**
	 * Logger used for tracing purposes
	 */
	private static final Log log = LogFactory.getLog(ConfigurationMerger.class);
	
	/**
	 * Merges a series of Maps as non-destructively as possible.
	 * 
	 * See {@link #mergeMaps(Map, Map)} for details on how the merge process works.
	 * 
	 * @param inputs The list of Map sources
	 * @return A new Attributes object containing an overlay of each Map
	 */
	@SafeVarargs
	public static Attributes<String, Object> mergeConfigurations(Map<String, Object>... inputs) {
		Attributes<String, Object> output = new Attributes<>();
		for(Map<String, Object> input : inputs) {
			if (input != null) {
				output = new Attributes<String, Object>(mergeMaps(input, output));
				
				if (log.isTraceEnabled()) {
					log.trace("Merged configuration after round is " + output);
				}
			}
		}
		return output;
	}
	
	
	/**
	 * Merges two values, one of which must be a Collection. The result will be
	 * a merged view of the two values. Any input that is not already a Collection
	 * will be passed through {@link #toList(Object)} for conversion.
	 * 
	 * The output will always be a new non-null List. The existing values will be 
	 * added first, followed by any new values that are not already in the existing
	 * values. Duplicate values will not be added.
	 * 
	 * @param newValues The new value(s) to add to the merged list
	 * @param existingValue The existing value(s) already in the list
	 * @return A new List containing the two values merged
	 */
	public static List<Object> mergeLists(final Object newValues, final Object existingValue) {
		if (!(newValues instanceof Collection || existingValue instanceof Collection)) {
			throw new IllegalArgumentException("At least one argument to mergeLists must contain at least one List");
		}
		
		List<Object> output = new ArrayList<Object>();
		
		List<?> l1 = toList(newValues);
		List<?> l2 = toList(existingValue);
		
		output.addAll(l2);
		
		for(Object o : l1) {
			if (!output.contains(o)) {
				output.add(o);
			}
		}
		
		return output;
	}
	
	/**
	 * Merges the 'new' Map into the 'existing' Map, returning a new Map is that is a merger
	 * of the two. Neither parameter Map will be modified. 
	 * 
	 * If both inputs are null, the output will be empty.
	 * 
	 * If only the existing Map is null, the output will contain only the 'new' values.
	 * 
	 * If only the new Map is null, the output will contain only the 'existing' values.
	 * 
	 * If both Maps are non-null, the following procedure will be applied.
	 * 
	 * If the new Map contains the special key '_replace', it is expected to 
	 * contain a list of field names. The values for those specific keys will
	 * be replaced rather than merged in the output, even if a merge would 
	 * normally be possible.
	 * 
	 * For each key:
	 * 
	 *  - If the new value is null, it will be ignored quietly.
	 *  - If the new value is the string '(null)', with the parens, the corresponding key will be removed from the result.
	 *  - If the existing Map does not already contain that key, the value is inserted as-is.
	 *  - If both new and existing values are Maps, they will be merged via a recursive call to this method
	 *  - If either new or existing values are Collections, they will be merged via {@link #mergeLists(Object, Object)}
	 *  - Otherwise, the new value will overwrite the old value
	 * 
	 * @param newMap The new map to merge into the final product
	 * @param existingMap The existing map, built from this or previous mergers
     * @return The merged Map
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, Object> mergeMaps(final Map<String, Object> newMap, final Map<String, Object> existingMap) {
		if (existingMap == null) {
			if (newMap != null) {
				return new HashMap<>(newMap);
			} else {
				return new HashMap<>();
			}
		}
		Set<String> skipKeys = new HashSet<>();
		if (newMap != null && newMap.containsKey(REPLACE_TOKEN)) {
			skipKeys = new HashSet<>(Util.otol(newMap.get(REPLACE_TOKEN)));
		}
		Map<String, Object> target = new HashMap<String, Object>(existingMap);
		skipKeys.forEach(target::remove);
		if (newMap != null) {
			for(String key : newMap.keySet()) {
				final Object newValue = newMap.get(key);
				final Object existingValue = target.get(key);
				
				if (log.isDebugEnabled()) {
					log.debug("New value for key " + key + " = " + newValue);
				}
				
				if (newValue != null) {
					if (newValue instanceof String && (newValue.equals("") || newValue.equals(NULL_TOKEN))) {
						target.remove(key);
					} else if (existingValue == null) {
						// If the existing value is null, we just insert the new one
						target.put(key, newValue);
					} else if (newValue instanceof Map || existingValue instanceof Map) {
						// Maps require a nested merge operation
						if (existingValue instanceof Map) {
							Map<String, Object> newTarget = new HashMap<String, Object>();
                            newTarget.putAll((Map<String, Object>) existingValue);
                            newTarget = mergeMaps((Map<String, Object>)newValue, newTarget);
							target.put(key, newTarget);
						} else {
							target.put(key, newValue);
						}
					} else if (newValue instanceof Collection || existingValue instanceof Collection) {
						List<Object> newList = mergeLists(newValue, existingValue);
						target.put(key, newList);
					} else {
						// New overrides old
						target.put(key, newValue);
					}
				}
			}
		}
		return target;
	}
	
	/**
	 * Overlays a series of Maps in a destructive way. 
	 * 
	 * For each key in each Map, the value in later Maps overlays the value in earlier maps.
	 * 
	 * If the input is an empty string or the special value '(null)', and ignoreNulls is false, 
	 * the key will be removed from the resulting Map unless a later value in the series of Maps 
	 * inserts a new one. If ignoreNulls is true, these values will be ignored as though they were
	 * a null input.
	 * 
	 * If 'ignoreNulls' is true, null inputs will just be ignored. 
	 * 
	 * Unlike {@link ConfigurationMerger#mergeConfigurations(Map...)}, collection data is not merged. 
	 * Lists in later maps will obliterate the values in earlier maps.
	 * 
	 * @param ignoreNulls If true, null values in any map will simply be ignored, rather than overwriting
	 * @param sources The list of Map sources
	 * @return A new Attributes object containing an overlay of each Map
	 */
	@SafeVarargs
	public static Attributes<String, Object> overlayConfigurations(boolean ignoreNulls, Map<String, Object>... sources) {
		Attributes<String, Object> result = new Attributes<>();
		
		for(Map<String, Object> source : sources) {
			if (source != null) {
				for(final String key : source.keySet()) {
					Object value = source.get(key);
					
					if (value instanceof String && (value.equals("") || value.equals(NULL_TOKEN))) {
						value = null;
					}
					
					if (value == null && ignoreNulls) {
						continue;
					}
					
					if (value == null) {
						result.remove(key);
					} else {
						result.put(key, value);

					}
				}
			}
			
			if (log.isTraceEnabled()) {
				log.trace("Overlaid configuration after round is " + result);
			}

		}
		
		return result;
	}
	
	/**
	 * Converts the input object to a non-null List. If the input is a List, its
	 * values will be added to the result as-is. If the input is a String, it will
	 * be passed through Sailpoint's CSV parser. If the input is anything else, the
	 * list will contain only that item.
	 * 
	 * If the input is null, the output list will be empty.
	 * 
	 * @param value The input object to convert to a list
	 * @return A non-null list containing the converted input
	 */
	@SuppressWarnings("unchecked")
	private static List<Object> toList(final Object value) {
		List<Object> output = new ArrayList<Object>();
		if (value instanceof Collection) {
			output.addAll((Collection<? extends Object>) value);
		} else if (value instanceof String) {
			List<String> listified = Util.stringToList((String)value);
			if (listified != null) {
				output.addAll(listified);
			}
		} else if (value != null) {
			output.add(value);
		}
		return output;
	}

	/**
	 * This class should never be constructed
	 */
	private ConfigurationMerger() {
		throw new UnsupportedOperationException();
	}
}
