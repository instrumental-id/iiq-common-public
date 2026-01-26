package com.identityworksllc.iiq.common.task;

import com.identityworksllc.iiq.common.Ref;
import com.identityworksllc.iiq.common.Utilities;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.api.Terminator;
import sailpoint.object.*;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;

import java.util.*;

/**
 * A class that can prune a subset of Sailpoint objects in a more granular
 * way. The objects to prune can be supplied by a script or by a search filter.
 * In the case of a filter, objects can be further narrowed by a post-selector
 * script that can arbitrarily reject specific objects.
 *
 * All selectors are defined in a Configuration object.
 *
 * If a filter is used, the {@link TimeTokenizer} class allows for dynamic time
 * based filtering (e.g. older than five days ago).
 */
public class SmartObjectPruner extends AbstractThreadedTask<Reference> {
    /**
     * The allowed list of object types for deletion
     */
    private static final List<String> OBJECT_TYPES = Arrays.asList(
            AuditEvent.class.getSimpleName(),
            Bundle.class.getSimpleName(),
            Identity.class.getSimpleName(),
            IdentityRequest.class.getSimpleName(),
            Link.class.getSimpleName(),
            Request.class.getSimpleName(),
            ProvisioningTransaction.class.getSimpleName(),
            ProvisioningRequest.class.getSimpleName(),
            SyslogEvent.class.getSimpleName(),
            TaskResult.class.getSimpleName(),
            WorkflowCase.class.getSimpleName(),
            WorkItem.class.getSimpleName()
    );

    /**
     * Gets the list of objects to prune
     */
    @Override
    protected Iterator<? extends Reference> getObjectIterator(SailPointContext context, Attributes<String, Object> attributes) throws GeneralException {
        String prunerConfig = "IDW - Smart Pruner Configuration";
        if (Util.isNotNullOrEmpty(attributes.getString("prunerConfigName"))) {
            prunerConfig = attributes.getString("prunerConfigName");
        }
        Configuration smartPrunerConfiguration = context.getObjectByName(Configuration.class, prunerConfig);

        if (smartPrunerConfiguration == null || smartPrunerConfiguration.getAttributes() == null || smartPrunerConfiguration.getAttributes().isEmpty()) {
            taskResult.addMessage(Message.warn("Smart pruner configuration " + prunerConfig + " does not exist or is empty; aborting"));
            return null;
        }

        List<Reference> toDelete = new ArrayList<>();

        for(String objectType : OBJECT_TYPES) {
            if (terminated.get()) {
                break;
            }
            if (!smartPrunerConfiguration.containsKey(objectType)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Class<? extends SailPointObject> spClass = ObjectUtil.getSailPointClass(objectType);

            @SuppressWarnings("unchecked")
            Map<String, Object> objectConfig = (Map<String, Object>) smartPrunerConfiguration.get(objectType);
            if (objectConfig != null && !objectConfig.isEmpty()) {
                String selectorType = Util.otoa(objectConfig.get("type"));
                if (Util.isNullOrEmpty(selectorType)) {
                    throw new IllegalArgumentException("A selector 'type' must be specified for class " + objectType);
                }

                Object selector = objectConfig.get("selector");
                Script postSelectorScript = Utilities.getAsScript(objectConfig.get("postSelectorScript"));
                List<String> props = new ArrayList<>();
                props.add("id");
                if (selectorType.equalsIgnoreCase("all")) {
                    QueryOptions qo = new QueryOptions();
                    Iterator<Object[]> objects = context.search(spClass, qo, props);
                    while(objects.hasNext()) {
                        Object[] result = objects.next();
                        String id = Util.otoa(result[0]);
                        maybeAdd(context, toDelete, spClass, id, postSelectorScript);
                    }
                } else if (selectorType.equalsIgnoreCase("filter")) {
                    String filterString = Util.otoa(selector);
                    if (Util.isNullOrEmpty(filterString)) {
                        throw new IllegalArgumentException("For object type " + objectType + " with selector type filter, the filter string is null or empty");
                    }
                    String modifiedFilterString = TimeTokenizer.parseTimeComponents(taskSchedule, filterString, null);
                    Filter objectFilter = Filter.compile(modifiedFilterString);
                    QueryOptions qo = new QueryOptions();
                    qo.addFilter(objectFilter);
                    Iterator<Object[]> objects = context.search(spClass, qo, props);
                    while(objects.hasNext()) {
                        Object[] result = objects.next();
                        String id = Util.otoa(result[0]);
                        maybeAdd(context, toDelete, spClass, id, postSelectorScript);
                    }
                } else if (selectorType.equalsIgnoreCase("script")) {
                    Script scriptSelector = Utilities.getAsScript(selector);
                    if (scriptSelector != null) {
                        Map<String, Object> params = new HashMap<>();
                        params.put("type", objectType);
                        params.put("typeClass", spClass);
                        params.put("environment", attributes);
                        params.put("configuration", objectConfig);

                        Object output = context.runScript(scriptSelector, params);
                        if (output instanceof List) {
                            List<Object> objectList = (List<Object>)output;
                            for(Object obj : objectList) {
                                if (obj != null) {
                                    if (obj instanceof String) {
                                        toDelete.add(Ref.of(spClass, (String) obj));
                                    } else if (obj instanceof SailPointObject) {
                                        SailPointObject spo = (SailPointObject) obj;
                                        toDelete.add(Ref.of(spo));

                                        context.decache(spo);
                                    } else {
                                        throw new IllegalStateException("Illegal output list element from selector script for object type " + objectType + ": " + obj.getClass().getName());
                                    }
                                }
                            }
                        } else if (output != null) {
                            throw new IllegalStateException("Illegal output type from selector script for object type " + objectType + ": " + output.getClass().getName());
                        }
                    }
                } else {
                    throw new IllegalArgumentException("Invalid selector type: " + selectorType);
                }
            }
        }
        return Util.safeIterable(toDelete).iterator();
    }

    /**
     * Executes the post-selector script if one exists. If the script returns true,
     * adds the item to the list.
     *
     * If no post-selector script is defined, adds the item to the list.
     *
     * @param context The context to use for the script execution
     * @param toDelete The list to which the objects should be added on deletion
     * @param spClass The queried class
     * @param objectId The object ID to check
     * @param postSelectorScript The script, which may be null
     * @throws GeneralException if a script failure occurs
     */
    private void maybeAdd(SailPointContext context, List<Reference> toDelete, Class<? extends SailPointObject> spClass, String objectId, Script postSelectorScript) throws GeneralException {
        if (postSelectorScript != null) {
            SailPointObject spo = context.getObjectById(spClass, objectId);
            Map<String, Object> params = new HashMap<>();
            params.put("object", spo);
            params.put("context", context);
            boolean shouldAdd = Util.otob(context.runScript(postSelectorScript, params));
            if (shouldAdd) {
                toDelete.add(Ref.of(spo.getClass(), spo.getId()));
            }

            context.decache(spo);
        } else {
            toDelete.add(Ref.of(spClass, objectId));
        }
    }

    /**
     * Deletes the input
     * @param threadContext A private IIQ context for the current JVM thread
     * @param parameters A set of default parameters suitable for a Rule or Script. In the default implementation, the object will be in this map as 'object'.
     * @param ref The object to terminate in this thread
     * @return always null
     * @throws GeneralException if a failure occurs deleting the object
     */
    @Override
    public Object threadExecute(SailPointContext threadContext, Map<String, Object> parameters, Reference ref) throws GeneralException {
        SailPointObject spo = ref.resolve(threadContext);
        if (spo != null) {
            if (spo instanceof Identity && ((Identity) spo).isProtected()) {
                log.warn("Filter returned Identity " + ((Identity) spo).getDisplayableName() + " but it is protected; ignoring it");
                return null;
            }
            Terminator terminator = new Terminator(threadContext);
            terminator.deleteObject(spo);
        }
        return null;
    }
}
