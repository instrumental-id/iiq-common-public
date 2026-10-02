package com.example.iiq.task;

import com.identityworksllc.iiq.common.task.AbstractThreadedObjectIteratorTask;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Identity;
import sailpoint.object.Reference;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.List;
import java.util.Map;

/**
 * Sets one Identity attribute to a fixed value. Whoever schedules the task
 * chooses the targets with the standard retrieval arguments (retrievalType,
 * retrievalFilter, retrievalFile, and so on).
 *
 * Patterns shown:
 *
 *  - Letting AbstractThreadedObjectIteratorTask find the targets
 *  - A convertObject() that turns every expected input type into an ID or name,
 *    decaching live objects from the parent context as it goes
 *  - Rejecting retrieval types that make no sense for this task
 *
 * TaskDefinition arguments:
 *
 *  - threads, batchSize, retrievalType, retrieval*: see the threaded tasks doc
 *  - attributeName: required; the Identity attribute to set
 *  - attributeValue: the value to set (blank removes the value)
 *
 * Example TaskDefinition:
 *
 * <pre>
 * &lt;TaskDefinition name="Set Department to Sales" executor="com.example.iiq.task.ThreadedIdentityAttributeTask"
 *     resultAction="Rename" type="Generic" progressMode="String"&gt;
 *   &lt;Attributes&gt;
 *     &lt;Map&gt;
 *       &lt;entry key="threads" value="4"/&gt;
 *       &lt;entry key="batchSize" value="50"/&gt;
 *       &lt;entry key="retrievalType" value="file"/&gt;
 *       &lt;entry key="retrievalFile" value="/opt/iiq/input/sales-people.txt"/&gt;
 *       &lt;entry key="attributeName" value="department"/&gt;
 *       &lt;entry key="attributeValue" value="Sales"/&gt;
 *     &lt;/Map&gt;
 *   &lt;/Attributes&gt;
 * &lt;/TaskDefinition&gt;
 * </pre>
 */
public class ThreadedIdentityAttributeTask extends AbstractThreadedObjectIteratorTask<String> {

    private String attributeName;

    private String attributeValue;

    @Override
    protected void parseArgs(Attributes<String, Object> args) throws Exception {
        // Required: this is where AbstractThreadedObjectIteratorTask reads the
        // retrieval arguments and sets up its retriever.
        super.parseArgs(args);

        // 'connector' produces account ResourceObjects, not identities. (The
        // supportsRetrievalType() hook can't be overridden outside IIQCommon's
        // task package, so check the argument directly.)
        if ("connector".equals(args.getString("retrievalType"))) {
            throw new IllegalArgumentException("This task does not support retrievalType 'connector'");
        }

        this.attributeName = args.getString("attributeName");
        if (Util.isNullOrEmpty(attributeName)) {
            throw new IllegalArgumentException("The 'attributeName' argument is required");
        }
        this.attributeValue = Util.trimnull(args.getString("attributeValue"));
    }

    /**
     * Called on the main thread for every retrieved item, before it is handed
     * to a worker thread. Whatever we return here is what threadExecute receives.
     * Returning null skips the item.
     *
     * We return a String holding an Identity ID or name. (A Reference would also
     * work for IDs, but Reference.resolve() looks objects up by ID only, and the
     * values in a file are often names.)
     */
    @Override
    protected String convertObject(Object input) {
        if (input instanceof Identity) {
            // 'filter' retrieval, or 'sql'/'file' with a class: a live object
            // owned by the parent context. Keep only its ID, then evict the
            // object so the parent context's cache doesn't keep growing.
            Identity identity = (Identity) input;
            String id = identity.getId();
            try {
                context.decache(identity);
            } catch (GeneralException e) {
                throw new IllegalStateException("Unable to decache " + identity.getName(), e);
            }
            return id;
        } else if (input instanceof SailPointObject) {
            throw new IllegalArgumentException("Expected an Identity but got a " + input.getClass().getSimpleName());
        } else if (input instanceof Reference) {
            // A retrieval rule or script that returns References
            return ((Reference) input).getIdOrName();
        } else if (input instanceof List) {
            // 'file' retrieval without a class: each line arrives as a List of tokens
            List<?> tokens = (List<?>) input;
            return tokens.isEmpty() ? null : Util.trimnull(Util.otoa(tokens.get(0)));
        } else if (input instanceof String) {
            // 'provided' or 'sql' retrieval without a class
            return Util.trimnull((String) input);
        }
        throw new IllegalArgumentException("Unexpected input type: " + (input == null ? "null" : input.getClass().getName()));
    }

    @Override
    public Object threadExecute(SailPointContext threadContext, Map<String, Object> parameters, String idOrName) throws GeneralException {
        // getObject() accepts either an ID or a name
        Identity identity = threadContext.getObject(Identity.class, idOrName);
        if (identity == null) {
            // Throwing marks this item as a failure and adds the message to the TaskResult
            throw new GeneralException("No identity found for " + idOrName);
        }

        identity.setAttribute(attributeName, attributeValue);
        threadContext.saveObject(identity);
        return null;
    }
}
