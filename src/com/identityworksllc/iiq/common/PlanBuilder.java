package com.identityworksllc.iiq.common;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.Attributes;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.ProvisioningPlan;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A fluent builder for provisioning plans, especially useful for quickly writing
 * rules or test cases. Import the methods from this class statically and begin with
 * one of the plan() methods. The builder model will enforce only calling appropriate
 * operations in any given context.
 *
 * The interfaces nested within this class will be used to implement a chain of
 * modifiers to the 'current' object.
 *
 * Example:
 *
 * ```
 * ProvisioningPlan plan =
 *   PlanBuilder.plan(existingLink,
 *     removeAllValues(existingLink, "emails"),
 *     attribute("firstName", set("John")));
 * ```
 *
 * This results in a ProvisioningPlan containing an AccountRequest to modify the
 * given account. The AccountRequest contains AttributeRequests to remove all
 * existing values from 'email' and to set the 'firstName' attribute to 'John'.
 */
public class PlanBuilder {

    /**
     * Interface for modifying existing links, can be passed to the Link plan() method
     * for better fluency
     */
    public interface ExistingLinkModifier {}

    /**
     * Can modify the given provisioning plan
     */
    public interface PlanModifier extends ExistingLinkModifier {
        /**
         * Modifies the given plan
         * @param plan The plan to modify
         * @throws GeneralException if anything goes wrong
         */
        void modifyPlan(ProvisioningPlan plan) throws GeneralException;
    }

    /**
     * Can modify the given account request
     */
    public interface AccountRequestModifier extends ExistingLinkModifier {
        void modifyAccountRequest(ProvisioningPlan.AccountRequest accountRequest);
    }

    /**
     * Can modify the given attribute request
     */
    public interface AttributeRequestModifier {
        void modifyAttributeRequest(ProvisioningPlan.AttributeRequest attributeRequest);
    }

    /**
     * Can be used in any context
     */
    public interface AnyModifier extends PlanModifier, AccountRequestModifier, AttributeRequestModifier {

    }

    /**
     * Handles the case of a string parameter to operation()
     */
    public interface OperationModifier extends AccountRequestModifier, AttributeRequestModifier {

    }

    /**
     * returns a plan modifier that adds an account request to the plan
     * @param modifiers Any modifiers to apply to the account request
     * @return The plan modifier
     */
    public static PlanModifier account(AccountRequestModifier... modifiers) {
        return (plan) -> {
            ProvisioningPlan.AccountRequest accountRequest = new ProvisioningPlan.AccountRequest();
            for(AccountRequestModifier modifier : safeIterable(modifiers)) {
                modifier.modifyAccountRequest(accountRequest);
            }
            plan.add(accountRequest);
        };
    }

    /**
     * Returns an AccountRequestModifier that sets the application on the account request
     * @param application The application name
     * @return The AccountRequestModifier
     */
    public static AccountRequestModifier application(String application) {
        return (ar) -> ar.setApplication(application);
    }

    /**
     * Returns a modifier for any object that adds an argument to the object's `arguments` Map.
     * This method works on plans, requests, and attributes.
     *
     * @param name The argument name
     * @param value The argument value
     * @return The modifier
     */
    public static AnyModifier argument(String name, Object value) {
        return new AnyModifier() {
            @Override
            public void modifyAccountRequest(ProvisioningPlan.AccountRequest accountRequest) {
                setArgument(accountRequest::getArguments, accountRequest::setArguments, name, value);
            }

            @Override
            public void modifyAttributeRequest(ProvisioningPlan.AttributeRequest attributeRequest) {
                setArgument(attributeRequest::getArguments, attributeRequest::setArguments, name, value);
            }

            @Override
            public void modifyPlan(ProvisioningPlan plan) throws GeneralException {
                setArgument(plan::getArguments, plan::setArguments, name, value);
            }
        };
    }

    /**
     * Creates a modifier to add an AttributeRequest to the AccountRequest
     * @param name The name of the attribute
     * @param modifiers Any modifiers to apply to the attribute request
     * @return the resulting modifier implementation
     */
    public static AccountRequestModifier attribute(String name, AttributeRequestModifier... modifiers) {
        return (ar) -> {
            ProvisioningPlan.AttributeRequest attributeRequest = new ProvisioningPlan.AttributeRequest();
            attributeRequest.setName(name);

            for(AttributeRequestModifier modifier : safeIterable(modifiers)) {
                modifier.modifyAttributeRequest(attributeRequest);
            }

            ar.add(attributeRequest);
        };
    }

    /**
     * Locates an Enum constant case-insensitively by name and returns it
     * @param enumClass The enum class
     * @param value The value to find
     * @return The matching enum constant
     * @param <T> The enum type
     * @throws IllegalArgumentException if no matching constant is found
     */
    private static <T extends Enum<T>> T findValue(Class<T> enumClass, String value) {
        EnumSet<T> values = EnumSet.allOf(enumClass);
        for(T val : values) {
            if (Util.nullSafeCaseInsensitiveEq(val.name(), value)) {
                return val;
            }
        }
        throw new IllegalArgumentException("No such enum value in " + enumClass.getName() + ": " + value);
    }

    /**
     * Sets the identity on the plan to the given identity
     * @param identity The identity to set
     * @return A plan modifier that changes the plan Identity
     */
    public static PlanModifier identity(Identity identity) {
        return (plan) -> plan.setIdentity(identity);
    }

    /**
     * Creates a plan modifier that sets the identity on the plan to the identity with the given name or ID
     * @param idOrName The identity name or ID
     * @return The plan modifier
     * @throws GeneralException if the identity cannot be found
     */
    public static PlanModifier identity(String idOrName) throws GeneralException {
        SailPointContext context = SailPointFactory.getCurrentContext();
        Identity identity = context.getObject(Identity.class, idOrName);
        return (plan) -> plan.setIdentity(identity);
    }

    /**
     * Merges the new value into the list of values in the attribute request
     * @param attributeRequest The attribute request to modify
     * @param value The new value
     */
    private static void mergeValue(ProvisioningPlan.AttributeRequest attributeRequest, Object value) {
        List<Object> values = new ArrayList<>();
        Object existingValue = attributeRequest.getValue();
        if (existingValue instanceof Collection) {
            values.addAll((Collection<?>) existingValue);
        } else if (existingValue != null) {
            values.add(existingValue);
        }
        if (value instanceof Collection) {
            values.addAll((Collection<?>)value);
        } else {
            values.add(value);
        }
        attributeRequest.setValue(values);
    }

    /**
     * Creates an AccountRequestModifier that sets the native identity on the account request
     * @param ni The native identity to set
     * @return The AccountRequestModifier
     */
    public static AccountRequestModifier nativeIdentity(String ni) {
        return (ar) -> ar.setNativeIdentity(ni);
    }

    /**
     * Creates an OperationModifier that sets the operation on the account request or attribute request
     * @param op The operation to set, as a string
     * @return The OperationModifier
     */
    public static OperationModifier operation(String op) {
        return new OperationModifier() {
            @Override
            public void modifyAccountRequest(ProvisioningPlan.AccountRequest accountRequest) {
                ProvisioningPlan.AccountRequest.Operation operation = findValue(ProvisioningPlan.AccountRequest.Operation.class, op);
                accountRequest.setOperation(operation);
            }

            @Override
            public void modifyAttributeRequest(ProvisioningPlan.AttributeRequest attributeRequest) {
                ProvisioningPlan.Operation operation = findValue(ProvisioningPlan.Operation.class, op);
                attributeRequest.setOperation(operation);
            }
        };
    }

    /**
     * Creates an AttributeRequestModifier that sets the operation on the attribute request
     * @param operation The operation to set
     * @return The AttributeRequestModifier
     */
    public static AttributeRequestModifier operation(ProvisioningPlan.Operation operation) {
        return (at) -> at.setOperation(operation);
    }

    /**
     * Creates an AccountRequestModifier that sets the operation on the account request
     * @param operation The operation to set
     * @return The AccountRequestModifier
     */
    public static AccountRequestModifier operation(ProvisioningPlan.AccountRequest.Operation operation) {
        return (ar) -> ar.setOperation(operation);
    }

    /**
     * Creates a provisioning plan based on the given link and any modifiers.
     * This method is one of the entry points to the PlanBuilder.
     *
     * @param toModify The link to modify
     * @param modifiers Any modifiers to apply to the plan or account request
     * @return The resulting plan
     * @throws GeneralException if anything goes wrong
     */
    public static ProvisioningPlan plan(Link toModify, ExistingLinkModifier... modifiers) throws GeneralException {
        ProvisioningPlan plan = new ProvisioningPlan();
        if (toModify.getIdentity() != null) {
            plan.setIdentity(toModify.getIdentity());
        } else {
            SailPointContext context = SailPointFactory.getCurrentContext();
            String identityName = ObjectUtil.getIdentityFromLink(context, toModify.getApplication(), toModify.getInstance(), toModify.getNativeIdentity());
            Identity identity = context.getObject(Identity.class, identityName);
            plan.setIdentity(identity);
        }

        ProvisioningPlan.AccountRequest request = new ProvisioningPlan.AccountRequest();
        request.setApplication(toModify.getApplicationName());
        request.setNativeIdentity(toModify.getNativeIdentity());

        for(ExistingLinkModifier modifier : safeIterable(modifiers)) {
            if (modifier instanceof PlanModifier) {
                ((PlanModifier) modifier).modifyPlan(plan);
            } else if (modifier instanceof AccountRequestModifier) {
                ((AccountRequestModifier) modifier).modifyAccountRequest(request);
            }
        }

        plan.add(request);

        return plan;
    }

    /**
     * Creates a provisioning plan based on the given modifiers.
     * This method is one of the entry points to the PlanBuilder.
     *
     * @param planModifiers Any modifiers to apply to the plan
     * @return The resulting plan
     * @throws GeneralException if anything goes wrong
     */
    public static ProvisioningPlan plan(PlanModifier... planModifiers) throws GeneralException {
        ProvisioningPlan plan = new ProvisioningPlan();
        for(PlanModifier modifier : safeIterable(planModifiers)) {
            modifier.modifyPlan(plan);
        }
        return plan;
    }

    /**
     * Generates a complex AccountRequestModifier that removes all existing values from the given field
     * @param existing The Link from which to extract existing values
     * @param field The field to remove values from
     * @return The AccountRequestModifier
     */
    public static AccountRequestModifier removeAllValues(Link existing, String field) {
        return (ar) -> {
            if (existing == null || existing.getAttributes() == null) {
                return;
            }
            ProvisioningPlan.AttributeRequest attributeRequest = ar.getAttributeRequest(field);
            if (attributeRequest == null) {
                attributeRequest = new ProvisioningPlan.AttributeRequest();
                attributeRequest.setName(field);
                attributeRequest.setOperation(ProvisioningPlan.Operation.Remove);
            }
            List<String> existingValues = existing.getAttributes().getStringList(field);
            mergeValue(attributeRequest, existingValues);
        };
    }

    /**
     * Internal utility to create a safe Iterable object from an array
     * which may be null or empty.
     *
     * @param array The array, which may be null
     * @param <T> The type of the array
     * @return A non-null Iterable object for use in a for loop
     */
    private static <T> Iterable<T> safeIterable(T[] array) {
        if (array == null || array.length == 0) {
            return new ArrayList<>();
        }
        return Arrays.asList(array);
    }

    /**
     * Shortcut for 'operation("Set"), value(value)'
     * @param value The value to set in the attribute request
     * @return The AttributeRequestModifier
     */
    public static AttributeRequestModifier set(Object value) {
        return (at) -> {
            at.setOperation(ProvisioningPlan.Operation.Set);
            if (value instanceof Identity) {
                at.setValue(((Identity) value).getName());
            } else {
                at.setValue(value);
            }
        };
    }

    /**
     * Utility method to retrieve the arguments from the given object, add the
     * argument specified to the arguments, then put the arguments back. This
     * generically handles the case where the arguments do not yet exist.
     *
     * @param getArguments A reference to the object's getArguments
     * @param setArguments A reference to the object's setArguments
     * @param arg The argument to set
     * @param val The value of the argument
     */
    private static void setArgument(Supplier<Attributes<String, Object>> getArguments, Consumer<Attributes<String, Object>> setArguments, String arg, Object val) {
        Attributes<String, Object> attributes = getArguments.get();
        if (attributes == null) {
            attributes = new Attributes<>();
        }
        attributes.put(arg, val);
        setArguments.accept(attributes);
    }

    /**
     * Adds the given value(s) to the Plan. If only one value is passed, it
     * will be added as a single value. If value() is called more than once,
     * if more than one value is passed, or if the attribute request already
     * has a value, it will be transformed into a list and merged.
     *
     * @param values The values
     * @return The input
     */
    public static AttributeRequestModifier value(Object... values) {
        return (at) -> {
            if (values != null && values.length == 1 && at.getValue() == null) {
                at.setValue(values[0]);
            } else {
                for (Object val : safeIterable(values)) {
                    mergeValue(at, val);
                }
            }
        };
    }

}
