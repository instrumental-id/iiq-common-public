package com.example.iiq.task;

import com.identityworksllc.iiq.common.task.AbstractThreadedTask;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The simplest useful threaded task: find some Identity IDs, then change each
 * Identity in parallel.
 *
 * Patterns shown:
 *
 *  - Reading TaskDefinition arguments in parseArgs() and storing them in fields
 *  - Reading all IDs into a List up front, so commits can't break the iterator
 *  - Passing String IDs to threads instead of live Identity objects
 *  - Rolling back on failure, since the worker does not roll back for you
 *
 * TaskDefinition arguments:
 *
 *  - threads: number of worker threads (default 1)
 *  - batchSize: items per private context (default 0, meaning one)
 *  - identityFilter: required; an IIQ filter string selecting the identities
 *  - dryRun: optional boolean; if true, log what would change without saving
 */
public class DeactivateIdentitiesTask extends AbstractThreadedTask<String> {

    private String identityFilter;

    private boolean dryRun;

    @Override
    protected void parseArgs(Attributes<String, Object> args) throws Exception {
        // Always call super first. It reads 'threads' and 'batchSize'.
        super.parseArgs(args);

        this.identityFilter = args.getString("identityFilter");
        if (Util.isNullOrEmpty(identityFilter)) {
            throw new IllegalArgumentException("The 'identityFilter' argument is required");
        }
        this.dryRun = args.getBoolean("dryRun");
    }

    /**
     * Runs once, on the main task thread. This is the only place (besides parseArgs)
     * where the parent 'context' should be used.
     */
    @Override
    protected Iterator<String> getObjectIterator(SailPointContext context, Attributes<String, Object> args) throws GeneralException {
        QueryOptions qo = new QueryOptions();
        qo.addFilter(Filter.compile(identityFilter));
        qo.addFilter(Filter.eq("inactive", false));

        // getObjectIds reads the whole result into a List, so nothing is left
        // open on the parent context while the threads are running.
        List<String> ids = ObjectUtil.getObjectIds(context, Identity.class, qo);
        log.info("Found " + ids.size() + " identities to deactivate");
        return ids.iterator();
    }

    /**
     * Runs in parallel on worker threads. Use only 'threadContext' in here.
     */
    @Override
    public Object threadExecute(SailPointContext threadContext, Map<String, Object> parameters, String identityId) throws GeneralException {
        Identity identity = threadContext.getObjectById(Identity.class, identityId);
        if (identity == null) {
            // Deleted since we searched; not an error
            return null;
        }

        if (dryRun) {
            log.info("Dry run: would deactivate " + identity.getName());
            return null;
        }

        try {
            identity.setInactive(true);
            threadContext.saveObject(identity);
            // No commit here: the worker commits after this method returns normally
        } catch (GeneralException | RuntimeException e) {
            // If we don't roll back, the next item's commit would save our
            // half-finished changes.
            threadContext.rollbackTransaction();
            throw e;
        }
        // Don't decache 'identity' here: its changes haven't been committed yet,
        // and evicting it before the worker's commit could discard them.
        return null;
    }
}
