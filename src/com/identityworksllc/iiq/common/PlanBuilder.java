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

    public static PlanModifier account(AccountRequestModifier... modifiers) {
        return (plan) -> {
            ProvisioningPlan.AccountRequest accountRequest = new ProvisioningPlan.AccountRequest();
            for(AccountRequestModifier modifier : safeIterable(modifiers)) {
                modifier.modifyAccountRequest(accountRequest);
            }
            plan.add(accountRequest);
        };
    }

    public static AccountRequestModifier application(String application) {
        return (ar) -> ar.setApplication(application);
    }

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

    private static <T extends Enum<T>> T findValue(Class<T> enumClass, String value) {
        EnumSet<T> values = EnumSet.allOf(enumClass);
        for(T val : values) {
            if (Util.nullSafeCaseInsensitiveEq(val.name(), value)) {
                return val;
            }
        }
        throw new IllegalArgumentException("No such enum value in " + enumClass.getName() + ": " + value);
    }

    public static PlanModifier identity(Identity identity) {
        return (plan) -> plan.setIdentity(identity);
    }

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

    public static AccountRequestModifier nativeIdentity(String ni) {
        return (ar) -> ar.setNativeIdentity(ni);
    }

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

    public static AttributeRequestModifier operation(ProvisioningPlan.Operation operation) {
        return (at) -> at.setOperation(operation);
    }

    public static AccountRequestModifier operation(ProvisioningPlan.AccountRequest.Operation operation) {
        return (ar) -> ar.setOperation(operation);
    }

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

    public static ProvisioningPlan plan(PlanModifier... planModifiers) throws GeneralException {
        ProvisioningPlan plan = new ProvisioningPlan();
        for(PlanModifier modifier : safeIterable(planModifiers)) {
            modifier.modifyPlan(plan);
        }
        return plan;
    }

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
     * Internal utility to create a safe Iterable object from the array
     * which may be null or empty.
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
