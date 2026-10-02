package com.example.iiq.task;

import com.identityworksllc.iiq.common.task.AbstractThreadedTask;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Builds one output row per Identity in parallel, then writes all rows to a
 * pipe-delimited file when every thread has finished.
 *
 * This is the "gather in threads, write at the end" pattern used by several
 * file-extract tasks. Two details are easy to get wrong:
 *
 *  1. The collection the threads add to must be thread-safe. A plain ArrayList
 *     shared by several threads will randomly lose rows or throw exceptions.
 *  2. Write the file in afterCompletion(), not in a termination handler.
 *     afterCompletion() runs only after every worker thread has stopped.
 *     Termination handlers run immediately if the task is terminated, possibly
 *     while threads are still adding rows.
 *
 * TaskDefinition arguments:
 *
 *  - threads, batchSize: see AbstractThreadedTask
 *  - identityFilter: required; an IIQ filter string selecting the identities
 *  - applicationName: required; the application whose account count is reported
 *  - outputFile: required; the full path of the file to write on the IIQ server
 */
public class ThreadedIdentityReportTask extends AbstractThreadedTask<String> {

    private static final String HEADER = "IDENTITY_NAME|DISPLAY_NAME|EMAIL|ACCOUNT_COUNT";

    private String identityFilter;

    private String applicationName;

    private Path outputFile;

    /**
     * Written by many threads at once, read by the main thread at the end.
     * ConcurrentLinkedQueue is safe for that; so is Collections.synchronizedList().
     */
    private final Queue<String> rows = new ConcurrentLinkedQueue<>();

    @Override
    protected void parseArgs(Attributes<String, Object> args) throws Exception {
        super.parseArgs(args);

        this.identityFilter = args.getString("identityFilter");
        this.applicationName = args.getString("applicationName");
        String fileName = args.getString("outputFile");
        if (Util.isNullOrEmpty(identityFilter) || Util.isNullOrEmpty(applicationName) || Util.isNullOrEmpty(fileName)) {
            throw new IllegalArgumentException("The 'identityFilter', 'applicationName', and 'outputFile' arguments are required");
        }
        this.outputFile = Paths.get(fileName);
        this.rows.clear();
    }

    @Override
    protected Iterator<String> getObjectIterator(SailPointContext context, Attributes<String, Object> args) throws GeneralException {
        QueryOptions qo = new QueryOptions();
        qo.addFilter(Filter.compile(identityFilter));
        return ObjectUtil.getObjectIds(context, Identity.class, qo).iterator();
    }

    @Override
    public Object threadExecute(SailPointContext threadContext, Map<String, Object> parameters, String identityId) throws GeneralException {
        Identity identity = threadContext.getObjectById(Identity.class, identityId);
        if (identity == null) {
            return null;
        }

        QueryOptions qo = new QueryOptions();
        qo.addFilter(Filter.eq("identity.id", identityId));
        qo.addFilter(Filter.eq("application.name", applicationName));
        int accountCount = threadContext.countObjects(Link.class, qo);

        rows.add(String.join("|",
                clean(identity.getName()),
                clean(identity.getDisplayName()),
                clean(identity.getEmail()),
                String.valueOf(accountCount)));

        // This task only reads, so evicting the identity is safe, and it keeps
        // the thread context's cache small across a large batch.
        threadContext.decache(identity);
        return null;
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace("|", " ");
    }

    /**
     * Runs once on the main thread after every worker thread has stopped.
     */
    @Override
    protected void afterCompletion(SailPointContext context) {
        if (terminated.get()) {
            // Don't leave a partial file that looks like a complete one
            log.warn("Task was terminated; not writing " + outputFile);
            return;
        }

        List<String> lines = new ArrayList<>(rows.size() + 1);
        lines.add(HEADER);
        lines.addAll(rows);
        try {
            Files.write(outputFile, lines, StandardCharsets.UTF_8);
            taskResult.setAttribute("rowsWritten", rows.size());
            log.info("Wrote " + rows.size() + " rows to " + outputFile);
        } catch (IOException e) {
            log.error("Unable to write " + outputFile, e);
            taskResult.addException(e);
        }
    }
}
