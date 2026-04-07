package com.identityworksllc.iiq.common.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.identityworksllc.iiq.common.HybridObjectMatcher;
import com.identityworksllc.iiq.common.TaskUtil;
import com.identityworksllc.iiq.common.Utilities;
import com.identityworksllc.iiq.common.logging.SLogger;
import sailpoint.api.IncrementalObjectIterator;
import sailpoint.api.SailPointContext;
import sailpoint.object.*;
import sailpoint.task.AbstractTaskExecutor;
import sailpoint.task.TaskMonitor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Exports audit events in a standard format to a specified Logger. This logger
 * can be configured to write to a file, send to a SIEM, or however the user wants to consume
 * the data. The task supports filtering events based on event attributes, as well as attributes
 * of the source and target identities. Additional filtering can be done via an optional script
 * or rule.
 *
 * The task keeps track of the last start time using a NamedTimestamp object, and will only export
 * events that were created after the last start time to avoid duplicates.
 */
public class AuditEventExportTask extends AbstractTaskExecutor {
    /**
     * The output key for the number of events exported
     */
    public static final String OUTPUT_EXPORTED_EVENTS = "exportedEvents";

    /**
     * The output key for the number of events skipped due to filtering
     */
    public static final String OUTPUT_SKIPPED_EVENTS = "skippedEvents";

    /**
     * The output key for the total number of events processed (including skipped and exported)
     */
    public static final String OUTPUT_TOTAL_EVENTS = "totalEvents";
    /**
     * Logger for this class, for internal logging events. This is NOT the same logger
     * used to export Audit Events.
     */
    private static final SLogger log = new SLogger(AuditEventExportTask.class);

    /**
     * Helper method to create a map of identity data based on the specified fields. This is used
     * to create the sourceIdentity and targetIdentity maps in the exported event data.
     *
     * @param sourceIdentity the identity to extract data from
     * @param identityFields the list of identity attribute names to include in the map
     * @return a map containing the identity's id, displayName, and specified attributes
     */
    private static Map<String, Object> createIdentityDataMap(Identity sourceIdentity, List<String> identityFields) {
        Map<String, Object> identityData = new HashMap<>();
        identityData.put("id", sourceIdentity.getId());
        identityData.put("displayName", sourceIdentity.getDisplayName());
        for (String field : Util.safeIterable(identityFields)) {
            var value = Util.otoa(sourceIdentity.getAttribute(field));
            if (value != null) {
                identityData.put(field, value);
            }
        }
        return identityData;
    }

    /**
     * Helper method to put a key-value pair into a map if the value is not null. This is used to
     * build the eventData map for each exported event.
     *
     * @param map the map to put the key-value pair into
     * @param key the key to use for the map entry
     * @param value the value to put in the map; if null, the key-value pair will not be added to the map
     */
    private static void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    /**
     * Indicates that the task has been terminated
     */
    private final AtomicBoolean terminated;

    /**
     * Constructor invoked by IIQ
     */
    public AuditEventExportTask() {
        this.terminated = new AtomicBoolean();
    }

    /**
     * The main task execution method invoked by IIQ. This method retrieves audit events based on the
     * specified filters, applies in-memory filtering and optional script/rule-based filtering,
     * and exports the resulting events.
     *
     * @param context the IIQ context
     * @param taskSchedule the task schedule
     * @param taskResult the task result
     * @param attributes the task attributes, which can include
     * @throws Exception if anything goes wrong during execution
     */
    @Override
    public void execute(SailPointContext context, TaskSchedule taskSchedule, TaskResult taskResult, Attributes<String, Object> attributes) throws Exception {
        var timestampName = Util.otoa(attributes.get("timestampName"));
        if (Util.isNullOrEmpty(timestampName)) {
            timestampName = this.getClass().getSimpleName();
        }

        long previousRunTime = 0L;
        NamedTimestamp timestamp = context.getObjectByName(NamedTimestamp.class, timestampName);
        if (timestamp != null) {
            previousRunTime = timestamp.getTimestamp().getTime();
            log.debug("Previous run time for timestamp {0} was {1} ({2})", timestampName, timestamp.getTimestamp(), previousRunTime);
        }

        var monitor = new TaskMonitor(context, taskResult);
        setMonitor(monitor);

        var loggerName = Util.otoa(attributes.get("loggerName"));
        var identityFields = Util.otol(attributes.get("identityFields"));
        var commonFilterString = Util.otoa(attributes.get("commonFilter"));
        var dateFormatString = Util.otoa(attributes.get("dateFormat"));
        var filterScript = Utilities.getAsScript(attributes.get("filterScript"));
        var filterRuleName = Util.otoa(attributes.get("filterRuleName"));
        if (Util.isNullOrEmpty(dateFormatString)) {
            dateFormatString = "yyyy-MM-dd'T'HH:mm:ssZ";
        }

        var dateFormatter = new SimpleDateFormat(dateFormatString);

        var outputLog = Util.isNullOrEmpty(loggerName) ? log : new SLogger(loggerName);

        Rule filterRule = null;
        if (Util.isNotNullOrEmpty(filterRuleName)) {
            filterRule = context.getObjectByName(Rule.class, filterRuleName);
            if (filterRule == null) {
                throw new GeneralException("Filter rule not found: " + filterRuleName);
            }
        }

        // Input can be in the form of Filter 1, 2, 3, 4, 5, etc, if enterd via the UI
        // Or a list if entered via XML file. Handle both cases.
        List<String> finalFilters = new ArrayList<>();
        List<String> filtersAttrList = Util.otol(attributes.get("filters"));
        if (filtersAttrList != null) {
            finalFilters.addAll(filtersAttrList);
        } else {
            for (int i = 1; i <= 20; i++) {
                String filter = Util.otoa(attributes.get("filter " + i));
                if (Util.isNotNullOrEmpty(filter)) {
                    finalFilters.add(filter);
                }
            }
        }

        if (finalFilters.isEmpty()) {
            throw new GeneralException("At least one filter must be specified");
        }

        final Date start = new Date();

        final List<Filter> filters = new ArrayList<>();
        for (String filterString : finalFilters) {
            var filter = Filter.compile(filterString);
            var datedFilter = Filter.and(
                    filter,
                    Filter.gt("created", new Date(previousRunTime)),
                    // Events may be generated continuously during this run.
                    // We also need to filter the max time to avoid duplicates on the next run.
                    Filter.lt("created", start)
            );
            filters.add(datedFilter);
        }

        final Filter inMemoryFilter = Util.isNotNullOrEmpty(commonFilterString) ? Filter.compile(commonFilterString) : null;

        final AtomicInteger totalEvents = new AtomicInteger();
        final AtomicInteger skippedEvents = new AtomicInteger();
        final AtomicInteger exportedEvents = new AtomicInteger();
        final ObjectMapper mapper = new ObjectMapper();

        for(final Filter filter : filters) {
            if (this.terminated.get()) {
                log.warn("Task was terminated, stopping processing");
                break;
            }

            log.info("Executing audit event filter: {0}", filter.getExpression(true));
            TaskUtil.withLockedMasterResult(monitor, (tr) -> {
                tr.addMessage(Message.info("Executing audit event filter: " + filter.getExpression(true)));
            });
            var qo = new QueryOptions();
            qo.addFilter(filter);
            IncrementalObjectIterator<AuditEvent> iterator = new IncrementalObjectIterator<>(context, AuditEvent.class, qo);
            log.debug("Filter found {0} events", iterator.getCount());
            while (iterator.hasNext()) {
                if (this.terminated.get()) {
                    log.warn("Task was terminated, stopping processing");
                    break;
                }

                var totalCount = totalEvents.incrementAndGet();


                if (totalCount % 200 == 0) {
                    log.debug("Processed {0} events so far (exported {1}, skipped {2})", totalCount, exportedEvents.get(), skippedEvents.get());
                    TaskUtil.withLockedMasterResult(monitor, (tr) -> {
                        tr.setInt(OUTPUT_TOTAL_EVENTS, totalEvents.get());
                        tr.setInt(OUTPUT_SKIPPED_EVENTS, skippedEvents.get());
                        tr.setInt(OUTPUT_EXPORTED_EVENTS, exportedEvents.get());
                    });
                }

                final AuditEvent ae = iterator.next();
                try {

                    if (inMemoryFilter != null) {
                        HybridObjectMatcher matcher = new HybridObjectMatcher(context, inMemoryFilter);
                        if (!matcher.matches(ae)) {
                            log.debug("Skipping event {0} based on in-memory filter", ae.getId());
                            skippedEvents.incrementAndGet();
                            continue;
                        }
                    }

                    var source = ae.getSource();
                    Identity sourceIdentity = null;
                    if (Util.isNotNullOrEmpty(source)) {
                        sourceIdentity = context.getObjectByName(Identity.class, source);
                    }

                    Identity targetIdentity = null;
                    var target = ae.getTarget();
                    if (Util.isNotNullOrEmpty(target)) {
                        targetIdentity = context.getObjectByName(Identity.class, target);
                    }

                    boolean likelySecret = false;
                    var attributeName = ae.getAttributeName();
                    if (Util.isNotNullOrEmpty(attributeName)) {
                        String lowerAttrName = attributeName.toLowerCase(Locale.ROOT);
                        for(String token : Utilities.LIKELY_PASSWORD_TOKENS) {
                            if (lowerAttrName.contains(token) && !lowerAttrName.contains("xpir")) {
                                likelySecret = true;
                                break;
                            }
                        }
                    }

                    final Map<String, Object> eventData = new HashMap<>();
                    putIfNotNull(eventData, "id", ae.getId());
                    putIfNotNull(eventData, "timestamp", dateFormatter.format(ae.getCreated()));
                    putIfNotNull(eventData, "timestampMillis", ae.getCreated().getTime());
                    putIfNotNull(eventData, "action", ae.getAction());
                    putIfNotNull(eventData, "application", ae.getApplication());
                    putIfNotNull(eventData, "accountName", ae.getAccountName());
                    putIfNotNull(eventData, "attributeName", ae.getAttributeName());
                    putIfNotNull(eventData, "attributeValue", likelySecret ? Utilities.MASKED_SECRET : ae.getAttributeValue());
                    putIfNotNull(eventData, "serverHost", ae.getServerHost());
                    putIfNotNull(eventData, "clientHost", ae.getClientHost());
                    putIfNotNull(eventData, "string1", ae.getString1());
                    putIfNotNull(eventData, "string2", ae.getString2());
                    putIfNotNull(eventData, "string3", ae.getString3());
                    putIfNotNull(eventData, "string4", ae.getString4());
                    putIfNotNull(eventData, "source", ae.getSource());
                    putIfNotNull(eventData, "target", ae.getTarget());

                    // TODO: Do we need to filter these attributes at all?
                    var eventAttributes = ae.getAttributes();
                    if (eventAttributes != null) {
                        // Deep-copy the attributes object so that we can safely modify it without
                        // affecting the original object. IIQ likes to sometimes auto-save
                        // objects in memory, and we want AuditEvents to be static.
                        XMLObjectFactory factory = XMLObjectFactory.getInstance();
                        var xml = factory.toXml(eventAttributes);
                        @SuppressWarnings("unchecked")
                        Attributes<String, Object> copy = (Attributes<String, Object>) factory.parseXml(context, xml, false);
                        if (copy != null) {
                            Utilities.heuristicMaskSecretAttributes(copy);
                            eventData.put("attributes", copy);
                        } else {
                            eventData.put("attributes", eventAttributes);
                        }
                    }

                    if (sourceIdentity != null) {
                        Map<String, Object> sourceData = createIdentityDataMap(sourceIdentity, identityFields);
                        putIfNotNull(eventData, "sourceIdentity", sourceData);
                    }
                    if (targetIdentity != null) {
                        Map<String, Object> targetData = createIdentityDataMap(targetIdentity, identityFields);
                        putIfNotNull(eventData, "targetIdentity", targetData);
                    }

                    if (filterScript != null) {
                        Map<String, Object> scriptBindings = new HashMap<>();
                        scriptBindings.put("event", ae);
                        scriptBindings.put("sourceIdentity", sourceIdentity);
                        scriptBindings.put("targetIdentity", targetIdentity);
                        scriptBindings.put("eventData", eventData);

                        Boolean includeEvent = (Boolean) context.runScript(filterScript, scriptBindings);
                        if (includeEvent == null || !includeEvent) {
                            log.debug("Skipping event {0} based on filter script", ae.getId());
                            skippedEvents.incrementAndGet();
                            continue;
                        }
                    }

                    if (filterRule != null) {
                        Map<String, Object> ruleBindings = new HashMap<>();
                        ruleBindings.put("event", ae);
                        ruleBindings.put("sourceIdentity", sourceIdentity);
                        ruleBindings.put("targetIdentity", targetIdentity);
                        ruleBindings.put("eventData", eventData);

                        Boolean includeEvent = (Boolean) context.runRule(filterRule, ruleBindings);
                        if (includeEvent == null || !includeEvent) {
                            log.debug("Skipping event {0} based on filter rule {1}", ae.getId(), filterRuleName);
                            skippedEvents.incrementAndGet();
                            continue;
                        }
                    }

                    var count = exportedEvents.incrementAndGet();
                    monitor.updateProgress("Exporting " + count + ": " + ae.getId());

                    String json = mapper.writeValueAsString(eventData);
                    outputLog.info(json);

                    // Clean up after ourselves
                    if (sourceIdentity != null) {
                        context.decache(sourceIdentity);
                    }
                    if (targetIdentity != null) {
                        context.decache(targetIdentity);
                    }
                } catch(GeneralException e) {
                    // TODO: do we want some sort of fallback (DB table?) so that these are output next time?
                    log.error("Error processing audit event " + ae.getId(), e);
                }
            }
        }

        // If we've gotten this far without an exception, then we can update the Timestamp
        // object to the current time. If the task fails or is aborted, we will have duplicate
        // events on the next run but we won't miss anything.
        if (!this.terminated.get()) {
            if (timestamp == null) {
                timestamp = new NamedTimestamp();
                timestamp.setName(timestampName);
            }
            log.info("Updating last run time to {0} ({1})", start, start.getTime());
            timestamp.setTimestamp(start);
            context.saveObject(timestamp);
            context.commitTransaction();
        }

        TaskUtil.withLockedMasterResult(monitor, (tr) -> {
            tr.setAttribute(OUTPUT_TOTAL_EVENTS, totalEvents.get());
            tr.setAttribute(OUTPUT_SKIPPED_EVENTS, skippedEvents.get());
            tr.setAttribute(OUTPUT_EXPORTED_EVENTS, exportedEvents.get());
        });
    }

    /**
     * Invoked by IIQ whenever the task is terminated by the user
     * @return true if the task was successfully marked as terminated, false otherwise
     */
    @Override
    public boolean terminate() {
        this.terminated.set(true);
        return true;
    }
}
