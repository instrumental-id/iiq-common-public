package com.identityworksllc.iiq.common;

import sailpoint.api.SailPointContext;
import sailpoint.object.*;
import sailpoint.search.MapMatcher;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Pair;
import sailpoint.tools.Util;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Utilities for acting on and generating ProvisioningPlan objects
 */
@SuppressWarnings("unused")
public class Plans {

    /**
     * A value you can pass to either removeAttributeRequest or hasAttributeRequest
     * to indicate a literal null instead of a wildcard (which is the default interpretation
     * of null inputs to those methods).
     *
     * This will match anything that {@link Utilities#isNothing(Object)} matches.
     */
    public static final String EMPTY_VALUE = Plans.class.getName() + "*NULL*";

    /**
     * Adds an AccountRequest to the given plan for each non-disabled account owned by the user
     * unless the account is matched by the exceptFilter.
     */
    public static void disableAccounts(SailPointContext context, Identity target, ProvisioningPlan plan, Filter exceptFilter) throws GeneralException {
        MapMatcher matcher = null;
        if (exceptFilter != null) {
            matcher = new MapMatcher(exceptFilter);
        }
        for(Link link : Util.safeIterable(target.getLinks())) {
            if (!link.isDisabled() && (matcher == null || !matcher.matches(Mapper.toMap(link)))) {
                plan.add(link.getApplicationName(), link.getNativeIdentity(), ProvisioningPlan.AccountRequest.Operation.Disable);
            }
        }
    }

    /**
     * Empties the input plan's account and object requests in place (i.e, by modifying
     * the Lists within the plan itself). This can be used to cancel a provisioning operation
     * in a Before Provisioning rule, for example.
     *
     * @param plan The plan
     */
    public static void emptyPlan(ProvisioningPlan plan) {
        if (plan != null) {
            if (plan.getAccountRequests() != null) {
                plan.getAccountRequests().clear();
            }
            if (plan.getObjectRequests() != null) {
                plan.getObjectRequests().clear();
            }
        }
    }

    /**
     * Adds an AccountRequest to the given plan for each non-disabled account owned by the user
     * unless the account is matched by the exceptFilter.
     */
    public static void enableAccounts(SailPointContext context, Identity target, ProvisioningPlan plan, Filter exceptFilter) throws GeneralException {
        MapMatcher matcher = null;
        if (exceptFilter != null) {
            matcher = new MapMatcher(exceptFilter);
        }
        for(Link link : Util.safeIterable(target.getLinks())) {
            if (link.isDisabled() && (matcher == null || !matcher.matches(Mapper.toMap(link)))) {
                plan.add(link.getApplicationName(), link.getNativeIdentity(), ProvisioningPlan.AccountRequest.Operation.Enable);
            }
        }
    }

    /**
     * If an entitlement is being added to the given application in the given plan, also
     * add a separate Enable operation. This is important for connectors like Salesforce
     * where entitlements cannot be added to disabled accounts.
     */
    public static void enableOnEntitlementAdd(ProvisioningPlan plan, Application application, boolean enableFirst) {
        Schema appSchema = application.getAccountSchema();
        boolean expandEnable = false;
        ProvisioningPlan.AccountRequest template = null;
        for(ProvisioningPlan.AccountRequest accountRequest : Util.safeIterable(plan.getAccountRequests(application.getName()))) {
            for(ProvisioningPlan.AttributeRequest attributeRequest : Util.safeIterable(accountRequest.getAttributeRequests())) {
                if (attributeRequest.getOperation().equals(ProvisioningPlan.Operation.Add) || attributeRequest.getOperation().equals(ProvisioningPlan.Operation.Set)) {
                    String attributeName = attributeRequest.getName();
                    AttributeDefinition attributeDefinition = appSchema.getAttributeDefinition(attributeName);
                    if (attributeDefinition != null) {
                        if (attributeDefinition.isEntitlement() || attributeDefinition.isManaged()) {
                            expandEnable = true;
                            template = accountRequest;
                            break;
                        }
                    }
                }
            }
        }
        if (expandEnable) {
            ProvisioningPlan.AccountRequest enable = new ProvisioningPlan.AccountRequest();
            enable.setOperation(ProvisioningPlan.AccountRequest.Operation.Enable);
            enable.setNativeIdentity(template.getNativeIdentity());
            enable.setApplication(template.getApplicationName());
            enable.setInstance(template.getInstance());
            enable.setComments("Expand entitlement add to enable");
            enable.setArguments(template.getArguments());
            List<ProvisioningPlan.AccountRequest> requests = plan.getAccountRequests();
            if (enableFirst) {
                requests.add(0, enable);
            } else {
                requests.add(enable);
            }
            plan.setAccountRequests(requests);
        }
    }

    /**
     * Extracts the target attribute from its AccountRequest into a new, second request
     * against the same account.
     */
    public static void extractToNewRequest(ProvisioningPlan plan, String targetAttribute) {
        extractToNewRequest(plan, targetAttribute, false);
    }

    /**
     * Extracts the target attribute from its AccountRequest into a new, second request
     * against the same account. The new request will be placed at the beginning of the
     * ProvisioningPlan sequence.
     */
    public static void extractToNewRequest(ProvisioningPlan plan, String targetAttribute, boolean atBeginning) {
        if (plan.getAccountRequests() == null) {
            return;
        }
        List<ProvisioningPlan.AccountRequest> toAdd = new ArrayList<>();
        for(ProvisioningPlan.AccountRequest accountRequest : plan.getAccountRequests()) {
            if (accountRequest.getAttributeRequests() == null) {
                continue;
            }
            if (accountRequest.getAttributeRequest(targetAttribute) != null) {
                ProvisioningPlan.AttributeRequest attribute = accountRequest.getAttributeRequest(targetAttribute);
                ProvisioningPlan.AccountRequest newRequest = accountRequest.clone();
                newRequest.cloneAccountProperties(accountRequest);
                newRequest.setAttributeRequests(new ArrayList<>());
                newRequest.add(attribute);
                accountRequest.remove(attribute);
                toAdd.add(newRequest);
            }
        }
        List<ProvisioningPlan.AccountRequest> requests = plan.getAccountRequests();
        if (atBeginning) {
            requests.addAll(0, toAdd);
        } else {
            requests.addAll(toAdd);
        }
        plan.setAccountRequests(requests);
    }

    /**
     * Finds attribute requests in the provisioning plan where the account matches the filter
     *
     * @param accountRequest The provisioning plan
     * @param findPredicate The account filter
     * @return The list of account requests
     */
    public static List<ProvisioningPlan.AttributeRequest> find(ProvisioningPlan.AccountRequest accountRequest, Predicate<ProvisioningPlan.AttributeRequest> findPredicate) {
        return Utilities.safeStream(accountRequest.getAttributeRequests()).filter(findPredicate).collect(Collectors.toList());
    }

    /**
     * Finds attribute requests in the provisioning plan where the account matches the filter
     *
     * @param plan The provisioning plan
     * @param findPredicate The account filter
     * @return The list of account requests
     */
    public static List<ProvisioningPlan.AccountRequest> find(ProvisioningPlan plan, Predicate<ProvisioningPlan.AccountRequest> findPredicate) {
        return Utilities.safeStream(plan.getAccountRequests()).filter(findPredicate).collect(Collectors.toList());
    }

    /**
     * Find pairs of account/attribute requests in the provisioning plan where the
     * account and attribute together match the filter.
     *
     * @param plan The provisioning plan
     * @param findPredicate The account and attribute filter
     * @return The list of account/attribute pairs
     */
    public static List<Pair<ProvisioningPlan.AccountRequest, ProvisioningPlan.AttributeRequest>> find(ProvisioningPlan plan, BiPredicate<ProvisioningPlan.AccountRequest, ProvisioningPlan.AttributeRequest> findPredicate) {
        List<Pair<ProvisioningPlan.AccountRequest, ProvisioningPlan.AttributeRequest>> pairs = new ArrayList<>();
        for(ProvisioningPlan.AccountRequest accountRequest : Util.safeIterable(plan.getAccountRequests())) {
            for(ProvisioningPlan.AttributeRequest attributeRequest : Util.safeIterable(accountRequest.getAttributeRequests())) {
                if (findPredicate.test(accountRequest, attributeRequest)) {
                    pairs.add(new Pair<>(accountRequest, attributeRequest));
                }
            }
        }
        return pairs;
    }

    /**
     * Find pairs of account/attribute requests in the provisioning plan where the
     * account matches the first filter and the account/attribute combined match
     * the second filter.
     *
     * @param plan The provisioning plan
     * @param accountFilter The account filter
     * @param attributeFilter The attribute filter
     * @return The list of account/attribute pairs
     */
    public static List<Pair<ProvisioningPlan.AccountRequest, ProvisioningPlan.AttributeRequest>> find(ProvisioningPlan plan, Predicate<ProvisioningPlan.AccountRequest> accountFilter, BiPredicate<ProvisioningPlan.AccountRequest, ProvisioningPlan.AttributeRequest> attributeFilter) {
        BiPredicate<ProvisioningPlan.AccountRequest, ProvisioningPlan.AttributeRequest> combo = (a, b) -> {
            if (accountFilter.test(a)) {
                return attributeFilter.test(a, b);
            }
            return false;
        };
        return find(plan, combo);
    }

    /**
     * Finds attribute requests matching any of the given names
     *
     * @param attributeName The attribute name(s)
     * @return The predicate object
     */
    public static BiPredicate<ProvisioningPlan.AccountRequest, ProvisioningPlan.AttributeRequest> hasAttributeNames(final String... attributeName) {
        final List<String> names = new ArrayList<>();
        if (attributeName != null) {
            names.addAll(Arrays.asList(attributeName));
        }

        return (accountRequest, attributeRequest) -> {
            for(String name : names) {
                if (Util.nullSafeEq(attributeRequest.getName(), name)) {
                    return true;
                }
            }
            return false;
        };
    }

    /**
     * Finds an attribute request matching the name, operation, and values as defined. Null
     * and empty values are considered a "skip this match".
     *
     * @param attributeName The attribute name
     * @param operation The operation to match
     * @param value The value(s) to match
     * @return The predicate object
     */
    public static BiPredicate<ProvisioningPlan.AccountRequest, ProvisioningPlan.AttributeRequest> hasAttributeRequest(final String attributeName, final ProvisioningPlan.Operation operation, final Object value) {
        return (accountRequest, attributeRequest) -> {
            if (Util.isNullOrEmpty(attributeName) || Util.nullSafeEq(attributeRequest.getName(), attributeName)) {
                if (operation == null || Util.nullSafeEq(attributeRequest.getOperation(), operation)) {
                    if (Utilities.isNothing(value) || Utilities.safeContainsAll(attributeRequest.getValue(), value)) {
                        return true;
                    } else if (Util.nullSafeEq(value, EMPTY_VALUE) && Utilities.isNothing(attributeRequest.getValue())) {
                        return true;
                    }
                }
            }
            return false;
        };
    }

    /**
     * Remove all assigned entitlements (i.e. AttributeAssignments requested via LCM or
     * added via certification) from the user, except those matched by the 'exceptFilter'.
     */
    public static void removeAssignedEntitlements(SailPointContext context, Identity target, ProvisioningPlan plan, Filter exceptFilter) throws GeneralException {
        MapMatcher matcher = null;
        if (exceptFilter != null) {
            matcher = new MapMatcher(exceptFilter);
        }
        for(AttributeAssignment ra : Util.safeIterable(target.getAttributeAssignments())) {
            if (matcher == null || !matcher.matches(Mapper.toMap(ra))) {
                ProvisioningPlan.AccountRequest accountRequest = plan.getAccountRequest(ra.getApplicationName(), ra.getInstance(), ra.getNativeIdentity());
                if (accountRequest == null) {
                    accountRequest = new ProvisioningPlan.AccountRequest();
                    accountRequest.setApplication(ra.getApplicationName());
                    accountRequest.setNativeIdentity(ra.getNativeIdentity());
                    accountRequest.setInstance(ra.getInstance());
                    accountRequest.setOperation(ProvisioningPlan.AccountRequest.Operation.Modify);
                    plan.add(accountRequest);
                }
                ProvisioningPlan.AttributeRequest attributeRequest = new ProvisioningPlan.AttributeRequest(ra.getName(), ProvisioningPlan.Operation.Remove, ra.getStringValue());
                attributeRequest.setAssignment(true);
                accountRequest.add(attributeRequest);
            }
        }
    }

    /**
     * Remove all assigned roles from the given Identity by adding Remove operations to the
     * given ProvisioningPlan. Role assignments matched by the 'exceptFilter' will not be removed.
     */
    public static void removeAssignedRoles(SailPointContext context, Identity target, ProvisioningPlan plan, Filter exceptFilter) throws GeneralException {
        MapMatcher matcher = null;
        if (exceptFilter != null) {
            matcher = new MapMatcher(exceptFilter);
        }
        for(RoleAssignment ra : Util.safeIterable(target.getRoleAssignments())) {
            if (matcher == null || !matcher.matches(Mapper.toMap(ra))) {
                ProvisioningPlan.AccountRequest iiqRequest = plan.getIIQAccountRequest();
                if (iiqRequest == null) {
                    iiqRequest = new ProvisioningPlan.AccountRequest(ProvisioningPlan.AccountRequest.Operation.Modify, "IIQ", null, target.getName());
                    plan.add(iiqRequest);
                }
                ProvisioningPlan.AttributeRequest removeRequest = new ProvisioningPlan.AttributeRequest("assignedRoles", ProvisioningPlan.Operation.Remove, ra.getRoleName());
                removeRequest.setAssignmentId(ra.getAssignmentId());
                iiqRequest.add(removeRequest);
            }
        }
    }

    /**
     * Removes the given attribute request(s) matching by either name or operation.
     *
     * @param plan The plan to modify
     * @param attributeName The attribute to remove (by name)
     * @param attributeOperation The attribute to remove (by operation)
     */
    public static void removeAttributeRequest(ProvisioningPlan plan, String attributeName, ProvisioningPlan.Operation attributeOperation) {
        removeAttributeRequest(plan, attributeName, attributeOperation, null);
    }

    /**
     * Removes the given attribute request(s) matching by name.
     *
     * @param plan The plan to modify
     * @param attributeName The attribute to remove (by name)
     */
    public static void removeAttributeRequest(ProvisioningPlan plan, String attributeName) {
        removeAttributeRequest(plan, attributeName, null, null);
    }

    /**
     * Removes the given attribute request(s) matching by either name, operation, or both, from any
     * account requests on this plan. Nulls provided for any of the three criteria will skip matching that
     * attribute.
     *
     * If you want to match an actual null or empty value, use {@link #EMPTY_VALUE}.
     *
     * @param plan The plan to modify
     * @param attributeName The attribute name (possibly null) to match
     * @param attributeOperation The attribute operation (possibly null) to match
     * @param attributeValue The attribute value (possibly null) to match
     */
    public static void removeAttributeRequest(ProvisioningPlan plan, String attributeName, ProvisioningPlan.Operation attributeOperation, Object attributeValue) {
        for(ProvisioningPlan.AccountRequest accountRequest : Util.safeIterable(plan.getAccountRequests())) {
            if (accountRequest.getAttributeRequests() != null) {
                removeAttributeRequest(accountRequest, attributeName, attributeOperation, attributeValue);
            }
        }
    }

    /**
     * Removes the attribute requests matching by name, operation, and/or value from the given
     * account request. Nulls provided for any of the three criteria will skip matching that
     * attribute.
     *
     * @param accountRequest The account request to modify
     * @param attributeName The attribute name
     * @param attributeOperation The attribute operation
     * @param attributeValue The attribute value
     */
    public static void removeAttributeRequest(ProvisioningPlan.AccountRequest accountRequest, String attributeName, ProvisioningPlan.Operation attributeOperation, Object attributeValue) {
        // Copy to a temporary list to avoid concurrent modification exceptions
        List<ProvisioningPlan.AttributeRequest> temporaryList = new ArrayList<>(accountRequest.getAttributeRequests());
        for(ProvisioningPlan.AttributeRequest attributeRequest : temporaryList) {
            if (Util.isNullOrEmpty(attributeName) || Util.nullSafeEq(attributeRequest.getName(), attributeName)) {
                if (attributeOperation == null || Util.nullSafeEq(attributeRequest.getOperation(), attributeOperation)) {
                    if (attributeValue == null || Util.nullSafeEq(attributeRequest.getValue(), attributeValue)) {
                        accountRequest.remove(attributeRequest);
                    } else if (Util.nullSafeEq(attributeValue, EMPTY_VALUE) && Utilities.isNothing(attributeRequest.getValue())) {
                        accountRequest.remove(attributeRequest);
                    }
                }
            }
        }
    }

    /**
     * Remove all entitlements from the given Identity by adding Remove operations to the
     * given ProvisioningPlan. Entitlements matched by the 'exceptFilter' will not be removed.
     */
    public static void removeEntitlements(SailPointContext context, Identity target, ProvisioningPlan plan, Filter exceptFilter) throws GeneralException {
        for(Link link : Util.safeIterable(target.getLinks())) {
            Filter appFilter = Filter.eq("application.name", link.getApplicationName());
            MapMatcher matcher = null;
            if (exceptFilter != null) {
                matcher = new MapMatcher(exceptFilter);
            }
            Attributes<String, Object> entitlements = link.getEntitlementAttributes();
            if (entitlements != null) {
                for(String name : entitlements.getKeys()) {
                    List<String> values = entitlements.getStringList(name);
                    for(String value : Util.safeIterable(values)) {
                        QueryOptions qo = new QueryOptions();
                        qo.addFilter(appFilter);
                        qo.addFilter(Filter.eq("attribute", name));
                        qo.addFilter(Filter.eq("value", value));
                        qo.setResultLimit(1);
                        ManagedAttribute ma = Utilities.safeSubscript(context.getObjects(ManagedAttribute.class, qo), 0);
                        if (ma != null) {
                            Map<String, Object> maMap = Mapper.toMap(link, ma);
                            if (matcher == null || !matcher.matches(maMap)) {
                                plan.add(link.getApplicationName(), link.getNativeIdentity(), name, ProvisioningPlan.Operation.Remove, value);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Sets the given other attribute to the given value on any entitlement add.
     */
    public static void setOnEntitlementAdd(ProvisioningPlan plan, Application application, String otherAttribute, Object value) {
        Schema appSchema = application.getAccountSchema();
        boolean expandEnable = false;
        ProvisioningPlan.AccountRequest template = null;
        for(ProvisioningPlan.AccountRequest accountRequest : Util.safeIterable(plan.getAccountRequests(application.getName()))) {
            for(ProvisioningPlan.AttributeRequest attributeRequest : Util.safeIterable(accountRequest.getAttributeRequests())) {
                if (attributeRequest.getOperation().equals(ProvisioningPlan.Operation.Add) || attributeRequest.getOperation().equals(ProvisioningPlan.Operation.Set)) {
                    String attributeName = attributeRequest.getName();
                    AttributeDefinition attributeDefinition = appSchema.getAttributeDefinition(attributeName);
                    if (attributeDefinition != null) {
                        if (attributeDefinition.isEntitlement() || attributeDefinition.isManaged()) {
                            expandEnable = true;
                            template = accountRequest;
                            break;
                        }
                    }
                }
            }
        }
        if (expandEnable) {
            ProvisioningPlan.AccountRequest modify = new ProvisioningPlan.AccountRequest();
            modify.setOperation(ProvisioningPlan.AccountRequest.Operation.Modify);
            modify.setNativeIdentity(template.getNativeIdentity());
            modify.setApplication(template.getApplicationName());
            modify.setInstance(template.getInstance());
            modify.setComments("Expand entitlement add to set " + otherAttribute);
            modify.setArguments(template.getArguments());
            ProvisioningPlan.AttributeRequest attr = new ProvisioningPlan.AttributeRequest(otherAttribute, ProvisioningPlan.Operation.Set, value);
            modify.add(attr);
            plan.add(modify);
        }
    }

    /**
     * Sorts the AccountRequests in the given ProvisioningPlan using the given sorter.
     * Several useful sorters are provided in {@link PlanComparators}.
     */
    public static void sort(ProvisioningPlan plan, Comparator<ProvisioningPlan.AccountRequest> sorter) {
        List<ProvisioningPlan.AccountRequest> requests = plan.getAccountRequests();
        if (requests != null) {
            requests.sort(sorter);
            plan.setAccountRequests(requests);
        }
    }

    /**
     * Sorts the AccountRequests in the given ProvisioningPlan using the default order,
     * which is defined in {@link PlanComparators#defaultSequence()}. Attributes on each
     * AccountRequest are then sorted using the default attribute comparator.
     *
     * The order is roughly create, modify, status changes, delete. This is what most
     * connectors are expecting when more than one operation happens at once.
     */
    public static void sort(ProvisioningPlan plan) {
        sort(plan, PlanComparators.defaultSequence());
        for(ProvisioningPlan.AccountRequest accountRequest : Util.safeIterable(plan.getAccountRequests())) {
            sort(accountRequest);
        }
    }

    /**
     * Sorts the AttributeRequessts in the given ProvisioningPlan using the default order,
     * which is defined in {@link PlanComparators#defaultAttributeSequence()}. The essence
     * is removes first, then sets, then adds.
     */
    public static void sort(ProvisioningPlan.AccountRequest accountRequest) {
        List<ProvisioningPlan.AttributeRequest> attributeRequests = accountRequest.getAttributeRequests();
        if (attributeRequests != null) {
            attributeRequests.sort(PlanComparators.defaultAttributeSequence());
            accountRequest.setAttributeRequests(attributeRequests);
        }
    }

}
