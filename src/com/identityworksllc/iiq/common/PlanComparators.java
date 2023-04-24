package com.identityworksllc.iiq.common;

import sailpoint.object.ProvisioningPlan;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

/**
 * Implements comparators to sort account requests within a provisioning plan. These can be used directly via the collections API or passed to {@link Plans#sort(ProvisioningPlan, Comparator)}.
 *
 * The comparators safely handle the case where two requests both have the property (e.g. both are enables), preserving existing ordering where the sort algorithm does so.
 *
 * Additionally, the "first" comparator methods will always retain Create requests at the start of the sequence.
 *
 * The reasoning behind creating this is the Salesforce connector. It executes the operations given blindly in the order they are received in the plan. However, certain operations must always precede others, resulting in a lot of code to handle this situation. Between this and the Plans class, that Rule could be cut down to a few lines.
 */
@SuppressWarnings("unused")
public class PlanComparators {

    /**
     * A map containing the default ordering for AccountRequests
     */
    private static final Map<ProvisioningPlan.AccountRequest.Operation, Integer> ACCOUNT_REQUEST_ORDERING = new HashMap<>();

    /**
     * A map containing the default ordering for AttributeRequests
     */
    private static final Map<ProvisioningPlan.Operation, Integer> ATTRIBUTE_REQUEST_ORDERING = new HashMap<>();

    /**
     * Performs a default sort of requests, implementing the sequence (Create, Enable, Unlock, Modify, Lock, Disable, Delete)
     * @return Sorter
     */
    public static Comparator<ProvisioningPlan.AttributeRequest> defaultAttributeSequence() {
        return Comparator.comparing(v -> ATTRIBUTE_REQUEST_ORDERING.get(v.getOperation()));
    }

    /**
     * Performs a default sort of requests, implementing the sequence (Create, Enable, Unlock, Modify, Lock, Disable, Delete)
     * @return Sorter
     */
    public static Comparator<ProvisioningPlan.AccountRequest> defaultSequence() {
        return Comparator.comparing(v -> {
            if (v.getOperation() == null) {
                return ACCOUNT_REQUEST_ORDERING.get(ProvisioningPlan.AccountRequest.Operation.Modify);
            } else {
                return ACCOUNT_REQUEST_ORDERING.get(v.getOperation());
            }
        });
    }

    /**
     * Moves disables to the start of the sequence, before any other operation except create
     * @return Sorter
     */
    public static Comparator<ProvisioningPlan.AccountRequest> disablesFirst() {
        return genericSort(ar -> {
            if (ar.getOperation().equals(ProvisioningPlan.AccountRequest.Operation.Create)) {
                // Lowest sort
                return -100;
            } else if (ar.getOperation().equals(ProvisioningPlan.AccountRequest.Operation.Disable)) {
                // Next lowest sort
                return -10;
            } else {
                // Don't care
                return 0;
            }
        });
    }

    /**
     * Moves disables to the end of the sequence
     * @return Sorter
     */
    public static Comparator<ProvisioningPlan.AccountRequest> disablesLast() {
        return genericSort(ar -> {
            if (ar.getOperation().equals(ProvisioningPlan.AccountRequest.Operation.Create)) {
                // Lowest sort
                return -100;
            } else if (ar.getOperation().equals(ProvisioningPlan.AccountRequest.Operation.Disable)) {
                // Highest sort
                return 100;
            } else {
                // Don't care
                return 0;
            }
        });
    }

    /**
     * Moves enables to the start of the sequence, before any other operation except create
     * @return Sorter
     */
    public static Comparator<ProvisioningPlan.AccountRequest> enablesFirst() {
        return genericSort(ar -> {
            if (ar.getOperation().equals(ProvisioningPlan.AccountRequest.Operation.Create)) {
                // Lowest sort
                return -100;
            } else if (ar.getOperation().equals(ProvisioningPlan.AccountRequest.Operation.Enable)) {
                // Next lowest sort
                return -10;
            } else {
                // Don't care
                return 0;
            }
        });
    }

    /**
     * Moves enables to the end of the sequence
     * @return Sorter
     */
    public static Comparator<ProvisioningPlan.AccountRequest> enablesLast() {
        return genericSort(ar -> {
            if (ar.getOperation().equals(ProvisioningPlan.AccountRequest.Operation.Create)) {
                // Lowest sort
                return -100;
            } else if (ar.getOperation().equals(ProvisioningPlan.AccountRequest.Operation.Enable)) {
                // Highest sort
                return 100;
            } else {
                // Don't care
                return 0;
            }
        });
    }

    /**
     * A generic sorter that will use a {@link ToIntFunction} to transform the given AccountRequest into an integer value, then sort those.
     * @param function The function to transform an AccountRequest into an appropriate integer
     * @return A comparator to sort AccountRequests by that
     */
    public static Comparator<ProvisioningPlan.AccountRequest> genericSort(final ToIntFunction<ProvisioningPlan.AccountRequest> function) {
        return (o1, o2) -> {
            int o1i = function.applyAsInt(o1);
            int o2i = function.applyAsInt(o2);

            return (o1i - o2i);
        };
    }

    /**
     * Moves an account request containing the given attribute request to the start of the sequence, before any other operation except create
     * @return Sorter
     */
    public static Comparator<ProvisioningPlan.AccountRequest> specificFirst(final String which) {
        return genericSort(ar -> {
            if (ar.getOperation().equals(ProvisioningPlan.AccountRequest.Operation.Create)) {
                // Lowest sort
                return -100;
            } else if (ar.getAttributeRequest(which) != null) {
                // Next lowest sort
                return -10;
            } else {
                // Don't care
                return 0;
            }
        });
    }

    /**
     * Moves any specific account requests containing the given attribute to the end of the sequence
     * @param which Which attribute to look for
     * @return Sorter
     */
    public static Comparator<ProvisioningPlan.AccountRequest> specificLast(final String which) {
        return genericSort(ar -> {
            if (ar.getOperation().equals(ProvisioningPlan.AccountRequest.Operation.Create)) {
                // Lowest sort
                return -100;
            } else if (ar.getAttributeRequest(which) != null) {
                // Highest sort
                return 100;
            } else {
                // Don't care
                return 0;
            }
        });
    }

    static {
        ACCOUNT_REQUEST_ORDERING.put(ProvisioningPlan.AccountRequest.Operation.Create, 0);
        ACCOUNT_REQUEST_ORDERING.put(ProvisioningPlan.AccountRequest.Operation.Enable, 1);
        ACCOUNT_REQUEST_ORDERING.put(ProvisioningPlan.AccountRequest.Operation.Unlock, 2);
        ACCOUNT_REQUEST_ORDERING.put(ProvisioningPlan.AccountRequest.Operation.Modify, 3);
        ACCOUNT_REQUEST_ORDERING.put(ProvisioningPlan.AccountRequest.Operation.Lock, 4);
        ACCOUNT_REQUEST_ORDERING.put(ProvisioningPlan.AccountRequest.Operation.Disable, 5);
        ACCOUNT_REQUEST_ORDERING.put(ProvisioningPlan.AccountRequest.Operation.Delete, 6);

        ATTRIBUTE_REQUEST_ORDERING.put(ProvisioningPlan.Operation.Remove, 0);
        ATTRIBUTE_REQUEST_ORDERING.put(ProvisioningPlan.Operation.Revoke, 0);
        ATTRIBUTE_REQUEST_ORDERING.put(ProvisioningPlan.Operation.Retain, 1);
        ATTRIBUTE_REQUEST_ORDERING.put(ProvisioningPlan.Operation.Set, 2);
        ATTRIBUTE_REQUEST_ORDERING.put(ProvisioningPlan.Operation.Add, 3);

    }

}
