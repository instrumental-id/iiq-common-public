package com.identityworksllc.iiq.common.task;

import com.identityworksllc.iiq.common.HybridObjectMatcher;
import com.identityworksllc.iiq.common.TaskUtil;
import com.identityworksllc.iiq.common.iterators.FilteringIterator;
import com.identityworksllc.iiq.common.logging.SLogger;
import sailpoint.api.IncrementalObjectIterator;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.api.Terminator;
import sailpoint.object.*;
import sailpoint.task.AbstractTaskExecutor;
import sailpoint.task.TaskMonitor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * PurgeObjectsTask is a SailPoint IIQ task executor that deletes objects of a specified type from the database.
 *
 * ## Features
 * - Deletes all objects of a given type, or only those matching a filter.
 * - Supports in-memory filtering using HybridObjectMatcher.
 * - Can run in simulation mode to log objects that would be deleted without actually deleting them.
 * - Tracks progress and supports early termination.
 *
 * ## Attributes
 * - `objectType` (String): The SailPoint object type to purge (required).
 * - `deleteAll` (Boolean): If true, deletes all objects of the type. If false, uses a filter (default: false).
 * - `filter` (String): A SailPoint filter string to select objects to delete (required if deleteAll is false).
 * - `inMemoryFilter` (Boolean): If true, applies the filter in memory (default: false).
 * - `simulate` (Boolean): If true, only logs objects that would be deleted (default: true).
 *
 */
public class PurgeObjectsTask extends AbstractTaskExecutor {
    /**
     * Atomic flag to indicate if the task has been terminated early.
     */
    private final AtomicBoolean terminated = new AtomicBoolean(false);

    /**
     * Logger for task events and progress.
     */
    private final SLogger logger = new SLogger(PurgeObjectsTask.class);

    /**
     * Executes the purge task, deleting or simulating deletion of objects.
     *
     * @param context SailPointContext for database operations
     * @param taskSchedule The schedule for this task
     * @param taskResult The result object to record progress and output
     * @param attributes Task attributes (see class-level docs)
     * @throws Exception if required attributes are missing or errors occur
     */
    @Override
    public void execute(SailPointContext context, TaskSchedule taskSchedule, TaskResult taskResult, Attributes<String, Object> attributes) throws Exception {
        TaskMonitor monitor = new TaskMonitor(context, taskResult);
        setMonitor(monitor);

        String objectType = attributes.getString("objectType");
        if (Util.isNullOrEmpty(objectType)) {
            throw new GeneralException("PurgeObjectsTask requires an 'objectType' attribute");
        }

        Class<? extends SailPointObject> objectClass = ObjectUtil.getSailPointClass(objectType);

        boolean deleteAll = attributes.getBoolean("deleteAll", false);
        String filter = attributes.getString("filter");
        if (Util.isNullOrEmpty(filter) && !deleteAll) {
            throw new GeneralException("PurgeObjectsTask requires either a non-empty 'filter' attribute or 'deleteAll' set to true");
        }

        boolean inMemoryFilter = attributes.getBoolean("inMemoryFilter", false);

        boolean simulate = attributes.getBoolean("simulate", true);

        if (logger.isInfoEnabled()) {
            logger.info("Starting purge of objects of type '" + objectType + "'"
                    + (deleteAll ? " (deleting all objects)" : " with filter: " + filter)
                    + (inMemoryFilter ? " using in-memory filtering" : "")
                    + (simulate ? " [DRY RUN MODE]" : ""));
        }

        Filter compiledFilter = null;
        if (Util.isNotNullOrEmpty(filter)) {
            compiledFilter = Filter.compile(filter);
        }

        Iterator<? extends SailPointObject> objectsIterator;

        if (deleteAll) {
            QueryOptions qo = new QueryOptions();
            objectsIterator = new IncrementalObjectIterator<>(context, objectClass, qo);
        } else {
            if (inMemoryFilter) {
                QueryOptions qo = new QueryOptions();
                HybridObjectMatcher matcher = new HybridObjectMatcher(context, compiledFilter);
                Iterator<? extends SailPointObject> tempIterator = new IncrementalObjectIterator<>(context, objectClass, qo);
                objectsIterator = new FilteringIterator<>(tempIterator, (spo) -> {
                    try {
                        return matcher.matches(spo);
                    } catch (GeneralException e) {
                        logger.error("Error applying in-memory filter to object of type " + objectType + ": " + e.getMessage(), e);
                        return false;
                    }
                });
            } else {
                QueryOptions qo = new QueryOptions();
                qo.addFilter(compiledFilter);
                objectsIterator = new IncrementalObjectIterator<>(context, objectClass, qo);
            }
        }

        Terminator terminator = new Terminator(context);

        AtomicInteger deleteCount = new AtomicInteger();
        while (objectsIterator.hasNext()) {
            if (terminated.get()) {
                logger.warn("PurgeObjectsTask terminated early after deleting " + deleteCount.get() + " objects.");
                return;
            }
            monitor.updateProgress("Deleting object " + (deleteCount.get() + 1));
            SailPointObject spo = objectsIterator.next();
            if (!simulate) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Deleting object : " + spo.getId() + " " + spo.getName());
                }
                terminator.deleteObject(objectsIterator.next());
            } else {
                logger.warn("DRY RUN: Would delete object: " + spo.getId() + " " + spo.getName());
            }
            deleteCount.incrementAndGet();
        }

        TaskUtil.withLockedMasterResult(monitor, tr -> {
            taskResult.setAttribute("deleted", deleteCount);
        });
    }

    /**
     * Terminates the purge task early.
     *
     * @return true if termination was successful
     */
    @Override
    public boolean terminate() {
        terminated.set(true);
        return true;
    }
}
