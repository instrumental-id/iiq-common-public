package com.identityworksllc.iiq.common.task.export;

import com.identityworksllc.iiq.common.Metered;
import com.identityworksllc.iiq.common.TaskUtil;
import com.identityworksllc.iiq.common.Utilities;
import com.identityworksllc.iiq.common.query.NamedParameterStatement;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.IncrementalObjectIterator;
import sailpoint.api.IncrementalProjectionIterator;
import sailpoint.api.Meter;
import sailpoint.api.SailPointContext;
import sailpoint.object.*;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class ExportLinksPartition extends ExportPartition {

    protected static final String ATTRIBUTE_VALUE_FIELD = "attributeValue";

    public static final String DELETE_LINK =
            "delete from de_link where id = :id";

    public static final String DELETE_LINK_ATTRS =
            "delete from de_link_attr where id = :id";

    /**
     * The application name used to specify fields we do not want to export on EVERY type of account
     */
    protected static final String GLOBAL_SETTING = "global";

    private static final String INSERT_LINK =
            "insert into de_link " +
                    "( id, identity_id, application, native_identity, created, modified, last_refresh, de_timestamp ) " +
                    "values ( :id, :identityId, :application, :nativeIdentity, :created, :modified, :lastRefresh, :now )";

    private static final String INSERT_LINK_ATTR =
            "insert into de_link_attr ( id, attribute_name, attribute_value ) values ( :id, :attributeName, :attributeValue )";

    private static final String METER_FETCH = "ExportLinkPartition.fetch";
    private static final String METER_LINK = "ExportLinkPartition.link";
    private static final String METER_STORE = "ExportLinkPartition.store";

    private static final String REGEX_PREFIX = "regex:";

    private final Log logger;

    public ExportLinksPartition() {
        this.logger = LogFactory.getLog(ExportLinksPartition.class);
    }

    /**
     * Exports the identified Link objects to the export table
     * @param context The context
     * @param connection The connection to the target database
     * @param _logger The logger attached to the {@link com.identityworksllc.iiq.common.threads.SailPointWorker}
     * @throws GeneralException if there are any failures
     */
    @Override
    protected void export(SailPointContext context, Connection connection, Log _logger) throws GeneralException {
        Integer linkBatchSize = configuration.getInteger("linkBatchSize");
        if (linkBatchSize == null || linkBatchSize < 1) {
            linkBatchSize = getBatchSize();
        }

        logger.info("Partition batch size is " + getBatchSize());

        // Mapped from Application name to a set of column names
        Map<String, Set<String>> excludedByApplication = getExcludedColumnsByApplication(context);

        Date exportDate = new Date(exportTimestamp);

        QueryOptions qo = new QueryOptions();
        qo.addFilter(Filter.compile(filterString));
        if (Util.isNotNullOrEmpty(filterString2)) {
            qo.addFilter(Filter.compile(filterString2));
        }
        qo.addFilter(Filter.or(Filter.gt("created", new Date(cutoffDate)), Filter.gt("modified", new Date(cutoffDate))));
        qo.setCacheResults(false);
        qo.setTransactionLock(false);

        TaskUtil.withLockedPartitionResult(monitor, (partitionResult) -> {
            monitor.updateProgress(partitionResult, "Executing query", -1);
        });

        List<String> projectionFields = new ArrayList<>();
        projectionFields.add("id");                 // 0
        projectionFields.add("nativeIdentity");
        projectionFields.add("application.name");
        projectionFields.add("identity.id");
        projectionFields.add("displayName");
        projectionFields.add("created");            // 5
        projectionFields.add("modified");
        projectionFields.add("lastRefresh");
        projectionFields.add("attributes");

        long count = context.countObjects(Link.class, qo);

        IncrementalProjectionIterator links = new IncrementalProjectionIterator(context, Link.class, qo, projectionFields);

        AtomicInteger totalCount = new AtomicInteger();

        Map<String, Schema> schemaMap = new HashMap<>();

        List<String> linksInBatch = new ArrayList<>();

        ObjectConfig linkConfig = Link.getObjectConfig();

        try (NamedParameterStatement deleteAttrs = new NamedParameterStatement(connection, DELETE_LINK_ATTRS); NamedParameterStatement deleteLink = new NamedParameterStatement(connection, DELETE_LINK); NamedParameterStatement insertLink = new NamedParameterStatement(connection, INSERT_LINK); NamedParameterStatement insertAttribute = new NamedParameterStatement(connection, INSERT_LINK_ATTR)) {
            int batchCount = 0;

            while (links.hasNext()) {
                if (isTerminated()) {
                    logger.info("Thread has been terminated; exiting cleanly");
                    break;
                }
                Meter.enterByName(METER_LINK);
                try {
                    Meter.enterByName(METER_FETCH);
                    Object[] link = links.next();
                    Meter.exitByName(METER_FETCH);

                    String linkId = Util.otoa(link[0]);
                    String nativeIdentity = Util.otoa(link[1]);
                    String applicationName = Util.otoa(link[2]);
                    String identityId = Util.otoa(link[3]);
                    String displayName = Util.otoa(link[4]);

                    Date created = (Date) link[5];
                    Date modified = (Date) link[6];
                    Date lastRefresh = (Date) link[7];

                    // Skip Links created after the job began; they'll be captured on the next run
                    if (created != null && created.after(exportDate)) {
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    Attributes<String, Object> attributes = (Attributes<String, Object>) link[8];

                    if (logger.isTraceEnabled()) {
                        logger.trace("Exporting Link " + linkId + ": " + applicationName + " " + nativeIdentity);
                    }

                    if (isDeleteEnabled()) {
                        deleteLink.setString("id", linkId);
                        deleteLink.addBatch();

                        deleteAttrs.setString("id", linkId);
                        deleteAttrs.addBatch();
                    }

                    insertLink.setString("id", linkId);
                    if (identityId != null) {
                        insertLink.setString("identityId", identityId);
                    } else {
                        logger.warn("Link with ID " + linkId + " is orphaned and does not have an Identity");
                        continue;
                    }
                    insertLink.setString("application", applicationName);
                    insertLink.setString("nativeIdentity", nativeIdentity);

                    addCommonDateFields(insertLink, exportDate, created, modified, lastRefresh);

                    insertLink.addBatch();

                    linksInBatch.add(applicationName + ": " + nativeIdentity);

                    if (!schemaMap.containsKey(applicationName)) {
                        Application application = context.getObjectByName(Application.class, applicationName);
                        schemaMap.put(applicationName, application.getAccountSchema());
                    }

                    Schema schema = schemaMap.get(applicationName);

                    Set<String> excludedColumns = excludedByApplication.get(applicationName);

                    for (ObjectAttribute attribute : linkConfig.getObjectAttributes()) {
                        if (attribute.isSystem() || attribute.isStandard()) {
                            continue;
                        }

                        String attrName = attribute.getName();

                        if (excludedColumns != null) {
                            boolean excluded = excludedColumns.contains(attrName);
                            if (excluded) {
                                continue;
                            }
                        }

                        Object value = attributes.get(attrName);
                        if (!Utilities.isNothing(value)) {
                            insertAttribute.setString("id", linkId);
                            insertAttribute.setString("attributeName", attrName);

                            if (attribute.isMulti()) {
                                for (String val : Util.otol(value)) {
                                    insertAttribute.setString(ATTRIBUTE_VALUE_FIELD, Util.truncate(val, 3996));
                                    insertAttribute.addBatch();
                                }
                            } else {
                                insertAttribute.setString(ATTRIBUTE_VALUE_FIELD, Util.truncate(Util.otoa(value), 3996));
                                insertAttribute.addBatch();
                            }
                        }
                    }

                    for (AttributeDefinition attribute : schema.getAttributes()) {
                        String attrName = attribute.getName();

                        if (excludedColumns != null) {
                            boolean excluded = excludedColumns.contains(attrName);
                            if (excluded) {
                                continue;
                            }
                        }

                        Object value = attributes.get(attrName);
                        if (!Utilities.isNothing(value)) {
                            insertAttribute.setString("id", linkId);
                            insertAttribute.setString("attributeName", attrName);

                            if (attribute.isMulti()) {
                                for (String val : Util.otol(value)) {
                                    insertAttribute.setString(ATTRIBUTE_VALUE_FIELD, Utilities.truncateStringToBytes(val, 4000, StandardCharsets.UTF_8));
                                    insertAttribute.addBatch();
                                }
                            } else {
                                insertAttribute.setString(ATTRIBUTE_VALUE_FIELD, Utilities.truncateStringToBytes(Util.otoa(value), 4000, StandardCharsets.UTF_8));
                                insertAttribute.addBatch();
                            }
                        }
                    }

                    if (batchCount++ > linkBatchSize) {
                        Meter.enterByName(METER_STORE);
                        try {
                            if (isDeleteEnabled()) {
                                deleteLink.executeBatch();
                                deleteAttrs.executeBatch();
                            }
                            insertLink.executeBatch();
                            insertAttribute.executeBatch();

                            connection.commit();
                        } catch(SQLException e) {
                            logger.error("Caught an error committing a batch containing these accounts: " + linksInBatch, e);
                            throw e;
                        } finally {
                            linksInBatch.clear();
                            Meter.exitByName(METER_STORE);
                        }
                        batchCount = 0;
                    }

                    int currentCount = totalCount.incrementAndGet();
                    if ((currentCount % 100) == 0) {
                        TaskUtil.withLockedPartitionResult(monitor, (partitionResult) -> {
                            monitor.updateProgress(partitionResult, "Processed " + currentCount + " of " + count + " links", -1);
                            partitionResult.setInt("exportedLinks", currentCount);
                        });
                    }
                } finally{
                    Meter.exitByName(METER_LINK);
                }
            }

            try {
                deleteLink.executeBatch();
                deleteAttrs.executeBatch();
                insertLink.executeBatch();
                insertAttribute.executeBatch();

                connection.commit();

                int currentCount = totalCount.get();
                TaskUtil.withLockedPartitionResult(monitor, (partitionResult) -> {
                    monitor.updateProgress(partitionResult, "Processed " + currentCount + " of " + count + " links", -1);
                    partitionResult.setInt("exportedLinks", currentCount);
                });
            } catch(SQLException e) {
                logger.error("Caught an error committing a batch containing these accounts: " + linksInBatch, e);
                throw e;
            }
        } catch(SQLException e) {
            throw new GeneralException(e);
        }
    }

    /**
     * If the given timestamp is positive, a Date representing that timestamp
     * will be returned. Otherwise, null will be returned.
     *
     * @param timestamp The timestamp
     * @return The date, or null
     */
    private Date toDate(long timestamp) {
        if (timestamp > 0) {
            return new Date(timestamp);
        } else {
            return null;
        }
    }

    /**
     * Builds the map of excluded attributes by application. This can be mapped as a global
     * list, as regular expressions on the name, as the name itself, or as the connector type.
     *
     * @param context The Sailpoint context for querying the DB
     * @return The resulting set of exclusions
     * @throws GeneralException if any failures occur reading the Application objects
     */
    private Map<String, Set<String>> getExcludedColumnsByApplication(SailPointContext context) throws GeneralException {
        Map<String, Set<String>> excludeLinkColumns = new HashMap<>();

        if (configuration.containsAttribute("excludeLinkColumns")) {
            Object config = configuration.get("excludeLinkColumns");
            if (config instanceof Map) {
                Map<String, Object> mapConfig = (Map<String, Object>) config;

                List<Application> allApplications = context.getObjects(Application.class);
                for(Application a : allApplications) {
                    Set<String> mergedExclude = getMergedExcludeSet(a, mapConfig);
                    if (!mergedExclude.isEmpty()) {
                        excludeLinkColumns.put(a.getName(), mergedExclude);
                    }
                }
            } else {
                throw new GeneralException("Invalid configuration: excludeLinkColumns must be an instance of a Map");
            }
        }

        return excludeLinkColumns;
    }

    /**
     * Merges together the various exclusion lists that apply to this application.
     *
     * @param application The application to find exclusion lists for
     * @param excludeLinkColumns The resulting merged set of exclusions
     * @return if any failures occur
     */
    private Set<String> getMergedExcludeSet(Application application, Map<String, Object> excludeLinkColumns) {
        List<String> globalSet = Util.otol(excludeLinkColumns.get(GLOBAL_SETTING));
        List<String> typeSpecific = Util.otol(excludeLinkColumns.get("connector:" + application.getType()));


        Set<String> merged = new HashSet<>();
        if (globalSet != null) {
            merged.addAll(globalSet);
        }

        if (typeSpecific != null) {
            merged.addAll(typeSpecific);
        }

        String applicationName = application.getName();

        for(String key : excludeLinkColumns.keySet()) {
            List<String> colsForKey = Util.otol(excludeLinkColumns.get(key));

            if (key.startsWith(REGEX_PREFIX)) {
                String expression = key.substring(REGEX_PREFIX.length());
                if (Util.isNotNullOrEmpty(expression) && applicationName.matches(expression)) {
                    merged.addAll(colsForKey);
                }
            } else if (applicationName.equalsIgnoreCase(key)) {
                merged.addAll(colsForKey);
            }
        }

        return merged;
    }
}
