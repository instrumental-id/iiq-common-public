package com.identityworksllc.iiq.common;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.SailPointContext;
import sailpoint.object.ApprovalItem;
import sailpoint.object.ApprovalSet;
import sailpoint.object.Comment;
import sailpoint.object.Filter;
import sailpoint.object.Workflow;
import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Utility class for dealing with Approvals, ApprovalSets, and ApprovalItems
 */
public class AccessRequestUtilities extends AbstractBaseUtility {
    public static final String DEFAULT_APPROVER = "spadmin";
    public static final String VAR_APPROVAL_SET = "approvalSet";
    public static final String VAR_TEMP_APPROVAL_SET = "_tempSplitApprovalSet";
    private static final Log log = LogFactory.getLog(AccessRequestUtilities.class);

    public AccessRequestUtilities(SailPointContext context) {
        super(context);
    }

    public void autoApprove(ApprovalSet set, Predicate<ApprovalItem> approvalFilter, String comment) {
        if (set != null) {
            for(ApprovalItem item : Util.safeIterable(set.getItems())) {
                if (approvalFilter == null || approvalFilter.test(item)) {
                    item.approve();
                    item.setApprover(DEFAULT_APPROVER);
                    if (Util.isNotNullOrEmpty(comment)) {
                        item.add(new Comment(comment, DEFAULT_APPROVER));
                    }
                }
            }
        }
    }

    public void divideApprovalSet(Workflow workflow, Filter excludedItemMatcher) {
        divideApprovalSet(workflow, Functions.matches(excludedItemMatcher, ApprovalItem.class));
    }

    public void divideApprovalSet(Workflow workflow, Predicate<ApprovalItem> excludedItemMatcher) {
        ApprovalSet inputSet = (ApprovalSet) workflow.get(VAR_APPROVAL_SET);
        if (inputSet == null) {
            log.debug("divideApprovalSet() called with null approval set");
            return;
        }

        List<ApprovalItem> removedItems = new ArrayList<>();
        for(ApprovalItem item : Util.safeIterable(inputSet.getItems())) {
            if (excludedItemMatcher != null && excludedItemMatcher.test(item)) {
                removedItems.add(item);
            }
        }

        if (!Util.isEmpty(removedItems)) {
            ApprovalSet temporarySet = new ApprovalSet();
            temporarySet.setItems(removedItems);

            workflow.put(VAR_TEMP_APPROVAL_SET, temporarySet);

            for(ApprovalItem item : removedItems) {
                inputSet.remove(item);
            }
        } else {
            workflow.put(VAR_TEMP_APPROVAL_SET, null);
        }
    }

    public void mergeDividedApprovalSet(ApprovalSet approvalSet, ApprovalSet temporarySet, boolean autoApprove, String comment) {
        if (temporarySet == null || temporarySet.isEmpty()) {
            log.debug("Called mergeDividedApprovalSet() with a null or empty temporary set");
        } else {
            for (ApprovalItem item : Util.safeIterable(temporarySet.getItems())) {
                if (!item.isRejected()) {
                    if (autoApprove) {
                        item.approve();
                        if (Util.isNotNullOrEmpty(comment)) {
                            item.add(new Comment(comment, DEFAULT_APPROVER));
                        }
                        item.setApprover(DEFAULT_APPROVER);
                    } else {
                        if (Util.isNotNullOrEmpty(comment)) {
                            item.add(new Comment(comment, DEFAULT_APPROVER));
                        }
                    }
                }
                approvalSet.add(item);
            }
        }
    }

    public void mergeDividedApprovalSet(ApprovalSet approvalSet, ApprovalSet temporarySet) {
        mergeDividedApprovalSet(approvalSet, temporarySet, true, null);
    }

    public void mergeDividedApprovalSet(ApprovalSet approvalSet, ApprovalSet temporarySet, boolean autoApprove) {
        mergeDividedApprovalSet(approvalSet, temporarySet, autoApprove, null);
    }
}
