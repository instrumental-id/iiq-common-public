package com.identityworksllc.iiq.common.minimal.cache;

import com.identityworksllc.iiq.common.minimal.Utilities;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;

import java.lang.reflect.Modifier;

/**
 * A cache generator to automatically pull and cache SailPoint objects
 * @param <T> The cached object type, which must be a concrete subclass of SailPointObject
 */
@SuppressWarnings("unused")
public class SailPointObjectCacheGenerator<T extends SailPointObject> implements CacheGenerator<T> {

	/**
	 * Class logger
	 */
	private static final Log log = LogFactory.getLog(SailPointObjectCacheGenerator.class);

	/**
	 * The object type being generated
	 */
	private final Class<T> objectType;

	/**
	 * Constructs a new Sailpoint cache generator for this object type
	 * @param type The object type
	 * @throws IllegalArgumentException if the input type is not a concrete subclass of SailPointObject
	 */
	public SailPointObjectCacheGenerator(Class<T> type) {
		if (Modifier.isAbstract(type.getModifiers())) {
			throw new IllegalArgumentException("The type passed to " + SailPointObjectCacheGenerator.class.getName() + " must be a concrete type");
		}
		this.objectType = type;
	}

	/**
	 * Gets the value for the given key. The Key can be either a name or an ID. This
	 * method must be invoked in a thread that has a SailPointContext.
	 *
	 * If the key is not a string, or if no such object exists, null will be returned.
	 *
	 * The object will be detached via {@link Utilities#detach(SailPointContext, SailPointObject)}.
	 *
	 * @param key The key for which to retrieve a value
	 * @return The object, or null if there is an issue loading it
	 */
	@SuppressWarnings("unchecked")
	@Override
	public T getValue(Object key) {
		try {
			if (key instanceof String) {
				SailPointContext context = SailPointFactory.getCurrentContext();
				if (context == null) {
					throw new IllegalStateException("This factory method must be invoked in a thread that has a SailPointContext");
				}
				SailPointObject object = context.getObject(objectType, (String)key);
				if (object != null) {
					object.clearPersistentIdentity();
					object = Utilities.detach(context, object);
					return (T) object;
				} else {
					if (log.isWarnEnabled()) {
						log.warn("No such object: type [" + objectType.getName() + "], name or ID [" + key + "]");
					}
				}
			}
		} catch(GeneralException e) {
			log.error("Unable to load SailPointObject for caching", e);
			return null;
		}
		return null;
	}
	
}
