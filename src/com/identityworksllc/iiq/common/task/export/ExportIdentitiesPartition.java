package com.identityworksllc.iiq.common.task.export;

import com.identityworksllc.iiq.common.TaskUtil;
import com.identityworksllc.iiq.common.Utilities;
import com.identityworksllc.iiq.common.query.NamedParameterStatement;
import org.apache.commons.collections4.set.ListOrderedSet;
import org.apache.commons.logging.Log;
import sailpoint.api.IncrementalObjectIterator;
import sailpoint.api.IncrementalProjectionIterator;
import sailpoint.api.SailPointContext;
import sailpoint.object.*;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ExportIdentitiesPartition extends ExportPartition {

    private static final String DELETE_IDENTITY =
            "delete from de_identity where id = :id";

    private static final String DELETE_IDENTITY_ATTRS =
            "delete from de_identity_attr where id = :id";

    private static final String DELETE_IDENTITY_ROLES =
            "delete from de_identity_roles where id = :id";

    public static final int IDENTITY_BATCH_SIZE = 50;

    private static final String INSERT_IDENTITY =
            "insert into de_identity " +
                    "( id, name, type, firstname, lastname, email, manager_id, administrator_id, created, modified, last_refresh, de_timestamp ) " +
                    "values ( :id, :name, :type, :firstname, :lastname, :email, :managerId, :administratorId, :created, :modified, :lastRefresh, :now )";

    private static final String INSERT_IDENTITY_ATTR =
            "insert into de_identity_attr ( id, attribute_name, attribute_value ) values ( :id, :attributeName, :attributeValue )";

    private static final String INSERT_IDENTITY_ROLES =
            "insert into de_identity_roles ( id, role_name, role_type, role_date ) values ( :id, :roleName, :roleType, :assignedOn )";


    /**
     * Exports the identities identified by the filters
     *
     * @param context The context
     * @param connection The connection to the target database
     * @param logger The logger
     * @throws GeneralException if anything fails during execution
     */
    protected void export(SailPointContext context, Connection connection, Log logger) throws GeneralException {
        Set<String> excludeRoles = new HashSet<>();
        if (configuration.containsAttribute("excludeRoles")) {
            List<String> cols = configuration.getStringList("excludeRoles");
            if (cols != null) {
                excludeRoles.addAll(cols);
            }
        }

        Set<String> excludeRoleTypes = new HashSet<>();
        if (configuration.containsAttribute("excludeRoleTypes")) {
            List<String> cols = configuration.getStringList("excludeRoleTypes");
            if (cols != null) {
                excludeRoleTypes.addAll(cols);
            }
        }

        TaskUtil.withLockedPartitionResult(monitor, (partitionResult) -> {
            monitor.updateProgress(partitionResult, "Caching role data", -1);
        });

        Map<String, Bundle> cachedRoles = new HashMap<>();

        Iterator<Bundle> allRoles = context.search(Bundle.class, new QueryOptions());
        while(allRoles.hasNext()) {
            Bundle b = allRoles.next();
            cachedRoles.put(b.getName(), Utilities.detach(context, b));
        }

        Set<String> excludeIdentityCols = new HashSet<>();
        if (configuration.containsAttribute("excludeIdentityColumns")) {
            List<String> cols = configuration.getStringList("excludeIdentityColumns");
            if (cols != null) {
                excludeIdentityCols.addAll(cols);
            }
        }

        Date exportDate = new Date(exportTimestamp);

        QueryOptions qo = new QueryOptions();
        qo.addFilter(Filter.compile(filterString));
        qo.addFilter(Filter.or(Filter.gt("created", new Date(cutoffDate)), Filter.gt("modified", new Date(cutoffDate))));
        qo.setCacheResults(false);
        qo.setTransactionLock(false);

        TaskUtil.withLockedPartitionResult(monitor, (partitionResult) -> {
            monitor.updateProgress(partitionResult, "Executing query", -1);
        });

        int batchCount = 0;
        final AtomicInteger totalCount = new AtomicInteger();

        ObjectConfig identityConfig = Identity.getObjectConfig();

        List<String> fields = new ArrayList<>();
        fields.add("id");               // 0
        fields.add("name");
        fields.add("type");
        fields.add("firstname");
        fields.add("lastname");
        fields.add("displayName");      // 5
        fields.add("email");
        fields.add("manager.id");
        fields.add("administrator.id");
        fields.add("created");
        fields.add("modified");         // 10
        fields.add("lastRefresh");
        fields.add("attributes");
        fields.add("preferences");

        long count = context.countObjects(Identity.class, qo);

        IncrementalProjectionIterator identities = new IncrementalProjectionIterator(context, Identity.class, qo, fields);

        try (NamedParameterStatement deleteAttrs = new NamedParameterStatement(connection, DELETE_IDENTITY_ATTRS); NamedParameterStatement deleteIdentity = new NamedParameterStatement(connection, DELETE_IDENTITY); NamedParameterStatement insertIdentityStatement = new NamedParameterStatement(connection, INSERT_IDENTITY); NamedParameterStatement insertAttributeStatement = new NamedParameterStatement(connection, INSERT_IDENTITY_ATTR); NamedParameterStatement deleteRolesStatement = new NamedParameterStatement(connection, DELETE_IDENTITY_ROLES); NamedParameterStatement insertRolesStatement = new NamedParameterStatement(connection, INSERT_IDENTITY_ROLES)) {
            while (identities.hasNext()) {
                if (isTerminated()) {
                    logger.info("Thread has been terminated; exiting cleanly");
                    break;
                }

                Object[] identity = identities.next();

                String id = Util.otoa(identity[0]);
                String name = Util.otoa(identity[1]);
                String type = Util.otoa(identity[2]);
                String firstname = Util.otoa(identity[3]);
                String lastname = Util.otoa(identity[4]);
                String displayName = Util.otoa(identity[5]);
                String email = Util.otoa(identity[6]);
                String managerId = Util.otoa(identity[7]);
                String administratorId = Util.otoa(identity[8]);

                Date created = (Date) identity[9];
                Date modified = (Date) identity[10];
                Date lastRefresh = (Date) identity[11];

                Attributes<String, Object> attributes = (Attributes<String, Object>) identity[12];
                Map<String, Object> preferences = (Map<String, Object>) identity[13];

                if (logger.isTraceEnabled()) {
                    logger.trace("Exporting Identity " + id + ": " +  displayName);
                }

                deleteIdentity.setString("id", id);
                deleteIdentity.addBatch();

                deleteAttrs.setString("id", id);
                deleteAttrs.addBatch();

                deleteRolesStatement.setString("id", id);
                deleteRolesStatement.addBatch();

                insertIdentityStatement.setString("id", id);
                insertIdentityStatement.setString("name", name);
                insertIdentityStatement.setString("type", type);
                insertIdentityStatement.setString("firstname", firstname);
                insertIdentityStatement.setString("lastname", lastname);
                insertIdentityStatement.setString("email", email);
                if (managerId != null) {
                    insertIdentityStatement.setString("managerId", managerId);
                } else {
                    insertIdentityStatement.setNull("managerId", Types.VARCHAR);
                }
                if (administratorId != null) {
                    insertIdentityStatement.setString("administratorId", administratorId);
                } else {
                    insertIdentityStatement.setNull("administratorId", Types.VARCHAR);
                }

                addCommonDateFields(insertIdentityStatement, exportDate, created, modified, lastRefresh);

                insertIdentityStatement.addBatch();

                if (attributes != null) {
                    for (ObjectAttribute attribute : identityConfig.getObjectAttributes()) {
                        String attrName = attribute.getName();
                        Object value = attributes.get(attrName);
                        if (!Utilities.isNothing(value)) {
                            // TODO - do we want to filter more precisely here? e.g., exclude SSN for certain people?
                            if (!excludeIdentityCols.contains(attrName)) {
                                insertAttributeStatement.setString("id", id);
                                insertAttributeStatement.setString("attributeName", attrName);

                                if (attribute.isMulti()) {
                                    Set<String> uniqueValues = new ListOrderedSet<>();
                                    uniqueValues.addAll(Util.otol(value));
                                    for (String val : uniqueValues) {
                                        insertAttributeStatement.setString("attributeValue", Util.truncate(val, 3996));
                                        insertAttributeStatement.addBatch();
                                    }
                                } else {
                                    insertAttributeStatement.setString("attributeValue", Util.truncate(Util.otoa(value), 3996));
                                    insertAttributeStatement.addBatch();
                                }
                            }
                        }
                    }
                }

                if (preferences != null) {
                    List<RoleAssignment> assignments = (List<RoleAssignment>) preferences.get("roleAssignments");
                    for(RoleAssignment ra : Util.safeIterable(assignments)) {
                        if (!excludeRoles.contains(ra.getRoleName())) {
                            Bundle role = cachedRoles.get(ra.getRoleName());

                            if (role == null) {
                                logger.warn("Identity " + id + " appears to have non-real RoleAssignment to " + ra.getRoleName());
                            } else if (!excludeRoleTypes.contains(role.getType())) {
                                insertRolesStatement.setString("id", id);
                                insertRolesStatement.setString("roleName", role.getName());
                                insertRolesStatement.setString("roleType", role.getType());
                                insertRolesStatement.setDate("assignedOn", ra.getDate());
                                insertRolesStatement.addBatch();
                            }
                        }
                    }

                    List<RoleDetection> detections = (List<RoleDetection>) preferences.get("roleDetections");
                    for(RoleDetection rd : Util.safeIterable(detections)) {
                        if (!excludeRoles.contains(rd.getRoleName())) {
                            Bundle role = cachedRoles.get(rd.getRoleName());

                            if (role == null) {
                                logger.warn("Identity " + id + " appears to have non-real RoleDetection of " + rd.getRoleName());
                            } else if (!excludeRoleTypes.contains(role.getType())) {
                                insertRolesStatement.setString("id", id);
                                insertRolesStatement.setString("roleName", role.getName());
                                insertRolesStatement.setString("roleType", role.getType());
                                insertRolesStatement.setDate("assignedOn", rd.getDate());
                                insertRolesStatement.addBatch();
                            }
                        }
                    }
                }

                if (batchCount++ > IDENTITY_BATCH_SIZE) {
                    deleteAttrs.executeBatch();
                    deleteRolesStatement.executeBatch();
                    deleteIdentity.executeBatch();
                    insertIdentityStatement.executeBatch();
                    insertAttributeStatement.executeBatch();
                    insertRolesStatement.executeBatch();

                    connection.commit();
                    batchCount = 0;
                }

                int currentCount = totalCount.incrementAndGet();
                if ((currentCount % 100) == 0) {
                    TaskUtil.withLockedPartitionResult(monitor, (partitionResult) -> {
                        monitor.updateProgress(partitionResult, "Processed " + currentCount + " of " + count + " identities", -1);
                        partitionResult.setInt("exportedIdentities", currentCount);
                    });
                }
            }

            deleteAttrs.executeBatch();
            deleteRolesStatement.executeBatch();
            deleteIdentity.executeBatch();
            insertIdentityStatement.executeBatch();
            insertAttributeStatement.executeBatch();
            insertRolesStatement.executeBatch();

            connection.commit();

        } catch(SQLException e) {
            throw new GeneralException(e);
        }
    }

}
