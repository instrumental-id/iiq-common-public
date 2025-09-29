package com.identityworksllc.iiq.common;

import com.identityworksllc.iiq.common.annotation.Experimental;
import com.identityworksllc.iiq.common.query.ContextConnectionWrapper;
import sailpoint.api.Identitizer;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;

/**
 * Utilities for handling Identity operations
 */
public class BaseIdentityUtilities extends AbstractBaseUtility {

    public BaseIdentityUtilities(SailPointContext context) {
        super(context);
    }

    /**
     * Gets the default set of refresh options, with or without process-events.
     *
     * The refresh options set to true are:
     *
     *  - provision
     *  - correlateEntitlements
     *  - promoteManagedAttributes
     *  - refreshRoleMetadata
     *  - promoteAttributes
     *  - synchronizeAttributes
     *  - refreshManagerStatus
     *  - noResetNeedsRefresh
     *  - refreshProvisioningRequests
     *  - checkHistory
     *
     * If the provided _shouldProcessEvents_ is true, then _processTriggers_ will also be
     * set to true. This is optional because triggers can prolong a refresh considerably.
     *
     * @param shouldProcessEvents True if we should also process events, false if not
     * @return A new Attributes with the default set of refresh options
     */
    public Attributes<String, Object> getDefaultRefreshOptions(boolean shouldProcessEvents) {
        Attributes<String, Object> args = new Attributes<>();
        args.put(Identitizer.ARG_PROVISION, true);
        args.put(Identitizer.ARG_CORRELATE_ENTITLEMENTS, true);
        args.put(Identitizer.ARG_PROCESS_TRIGGERS, shouldProcessEvents);
        args.put(Identitizer.ARG_PROMOTE_MANAGED_ATTRIBUTES, true);
        args.put(Identitizer.ARG_REFRESH_ROLE_METADATA, true);
        args.put(Identitizer.ARG_PROMOTE_ATTRIBUTES, true);
        args.put(Identitizer.ARG_SYNCHRONIZE_ATTRIBUTES, true);
        args.put(Identitizer.ARG_REFRESH_MANAGER_STATUS, true);
        args.put(Identitizer.ARG_NO_RESET_NEEDS_REFRESH, true);
        args.put(Identitizer.ARG_REFRESH_PROVISIONING_REQUESTS, true);
        args.put(Identitizer.ARG_CHECK_HISTORY, true);
        return args;
    }

    /**
     * Gets an optional property from the Identity object, which itself may be null. If the
     * input Identity is null or the attribute name is null or empty, this will return an empty
     * Optional. If the property does not exist or is null, it will also return an empty Optional.
     *
     * Uses {@link Utilities#getProperty(Object, String, boolean)}, with graceful nulls set to true,
     * to retrieve the property value.
     *
     * @param identity The Identity object to query
     * @param attributeName The path to the property to retrieve, which must not be null or empty
     * @return An Optional containing the attribute value if it exists, or empty if not
     * @param <U> The type of the attribute value, which will be inferred from the Identity's attribute type
     * @throws GeneralException if any IIQ failure occurs, such as if the attribute cannot be retrieved
     */
    public <U> Optional<U> getOptionalProperty(Identity identity, String attributeName) throws GeneralException {
        if (identity == null || attributeName == null || attributeName.isEmpty()) {
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        U value = (U) Utilities.getProperty(identity, attributeName, true);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    /**
     * Returns true if the user has at least one of the detected role
     * @param identity The identity to check
     * @param roleName The role name to look for
     * @return true if the user has at least one detected role of this name
     */
    public boolean hasDetectedRole(Identity identity, String roleName) {
        long count = Utilities.safeStream(identity.getRoleDetections()).filter(rd -> Util.nullSafeEq(rd.getRoleName(), roleName)).count();
        return (count > 0);
    }

    /**
     * Returns true if the user has the given role more than one time (either via assignment or detection or both)
     * @param identity The identity to check
     * @param roleName The role name to look for
     * @return true if the user has at least two assigned/detected roles of this name
     */
    public boolean hasMultiple(Identity identity, String roleName) {
        long assignedCount = Utilities.safeStream(identity.getRoleAssignments()).filter(rd -> Util.nullSafeEq(rd.getRoleName(), roleName)).count();
        long detectedCount = Utilities.safeStream(identity.getRoleAssignments()).filter(rd -> Util.nullSafeEq(rd.getRoleName(), roleName)).count();
        return (assignedCount + detectedCount) > 1;
    }

    /**
     * Transforms the existing Map in place by replacing attributes of type Secret with asterisks
     * @param attributes The attribute map to modify
     */
    public void maskSecretAttributes(Map<String, Object> attributes) {
        ObjectConfig identityObjectConfig = Identity.getObjectConfig();
        for(ObjectAttribute attribute : identityObjectConfig.getObjectAttributes()) {
            if (attribute.getType().equals(ObjectAttribute.TYPE_SECRET)) {
                if (attributes.containsKey(attribute.getName())) {
                    attributes.put(attribute.getName(), "********");
                }
            }
        }
    }

    /**
     * Returns a recursive list of all subordinates of the given Identity by recursively navigating
     * other Identity objects starting with this one as their 'manager'.
     *
     * @param parent The parent Identity
     * @return A list of object arrays, containing the 'id' and 'name' of any Identities
     * @throws GeneralException if this fails
     */
    public List<Object[]> recursivelyExplodeHierarchy(Identity parent) throws GeneralException {
        return recursivelyExplodeHierarchy(parent.getId(), "manager");
    }

    /**
     * Returns the entire tree below the 'parent' Identity by recursively querying for other
     * objects that reference it via the given attribute. For example, this might return
     * a manager's entire tree of subordinates.
     *
     * @param parent an Identity ID to search in the given attribute
     * @param attribute the attribute containing an Identity ID reference (e.g., `manager`)
     * @return A list of object arrays, containing the 'id' and 'name' of any Identities
     * @throws GeneralException if this fails
     */
    public List<Object[]> recursivelyExplodeHierarchy(String parent, String attribute) throws GeneralException {
        List<Object[]> outputBucket = new ArrayList<>();
        QueryOptions qo = new QueryOptions();
        qo.addFilter(Filter.eq(attribute, parent));
        List<String> props = new ArrayList<>();
        props.add("id");
        props.add("name");
        Iterator<Object[]> subordinates = context.search(Identity.class, qo, props);
        if (subordinates != null) {
            try {
                while (subordinates.hasNext()) {
                    Object[] so = subordinates.next();
                    outputBucket.add(so);
                    outputBucket.addAll(recursivelyExplodeHierarchy((String)so[0], attribute));
                }
            } finally {
                Util.flushIterator(subordinates);
            }
        }
        return outputBucket;
    }

    /**
     * Recursively expands the input Identity, returning a list of workgroup members. If the input
     * Identity is not a workgroup, it is returned alone. If any members of a workgroup are themselves
     * workgroups, they will be recursively expanded.
     *
     * This can be used, for example, to send a notification to an entire workgroup.
     *
     * @param possibleWorkgroup an {@link Identity} object, which is likely a workgroup
     * @return The list of Identities in the given workgroup, and any child workgroups
     * @throws GeneralException if this fails
     */
    public List<Identity> recursivelyExplodeWorkgroup(Identity possibleWorkgroup) throws GeneralException {
        List<Identity> identities = new ArrayList<>();
        if (!possibleWorkgroup.isWorkgroup()) {
            identities.add(possibleWorkgroup);
            return identities;
        }
        List<String> props = new ArrayList<>();
        props.add("id");
        Iterator<Object[]> members = ObjectUtil.getWorkgroupMembers(context, possibleWorkgroup, props);
        try {
            while (members.hasNext()) {
                Object[] dehydrated = members.next();
                Identity hydrated = ObjectUtil.getIdentityOrWorkgroup(context, (String) dehydrated[0]);
                if (hydrated.isWorkgroup()) {
                    identities.addAll(recursivelyExplodeWorkgroup(hydrated));
                } else {
                    identities.add(hydrated);
                }
            }
        } finally {
            Util.flushIterator(members);
        }
        return identities;
    }

    /**
     * Performs a refresh with default options on the identity
     * @param id The identity in question
     * @throws GeneralException if any IIQ failure occurs
     */
    public void refresh(Identity id) throws GeneralException {
        refresh(id, false);
    }

    /**
     * Performs a refresh with mostly-default options on the identity
     * @param id The identity to target
     * @param shouldProcessEvents if true, processEvents will also be added
     * @throws GeneralException if any IIQ failure occurs
     */
    public void refresh(Identity id, boolean shouldProcessEvents) throws GeneralException {
        Identity reloaded = context.getObjectById(Identity.class, id.getId());

        Attributes<String, Object> args = getDefaultRefreshOptions(shouldProcessEvents);

        refresh(reloaded, args);
    }

    /**
     * Performs a refresh against the identity with the given arguments
     * @param id The target identity
     * @param args the refresh arguments
     * @throws GeneralException if any IIQ failure occurs
     */
    public void refresh(Identity id, Map<String, Object> args) throws GeneralException {
        Attributes<String, Object> attributes = new Attributes<>();
        attributes.putAll(args);

        Identitizer identitizer = new Identitizer(context, attributes);
        identitizer.refresh(id);
    }

    /**
     * Attempt to do a best effort rename of a user. Note that this will not catch usernames stored in:
     *
     *  (1) ProvisioningPlan objects
     *  (2) Running workflow variables
     *
     * @param target The Identity object to rename
     * @param newName The new name of the identity
     * @throws GeneralException if any renaming failures occur
     */
    @Experimental
    public void rename(Identity target, String newName) throws GeneralException {
        // TODO: CertificationDefinition selfCertificationViolationOwner
        // TODO there is probably more to do around certifications (e.g. Certification.getCertifiers())
        // CertificationItem has a getIdentity() but it depends on CertificationEntity, so we're fine
        String[] queries = new String[] {
                "update spt_application_activity set identity_name = ? where identity_name = ?",
                // This needs to run twice, once for MSMITH and once for Identity:MSMITH
                "update spt_audit_event set target = ? where target = ?",
                "update spt_audit_event set source = ? where source = ?",
                "update spt_identity_request set owner_name = ? where owner_name = ?", // TODO approver_name?
                "update spt_identity_snapshot set identity_name = ? where identity_name = ?",
                "update spt_provisioning_transaction set identity_name = ? where identity_name = ?",
                "update spt_certification set creator = ? where creator = ?",
                "update spt_certification set manager = ? where manager = ?",
                // Oddly, this is actually the name, not the ID
                "update spt_certification_entity set identity_id = ? where identity_id = ?",
                "update spt_certification_item set target_name = ? where target_name = ?",
                "update spt_identity_history_item set actor = ? where actor = ?",
                "update spt_task_result set launcher = ? where launcher = ?",

        };
        SailPointContext privateContext = SailPointFactory.createPrivateContext();
        try {
            Identity privateIdentity = privateContext.getObjectById(Identity.class, target.getId());
            privateIdentity.setName(newName);
            privateContext.saveObject(privateIdentity);
            privateContext.commitTransaction();

            try (Connection db = ContextConnectionWrapper.getConnection(privateContext)) {
                try {
                    db.setAutoCommit(false);
                    for (String query : queries) {
                        try (PreparedStatement stmt = db.prepareStatement(query)) {
                            stmt.setString(1, newName);
                            stmt.setString(2, target.getName());
                            stmt.executeUpdate();
                        }
                    }
                    db.commit();
                } finally {
                    db.setAutoCommit(true);
                }
            } catch(SQLException e) {
                throw new GeneralException(e);
            }

        } catch(GeneralException e) {
            privateContext.rollbackTransaction();
        } finally {
            SailPointFactory.releasePrivateContext(privateContext);
        }

    }

}
