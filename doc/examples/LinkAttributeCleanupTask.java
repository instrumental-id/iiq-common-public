package com.example.iiq.task;

import com.identityworksllc.iiq.common.Ref;
import com.identityworksllc.iiq.common.iterators.TransformingIterator;
import com.identityworksllc.iiq.common.task.AbstractThreadedTask;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.Link;
import sailpoint.object.QueryOptions;
import sailpoint.object.Reference;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Trims leading and trailing whitespace from selected attributes on a set of Links.
 *
 * Patterns shown:
 *
 *  - Draining a projection search into a List before returning it, then flushing
 *    the search iterator. Returning the raw context.search() iterator would fail
 *    with "ResultSet closed" once anything commits.
 *  - Converting each projection row to a Reference with Ref.of(Class, Object[])
 *  - Resolving the Reference with the thread context
 *  - Thread-safe shared counters (AtomicInteger) reported in afterCompletion()
 *
 * TaskDefinition arguments:
 *
 *  - threads, batchSize: see AbstractThreadedTask
 *  - linkFilter: required; an IIQ filter string selecting the Links
 *  - attributes: required; a CSV or list of Link attribute names to trim
 */
public class LinkAttributeCleanupTask extends AbstractThreadedTask<Reference> {

    /**
     * Set once in parseArgs, then only read by threads, so an unmodifiable
     * list is safe to share.
     */
    private List<String> attributeNames;

    private String linkFilter;

    /**
     * Incremented by many threads at once, so it must be an AtomicInteger.
     * A plain 'int' field would lose updates.
     */
    private final AtomicInteger changedCount = new AtomicInteger();

    @Override
    protected void parseArgs(Attributes<String, Object> args) throws Exception {
        super.parseArgs(args);

        this.linkFilter = args.getString("linkFilter");
        List<String> names = args.getStringList("attributes");
        if (Util.isNullOrEmpty(linkFilter) || Util.isEmpty(names)) {
            throw new IllegalArgumentException("The 'linkFilter' and 'attributes' arguments are required");
        }
        this.attributeNames = Collections.unmodifiableList(new ArrayList<>(names));

        // The task object can be reused between runs, so reset any state
        this.changedCount.set(0);
    }

    @Override
    protected Iterator<Reference> getObjectIterator(SailPointContext context, Attributes<String, Object> args) throws GeneralException {
        QueryOptions qo = new QueryOptions();
        qo.addFilter(Filter.compile(linkFilter));

        List<String> props = new ArrayList<>();
        props.add("id");

        Iterator<Object[]> results = context.search(Link.class, qo, props);
        List<Object[]> rows = new ArrayList<>();
        try {
            while (results.hasNext()) {
                rows.add(results.next());
            }
        } finally {
            // Releases the underlying database cursor
            Util.flushIterator(results);
        }

        log.info("Found " + rows.size() + " links to examine");

        // Ref.of(Class, Object[]) expects the ID in the first column
        return new TransformingIterator<>(rows.iterator(), row -> Ref.of(Link.class, row));
    }

    @Override
    public Object threadExecute(SailPointContext threadContext, Map<String, Object> parameters, Reference ref) throws GeneralException {
        Link link = (Link) ref.resolve(threadContext);
        if (link == null) {
            return null;
        }

        boolean changed = false;
        for (String name : attributeNames) {
            Object value = link.getAttribute(name);
            if (value instanceof String) {
                String trimmed = ((String) value).trim();
                if (!trimmed.equals(value)) {
                    link.setAttribute(name, trimmed);
                    changed = true;
                }
            }
        }

        if (changed) {
            threadContext.saveObject(link);
            changedCount.incrementAndGet();
        }
        return null;
    }

    /**
     * Runs once on the main thread after every worker thread has stopped,
     * so all counters are final here.
     */
    @Override
    protected void afterCompletion(SailPointContext context) {
        taskResult.setAttribute("changedLinks", changedCount.get());
    }
}
