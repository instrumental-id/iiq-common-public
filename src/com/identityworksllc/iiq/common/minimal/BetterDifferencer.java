package com.identityworksllc.iiq.common.minimal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.Differencer;
import sailpoint.api.SailPointContext;
import sailpoint.object.*;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Pair;
import sailpoint.tools.Util;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * An API to reimplement the Differencer to increase its reliability with various
 * Sailpoint objects.
 *
 * Most notably:
 *
 *  (1) LinkSnapshots will be compared correctly. That is, if the user has more than one
 *      account on the same Application, the correct LinkSnapshot will be returned, rather
 *      than simply returning the first one. Only if there are no matches by Native Identity
 *      will the first LinkSnapshot be returned.
 *
 *  (2) The Sameness class will be used to compare changes. This will account for a few
 *      differences in type and order that are not accounted for out-of-box.
 *
 *  (3) Changes solely in String case will be ignored in most cases.
 *
 *  (4) Old and new values will be sorted, for easier comparison.
 *
 * TODO: Policy violation differences
 */
public class BetterDifferencer {

    /**
     * A container for holding a pair of links judged by this tool to be the same
     * Link in two different snapshot contexts
     */
    private static class LinkPair {
        private LinkSnapshot ls1;
        private LinkSnapshot ls2;

        public LinkPair() {
            /* Empty */
        }

        public LinkPair(LinkSnapshot ls1, LinkSnapshot ls2) {
            this.ls1 = ls1;
            this.ls2 = ls2;
        }

        public LinkSnapshot getLs1() {
            return ls1;
        }

        public LinkSnapshot getLs2() {
            return ls2;
        }

        public void setLs1(LinkSnapshot ls1) {
            this.ls1 = ls1;
        }

        public void setLs2(LinkSnapshot ls2) {
            this.ls2 = ls2;
        }
    }
    private Set<String> caseInsensitiveApplications;
    private Set<String> caseInsensitiveFields;
    private final SailPointContext context;
    private boolean guessRenames;
    private final Log log;

    public BetterDifferencer(SailPointContext context) {
        this.log = LogFactory.getLog(this.getClass());
        this.context = Objects.requireNonNull(context);
        this.caseInsensitiveFields = new HashSet<>();
        this.caseInsensitiveApplications = new HashSet<>();
    }

    private void addAddedRemovedValues(Difference difference, Object oldValue, Object newValue) {
        List<Object> oldCollection = new ArrayList<>();
        List<Object> newCollection = new ArrayList<>();

        if (oldValue instanceof Collection) {
            oldCollection.addAll((Collection<?>)oldValue);
        } else if (oldValue != null) {
            oldCollection.add(oldValue);
        }

        if (newValue instanceof Collection) {
            newCollection.addAll((Collection<?>)newValue);
        } else if (newValue != null) {
            newCollection.add(newValue);
        }

        List<String> removedValues = oldCollection.stream().filter(o -> !Utilities.caseInsensitiveContains(newCollection, o)).filter(Objects::nonNull).map(String::valueOf).collect(Collectors.toList());
        List<String> addedValues = newCollection.stream().filter(o -> !Utilities.caseInsensitiveContains(oldCollection, o)).filter(Objects::nonNull).map(String::valueOf).collect(Collectors.toList());

        if (!addedValues.isEmpty()) {
            difference.setAddedValues(addedValues);
        }
        if (!removedValues.isEmpty()) {
            difference.setRemovedValues(removedValues);
        }
    }

    public void addCaseInsensitiveApplication(String application) {
        this.caseInsensitiveApplications.add(application);
    }

    public void addCaseInsensitiveField(String application, String field) {
        caseInsensitiveFields.add(application + ": " + field);
    }

    private void allNew(Consumer<Difference> differenceConsumer, Predicate<String> isMultiValued, Function<String, String> getDisplayName, Attributes<String, Object> afterAttributes) {
        for (String key : afterAttributes.getKeys()) {
            Object newValue = afterAttributes.get(key);
            Difference difference = new Difference();
            difference.setAttribute(key);
            difference.setNewValue(Utilities.safeString(newValue));
            difference.setMulti(isMultiValued.test(key));
            difference.setDisplayName(getDisplayName.apply(key));
            if (newValue instanceof Collection) {
                addAddedRemovedValues(difference, null, newValue);
            }
            differenceConsumer.accept(difference);
        }
    }

    private void allOld(Consumer<Difference> differenceConsumer, Predicate<String> isMultiValued, Function<String, String> getDisplayName, Attributes<String, Object> beforeAttributes) {
        for (String key : beforeAttributes.getKeys()) {
            Object oldValue = beforeAttributes.get(key);
            Difference difference = new Difference();
            difference.setAttribute(key);
            difference.setOldValue(Utilities.safeString(oldValue));
            difference.setMulti(isMultiValued.test(key));
            difference.setDisplayName(getDisplayName.apply(key));
            if (oldValue instanceof Collection) {
                addAddedRemovedValues(difference, oldValue, null);
            }
            differenceConsumer.accept(difference);
        }
    }

    /**
     * Diffs the two snapshots and returns an IdentityDifference object containing all
     * of the attribute, link, and role differences.
     *
     * TODO support permissions and policy violations
     *
     * @param before The before snapshot
     * @param after The after snapshot
     * @return Any differences between the two snapshots
     * @throws GeneralException if any failures occur
     */
    public IdentityDifference diff(IdentitySnapshot before, IdentitySnapshot after) throws GeneralException {
        IdentityDifference differences = new IdentityDifference();
        diffIdentityAttributes(differences, before, after);
        List<LinkPair> linkPairs = findLinkPairs(before, after);
        for(LinkPair pair : linkPairs) {
            String context;
            if (pair.ls2 != null) {
                context = IdentityDifference.generateContext(pair.ls2.getApplicationName(), pair.ls2.getNativeIdentity());
            } else {
                context = IdentityDifference.generateContext(pair.ls1.getApplicationName(), pair.ls1.getNativeIdentity());
            }
            diffLinks(differences, context, pair.ls1, pair.ls2);
        }

        // TODO I really need to figure out a good way to do role differences
        // because there's so much more in the BundleSnapshot and the RoleAssignmentSnapshot
        // that isn't easily captured by the Difference class. Notably, the associated
        // entitlements and role targets.
        List<String> rolesBefore = Utilities.safeStream(before.getBundles()).map(BundleSnapshot::getName).sorted().collect(Collectors.toList());
        List<String> rolesAfter = Utilities.safeStream(after.getBundles()).map(BundleSnapshot::getName).sorted().collect(Collectors.toList());
        Difference rolesDifference = Difference.diff(rolesBefore, rolesAfter, 4000, true);
        if (rolesDifference != null) {
            differences.addBundleDifference(rolesDifference);
        }

        List<String> assignmentsBefore = Utilities.safeStream(before.getAssignedRoles()).map(RoleAssignmentSnapshot::getName).sorted().collect(Collectors.toList());
        List<String> assignmentsAfter = Utilities.safeStream(after.getAssignedRoles()).map(RoleAssignmentSnapshot::getName).sorted().collect(Collectors.toList());
        Difference assignmentDifference = Difference.diff(assignmentsBefore, assignmentsAfter, 4000, true);
        if (assignmentDifference != null) {
            differences.addAssignedRoleDifference(assignmentDifference);
        }
        return differences;
    }

    /**
     * Performs a diff of the two attribute maps provided, calling the supplied function hooks
     * as needed to handle the details.
     *
     * @param beforeAttributes The attributes from "before"
     * @param afterAttributes The attributes from "after"
     * @param exclusions Any attributes to exclude from consideration
     * @param isMultiValued A function that returns true if the given attribute is multi-valued
     * @param getDisplayName A function that returns the display name of the given attribute
     * @param differenceConsumer A handler to consume any Difference objects produced
     */
    private void diff(String application, Attributes<String, Object> beforeAttributes, Attributes<String, Object> afterAttributes, List<String> exclusions, Predicate<String> isMultiValued, Function<String, String> getDisplayName, Consumer<Difference> differenceConsumer) {
        Set<String> onlyBefore = new HashSet<>();
        Set<String> onlyAfter = new HashSet<>();
        Set<String> both = new HashSet<>();
        if (beforeAttributes == null) {
            allNew(differenceConsumer, isMultiValued, getDisplayName, afterAttributes);
        } else if (afterAttributes == null) {
            allOld(differenceConsumer, isMultiValued, getDisplayName, beforeAttributes);
        } else {
            for (String key : beforeAttributes.keySet()) {
                if (!afterAttributes.containsKey(key)) {
                    onlyBefore.add(key);
                } else {
                    both.add(key);
                }
            }
            for (String key : afterAttributes.keySet()) {
                if (!beforeAttributes.containsKey(key)) {
                    onlyAfter.add(key);
                } else {
                    both.add(key);
                }
            }
            for(String exclusion : Util.safeIterable(exclusions)) {
                onlyBefore.remove(exclusion);
                onlyAfter.remove(exclusion);
                both.remove(exclusion);
            }
            for (String key : both) {
                Object oldValue = beforeAttributes.get(key);
                Object newValue = afterAttributes.get(key);
                if (!Differencer.objectsEqual(oldValue, newValue, true)) {
                    boolean ignoreCase = false;
                    if (caseInsensitiveApplications.contains(application)) {
                        ignoreCase = true;
                    } else {
                        String field = application + ": " + key;
                        if (caseInsensitiveFields.contains(field)) {
                            ignoreCase = true;
                        }
                    }
                    if (!Sameness.isSame(oldValue, newValue, ignoreCase)) {
                        Difference difference = new Difference();
                        difference.setAttribute(key);
                        difference.setNewValue(Utilities.safeString(newValue));
                        difference.setOldValue(Utilities.safeString(oldValue));
                        difference.setMulti(isMultiValued.test(key));
                        difference.setDisplayName(getDisplayName.apply(key));
                        if (oldValue instanceof Collection || newValue instanceof Collection) {
                            addAddedRemovedValues(difference, oldValue, newValue);
                        }
                        differenceConsumer.accept(difference);
                    }
                }
            }
            for (String key : onlyBefore) {
                Object oldValue = beforeAttributes.get(key);
                Difference difference = new Difference();
                difference.setAttribute(key);
                difference.setOldValue(Utilities.safeString(oldValue));
                difference.setMulti(isMultiValued.test(key));
                difference.setDisplayName(getDisplayName.apply(key));
                if (oldValue instanceof Collection) {
                    addAddedRemovedValues(difference, oldValue, null);
                }
                differenceConsumer.accept(difference);
            }
            for (String key : onlyAfter) {
                Object newValue = afterAttributes.get(key);
                Difference difference = new Difference();
                difference.setAttribute(key);
                difference.setNewValue(Utilities.safeString(newValue));
                difference.setMulti(isMultiValued.test(key));
                difference.setDisplayName(getDisplayName.apply(key));
                if (newValue instanceof Collection) {
                    addAddedRemovedValues(difference, null, newValue);
                }
                differenceConsumer.accept(difference);
            }
        }
    }

    private void diffIdentityAttributes(IdentityDifference difference, IdentitySnapshot before, IdentitySnapshot after) {
        Attributes<String, Object> beforeAttributes = before.getAttributes();
        Attributes<String, Object> afterAttributes = after.getAttributes();
        final ObjectConfig identityAttributes = Identity.getObjectConfig();

        diff(
                ProvisioningPlan.APP_IIQ,
                beforeAttributes,
                afterAttributes,
                null,
                a -> nullSafeObjectAttribute(identityAttributes, a).isMulti(),
                a -> nullSafeObjectAttribute(identityAttributes, a).getDisplayName(),
                difference::addAttributeDifference
        );
    }

    private void diffLinks(IdentityDifference differences, String contextName, LinkSnapshot beforeLink, LinkSnapshot afterLink) throws GeneralException {
        List<String> exclusions = Arrays.asList("directPermissions", "targetPermissions");
        Application application = null;
        if (beforeLink != null) {
            application = context.getObject(Application.class, beforeLink.getApplication());
        }
        if (application == null && afterLink != null) {
            application = context.getObject(Application.class, afterLink.getApplication());
        }
        if (application == null){
            log.warn("Unable to find an application");
            if (beforeLink != null) {
                log.warn("Before application is " + beforeLink.getApplication());
            }
            if (afterLink != null) {
                log.warn("After application is " + afterLink.getApplication());
            }
            return;
        }
        boolean ignoreCase = false;
        if (caseInsensitiveApplications.contains(application.getName()) || application.isCaseInsensitive()) {
            ignoreCase = true;
        }
        Schema accountSchema = application.getAccountSchema();
        Attributes<String, Object> beforeAttributes = new Attributes<>();
        if (beforeLink != null && beforeLink.getAttributes() != null) {
            beforeAttributes.putAll(beforeLink.getAttributes());
        }
        Attributes<String, Object> afterAttributes = new Attributes<>();
        if (afterLink != null && afterLink.getAttributes() != null) {
            afterAttributes.putAll(afterLink.getAttributes());
        }
        List<Difference> linkDifferences = new ArrayList<>();
        diff(
                application.getName(),
                beforeAttributes,
                afterAttributes,
                exclusions,
                a -> nullSafeObjectAttribute(accountSchema, a).isMultiValued(),
                a -> nullSafeObjectAttribute(accountSchema, a).getDisplayName(),
                d -> {
                    d.setContext(contextName);
                    linkDifferences.add(d);
                }
        );

        // Check the rename case
        String beforeNativeIdentity = null;
        String afterNativeIdentity = null;
        if (beforeLink != null) {
            beforeNativeIdentity = beforeLink.getNativeIdentity();
        }
        if (afterLink != null) {
            afterNativeIdentity = afterLink.getNativeIdentity();
        }

        if (!Differencer.objectsEqual(beforeNativeIdentity, afterNativeIdentity, true)) {
            Difference niDifference = new Difference();
            niDifference.setAttribute("nativeIdentity");
            niDifference.setContext(contextName);
            niDifference.setOldValue(beforeNativeIdentity);
            niDifference.setNewValue(afterNativeIdentity);
            linkDifferences.add(niDifference);
        }

        differences.addLinkDifferences(linkDifferences);

        List<Permission> beforePermissions = getPermissions(beforeLink);
        List<Permission> afterPermissions = getPermissions(afterLink);

        Collections.sort(beforePermissions, Comparator.comparing(Permission::getTarget).thenComparing(Permission::getRights));
        Collections.sort(afterPermissions, Comparator.comparing(Permission::getTarget).thenComparing(Permission::getRights));

        diffPermissions(beforeLink, afterLink, beforePermissions, afterPermissions, ignoreCase, differences::add);
    }

    /**
     * Diffs the sorted permissions lists between the two link snapshots
     * @param beforeLink The 'before' Link
     * @param afterLink The 'after' Link
     * @param beforePermissions The 'before' permissions, sorted
     * @param afterPermissions The 'after' permissions, sorted
     * @param ignoreCase If true, case will be ignored for comparison of target and rights
     * @param differenceConsumer Differences will be passed to this callback for processing
     */
    private void diffPermissions(LinkSnapshot beforeLink, LinkSnapshot afterLink, List<Permission> beforePermissions, List<Permission> afterPermissions, boolean ignoreCase, Consumer<PermissionDifference> differenceConsumer) {
        List<Pair<Permission, Permission>> changes = new ArrayList<>();
        List<Permission> newPermissions = new ArrayList<>();
        if (afterPermissions != null) {
            // Copy so we can remove them as we match
            newPermissions.addAll(afterPermissions);
        }
        List<Permission> oldPermissions = new ArrayList<>();
        for(Permission p1 : Util.safeIterable(beforePermissions)) {
            Permission p2 = findPermission(p1, newPermissions, ignoreCase);
            if (p2 == null) {
                oldPermissions.add(p1);
            } else {
                newPermissions.remove(p2);
                Pair<Permission, Permission> pair = new Pair<>(p1, p2);
                changes.add(pair);
            }
        }
        for(Permission p1 : oldPermissions) {
            PermissionDifference permissionDifference = new PermissionDifference();
            permissionDifference.setRights(p1.getRights());
            permissionDifference.setTarget(p1.getTarget());
            permissionDifference.setApplication(beforeLink.getApplicationName());
            permissionDifference.setRemoved(true);
            differenceConsumer.accept(permissionDifference);
        }
        for(Pair<Permission, Permission> pair : changes) {
            // Would be nice to be able to track before/after here, but PermissionDifference
            // only tracks the after
            Permission p2 = pair.getSecond();
            PermissionDifference permissionDifference = new PermissionDifference();
            permissionDifference.setRights(p2.getRights());
            permissionDifference.setTarget(p2.getTarget());
            permissionDifference.setApplication(afterLink.getApplicationName());
            differenceConsumer.accept(permissionDifference);
        }
        for(Permission p2 : newPermissions) {
            PermissionDifference permissionDifference = new PermissionDifference();
            permissionDifference.setRights(p2.getRights());
            permissionDifference.setTarget(p2.getTarget());
            permissionDifference.setApplication(afterLink.getApplicationName());
            differenceConsumer.accept(permissionDifference);
        }
    }

    /**
     * Finds the a LinkSnapshot in the given list that matches the target by app,
     * instance, and nativeIdentity
     *
     * @param snapshots The list of LinkSnapshots to search
     * @param target The target to find
     * @return the discovered LinkSnapshot, or null if none
     */
    private LinkSnapshot findLink(List<LinkSnapshot> snapshots, LinkSnapshot target) {
        for(LinkSnapshot link : Util.safeIterable(snapshots)) {
            if (Differencer.objectsEqual(link.getApplicationName(), target.getApplicationName(), true) && Differencer.objectsEqual(link.getNativeIdentity(), target.getNativeIdentity(), true) && Differencer.objectsEqual(link.getInstance(), target.getInstance(), true)) {
                return link;
            }
        }
        return null;
    }

    private List<LinkPair> findLinkPairs(IdentitySnapshot before, IdentitySnapshot after) {
        List<LinkPair> pairs = new ArrayList<>();
        List<LinkSnapshot> beforeLinks = safeCopy(before.getLinks());
        List<LinkSnapshot> afterLinks = safeCopy(after.getLinks());

        Iterator<LinkSnapshot> beforeIterator = beforeLinks.iterator();
        while(beforeIterator.hasNext()) {
            LinkSnapshot ls1 = beforeIterator.next();
            LinkSnapshot ls2 = findLink(afterLinks, ls1);
            if (ls2 != null) {
                LinkPair pair = new LinkPair(ls1, ls2);
                afterLinks.remove(ls2);
                beforeIterator.remove();
                pairs.add(pair);
            }
        }

        if (shouldGuessRenames() && !beforeLinks.isEmpty() && !afterLinks.isEmpty()) {
            // Match by Application only for renames
            Iterator<LinkSnapshot> beforeIterator2 = beforeLinks.iterator();
            while(beforeIterator2.hasNext()) {
                LinkSnapshot ls1 = beforeIterator2.next();
                List<LinkSnapshot> candidates = findLinksBlindly(afterLinks, ls1);
                if (candidates.size() == 1) {
                    // We found just one, assume it's a rename
                    LinkSnapshot ls2 = candidates.get(0);
                    if (looksLikeRename(ls1, ls2)) {
                        LinkPair pair = new LinkPair(ls1, ls2);
                        afterLinks.remove(ls2);
                        beforeIterator2.remove();
                        pairs.add(pair);
                    }
                } else if (candidates.size() > 1) {
                    // See if there are any that look like a rename (i.e. match by all other attributes except nativeIdentity)
                    for(LinkSnapshot candidate : candidates) {
                        if (looksLikeRename(ls1, candidate)) {
                            LinkSnapshot ls2 = candidates.get(0);
                            LinkPair pair = new LinkPair(ls1, ls2);
                            afterLinks.remove(ls2);
                            beforeIterator2.remove();
                            pairs.add(pair);
                            break;
                        }
                    }
                }
            }
        }
        if (!afterLinks.isEmpty()) {
            // New accounts
            for(LinkSnapshot ls : afterLinks) {
                LinkPair pair = new LinkPair(null, ls);
                pairs.add(pair);
            }
        }
        if (!beforeLinks.isEmpty()) {
            // Deleted accounts
            for(LinkSnapshot ls : beforeLinks) {
                LinkPair pair = new LinkPair(ls, null);
                pairs.add(pair);
            }
        }

        return pairs;
    }

    /**
     * Finds any LinkSnapshots in the given list that matches the target by app
     * only, without checking the native identity. This is a last resort if the
     * account has been renamed.
     *
     * @param snapshots The list of LinkSnapshots to search
     * @param target The target to find
     * @return the discovered LinkSnapshot, or null if none
     */
    private List<LinkSnapshot> findLinksBlindly(List<LinkSnapshot> snapshots, LinkSnapshot target) {
        List<LinkSnapshot> results = new ArrayList<>();
        for(LinkSnapshot link : Util.safeIterable(snapshots)) {
            if (Differencer.objectsEqual(link.getApplicationName(), target.getApplicationName(), true)) {
                results.add(link);
            }
        }
        return results;
    }

    /**
     * Finds the permission in the list matching p1. Permissions will be matched with decreasing specificity:
     *
     *  1) [Target, Rights, Annotation]
     *  2) [Target, Rights]
     *  3) [Target]
     *
     * Matching will be done using the {@link Sameness} class, meaning that list values will be compared independent of order (and possibly case).
     *
     * @param p1 The permission to match
     * @param afterPermissions The list from which permissions will be matched
     * @param ignoreCase If true, case will be ignored in comparison
     * @return The matching Permission object from the list, or null
     */
    private Permission findPermission(Permission p1, List<Permission> afterPermissions, boolean ignoreCase) {
        Optional<Permission> permission = Utilities.safeStream(afterPermissions).filter(p2 -> Sameness.isSame(p2.getTarget(), p1.getTarget(), ignoreCase)).filter(p2 -> Sameness.isSame(p2.getRightsList(), p1.getRightsList(), ignoreCase)).filter(p2 -> Util.nullSafeEq(p1.getAnnotation(), p2.getAnnotation())).findFirst();
        if (permission.isPresent()) {
            return permission.get();
        }
        permission = Utilities.safeStream(afterPermissions).filter(p2 -> Sameness.isSame(p2.getTarget(), p1.getTarget(), ignoreCase)).filter(p2 -> Sameness.isSame(p2.getRightsList(), p1.getRightsList(), ignoreCase)).findFirst();
        if (permission.isPresent()) {
            return permission.get();
        }
        permission = Utilities.safeStream(afterPermissions).filter(p2 -> Sameness.isSame(p2.getTarget(), p1.getTarget(), ignoreCase)).findFirst();
        return permission.orElse(null);
    }

    @SuppressWarnings("unchecked")
    private List<Permission> getPermissions(LinkSnapshot link) {
        List<Permission> permissions = new ArrayList<>();
        if (link == null || link.getAttributes() == null) {
            return permissions;
        }
        Attributes<String, Object> attributes = link.getAttributes();
        if (attributes != null) {
            List<Permission> perms = (List<Permission>)attributes.get("directPermissions");
            if (perms != null) {
                permissions.addAll(perms);
            }

            perms = (List<Permission>)attributes.get("targetPermissions");
            if (perms != null) {
                permissions.addAll(perms);
            }
        }
        return permissions;
    }

    /**
     * This looks like a rename if we differ only in nativeIdentity or if the Link IDs match
     * @param ls1 The LinkSnapshot to check
     * @param ls2 The other LinkSnapshot to check
     * @return True if this looks like a rename
     */
    private boolean looksLikeRename(LinkSnapshot ls1, LinkSnapshot ls2) {
        if (Differencer.objectsEqual(ls1.getId(), ls2.getId(), false)) {
            return true;
        }
        if (!Differencer.objectsEqual(ls1.getNativeIdentity(), ls2.getNativeIdentity(), true)) {
            return Difference.equal(ls1.getAttributes(), ls2.getAttributes());
        }
        return false;
    }

    private AttributeDefinition nullSafeObjectAttribute(Schema source, String name) {
        AttributeDefinition attr = source.getAttributeDefinition(name);
        if (attr == null) {
            attr = new AttributeDefinition();
        }
        return attr;
    }

    private ObjectAttribute nullSafeObjectAttribute(ObjectConfig source, String name) {
        ObjectAttribute attr = source.getObjectAttribute(name);
        if (attr == null) {
            attr = new ObjectAttribute();
        }
        return attr;
    }

    /**
     * Creates a shallow copy of a possibly null list. If the list is null, an
     * empty list will be returned.
     *
     * @param source The source list to copy
     * @param <T> The type tag of the list
     * @return A shallow copy of the list, or an empty list if the original is null
     */
    private <T> List<T> safeCopy(List<T> source) {
        List<T> copy = new ArrayList<>();
        if (source != null) {
            copy.addAll(source);
        }
        return copy;
    }

    public void setGuessRenames(boolean guessRenames) {
        this.guessRenames = guessRenames;
    }

    public boolean shouldGuessRenames() {
        return guessRenames;
    }
}
