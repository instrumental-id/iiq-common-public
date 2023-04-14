package com.identityworksllc.iiq.common.minimal.threads;

import com.identityworksllc.iiq.common.minimal.Ref;
import org.apache.commons.logging.Log;
import sailpoint.api.SailPointContext;
import sailpoint.api.Terminator;
import sailpoint.object.Reference;
import sailpoint.object.SailPointObject;
import sailpoint.tools.Util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A serializable worker to delete a single SailPoint object or a list of objects.
 */
public class TerminatorWorker extends SailPointWorker implements Serializable {
    /**
     * Returns a TerminatorWorker for the given SailPointObject
     * @param object The object
     * @return The worker
     */
    public static TerminatorWorker forObject(SailPointObject object) {
        List<Reference> refs = new ArrayList<>();
        refs.add(Ref.of(object));
        return new TerminatorWorker(refs);
    }

    /**
     * Returns a TerminatorWorker for the given list of SailPointObjects
     * @param objects The SailPointObjects
     * @return the worker
     */
    public static TerminatorWorker forObjects(List<? extends SailPointObject> objects) {
        List<Reference> refs = new ArrayList<>();
        for(SailPointObject spo : Util.safeIterable(objects)) {
            refs.add(Ref.of(spo));
        }
        return new TerminatorWorker(refs);
    }

    /**
     * Make references from a class name and list of IDs
     * @param className The class name
     * @param ids The IDs
     * @return A list of references
     */
    private static List<Reference> makeRefs(Class<SailPointObject> className, List<String> ids) {
        List<Reference> refs = new ArrayList<>();
        for(String id : Util.safeIterable(ids)) {
            refs.add(Ref.of(className, id));
        }
        return refs;
    }

    /**
     * The list of objects to terminate, stored as references
     */
    private final List<Reference> objects;

    /**
     * Required by Externalizable; don't use this please
     */
    public TerminatorWorker() {
        this.objects = new ArrayList<>();
    }

    /**
     * Creates a worker to terminate the given SailPointObject by ID
     * @param className The class name of the object to terminate
     * @param id The ID of the object to terminate
     */
    public TerminatorWorker(Class<SailPointObject> className, String id) {
        this(className, Collections.singletonList(id));
    }

    /**
     * Creates a worker to terminate a list of SailPointObjects by ID
     * @param className The class name of the objects to terminate
     * @param ids The list of IDs of the objects to terminate
     */
    public TerminatorWorker(Class<SailPointObject> className, List<String> ids) {
        this(makeRefs(className, ids));
    }

    /**
     * A new worker that will terminate a single object
     * @param singleObject The single object reference to terminate
     */
    public TerminatorWorker(Reference singleObject) {
        this(Collections.singletonList(singleObject));
    }

    /**
     * A new worker that will terminate a list of objects
     * @param objects The list of objects
     */
    public TerminatorWorker(List<Reference> objects) {
        this.objects = new ArrayList<>(objects);
    }

    /**
     * Invokes the Terminator on each object in the list of references.
     *
     * @param context The private context to use for this thread worker
     * @param logger The log attached to this Worker
     * @return Null; can be ignored
     * @throws Exception if any failures occur
     */
    @Override
    public Object execute(SailPointContext context, Log logger) throws Exception {
        Terminator terminator = new Terminator(context);
        terminator.setNoLocking(true);
        for(Reference ref : this.objects) {
            SailPointObject spo = ref.resolve(context);
            if (spo != null) {
                terminator.deleteObject(spo);
            }
        }
        return null;
    }
}
