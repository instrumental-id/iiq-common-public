package com.example.iiq.task;

import com.identityworksllc.iiq.common.task.AbstractThreadedTask;
import sailpoint.api.Identitizer;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.Iterator;
import java.util.Map;

/**
 * A lightweight, multi-threaded identity refresh that only promotes identity
 * attributes. It is much faster than a full Identity Refresh when that is all
 * you need.
 *
 * Patterns shown:
 *
 *  - Using batchSize so each worker processes many items with one private context
 *  - beforeBatch() to build an expensive helper once per batch rather than once
 *    per item. An Identitizer is tied to the context it was built with, so each
 *    batch (each private context) needs its own.
 *  - A ThreadLocal to hand that helper from beforeBatch() to threadExecute()
 *  - afterBatch() for per-batch cleanup
 *
 * Run with a batchSize such as 100. With batchSize 0, every "batch" is one item,
 * and a new Identitizer is built for every identity.
 *
 * TaskDefinition arguments:
 *
 *  - threads, batchSize: see AbstractThreadedTask
 *  - identityFilter: optional; an IIQ filter string (default: all non-workgroup identities)
 */
public class ThreadedAttributeRefreshTask extends AbstractThreadedTask<String> {

    private String identityFilter;

    /**
     * Each worker thread gets its own Identitizer. Threads never share one,
     * so the Identitizer itself does not need to be thread-safe.
     */
    private final ThreadLocal<Identitizer> identitizer = new ThreadLocal<>();

    @Override
    protected void parseArgs(Attributes<String, Object> args) throws Exception {
        super.parseArgs(args);
        this.identityFilter = args.getString("identityFilter");
    }

    @Override
    protected Iterator<String> getObjectIterator(SailPointContext context, Attributes<String, Object> args) throws GeneralException {
        QueryOptions qo = new QueryOptions();
        qo.addFilter(Filter.eq("workgroup", false));
        if (Util.isNotNullOrEmpty(identityFilter)) {
            qo.addFilter(Filter.compile(identityFilter));
        }
        return ObjectUtil.getObjectIds(context, Identity.class, qo).iterator();
    }

    /**
     * Runs on the worker thread before each batch, with that batch's private
     * context. If this throws, the whole batch is skipped (and afterBatch is not called).
     */
    @Override
    public void beforeBatch(SailPointContext threadContext) throws GeneralException {
        Attributes<String, Object> refreshOptions = new Attributes<>();
        refreshOptions.put(Identitizer.ARG_PROMOTE_ATTRIBUTES, true);
        refreshOptions.put(Identitizer.ARG_NO_CHECK_PENDING_WORKFLOW, true);

        Identitizer batchIdentitizer = new Identitizer(threadContext, refreshOptions);
        batchIdentitizer.prepare();
        identitizer.set(batchIdentitizer);
    }

    @Override
    public Object threadExecute(SailPointContext threadContext, Map<String, Object> parameters, String identityId) throws GeneralException {
        Identity identity = threadContext.getObjectById(Identity.class, identityId);
        if (identity == null) {
            return null;
        }

        identitizer.get().refresh(identity);
        threadContext.saveObject(identity);
        // The worker commits after this returns
        return null;
    }

    /**
     * Runs on the worker thread after each batch, once every item has been
     * committed. Exceptions thrown here are logged and otherwise ignored.
     */
    @Override
    public void afterBatch(SailPointContext threadContext) throws GeneralException {
        Identitizer batchIdentitizer = identitizer.get();
        // Pool threads are reused, so don't leave this batch's helper behind
        identitizer.remove();
        if (batchIdentitizer != null) {
            batchIdentitizer.cleanup();
        }
        // Everything in this batch is committed, so it's safe to empty the
        // thread context's Hibernate cache
        threadContext.decache();
    }
}
