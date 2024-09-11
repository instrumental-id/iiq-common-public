package com.identityworksllc.iiq.common;

import com.identityworksllc.iiq.common.annotation.InProgress;
import sailpoint.api.Formicator;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Attributes;
import sailpoint.object.Field;
import sailpoint.object.Form;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.Schema;
import sailpoint.tools.GeneralException;
import sailpoint.tools.MapUtil;
import sailpoint.tools.Util;

import java.util.*;

/**
 * Utilities for ManagedAttributes / entitlement objects
 */
@InProgress
public class ManagedAttributeUtilities extends AbstractBaseUtility {

    /**
     * Namespace for arguments that can be passed to {@link #buildForm(Map, Option...)}.
     */
    public static final class BuildForm {
        /**
         * The interface used to cluster options that can be passed to {@link #buildForm(Map, Option...)}
         */
        public interface Option {}

        /**
         * Yes/no flags as form options
         */
        public enum Flags implements BuildForm.Option {
            RequireComments,
            AllowExit,
            AllowCancel
        }

        /**
         * A button option for forms
         */
        public static class Button implements BuildForm.Option {
            /**
             * Builds an {@link Option} indicating that a next Button will be
             * added to the form with the given Label.
             *
             * @param label The label
             * @return The button option
             */
            public static BuildForm.Option next(String label) {
                return new Button(Form.ACTION_NEXT, label);
            }

            /**
             * Builds an {@link Option} indicating that a next Button will be
             * added to the form with the given Label.
             *
             * @param label The label
             * @param argumentName The variable to set when this button is clicked
             * @param argumentValue The value to set in the variable when this button is clicked
             * @return The button option
             */
            public static BuildForm.Option next(String label, String argumentName, String argumentValue) {
                return new Button(Form.ACTION_NEXT, label, argumentName, argumentValue);
            }

            /**
             * The action for this button
             */
            final String action;

            /**
             * The label for this button
             */
            final String label;

            /**
             * The argument set by this button
             */
            final String argumentName;

            /**
             * The value set to the argument set by this button
             */
            final String argumentValue;

            private Button(String action, String label) {
                this(action, label, null, null);
            }

            private Button(String action, String label, String argumentName, String argumentValue) {
                this.action = action;
                this.label = label;
                this.argumentName = argumentName;
                this.argumentValue = argumentValue;
            }
        }
    }

    /**
     * Constructs a new utility object
     * @param context The IIQ context
     */
    public ManagedAttributeUtilities(SailPointContext context) {
        super(context);
    }

    /**
     * Builds a form for the given ManagedAttribute based on the input Map. It
     * will be derived from the existing Update or Create forms on the relevant
     * Application.
     *
     * The input must be a {@link Map} containing metadata under the key `sys`,
     * similar to the Maps produced for Form Models by {@link sailpoint.transformer.IdentityTransformer}.
     * The `sys` map should contain these keys:
     *
     *  - `nativeIdentity`: The native ID of the entitlement, if we are updating and not creating it
     *  - `application`: The application name
     *  - `type`: The schema name (e.g., _group_); will be used to look up the existing form
     *
     * You can build these maps with {@link #getCreateManagedAttributeMap(String, String)} and
     * {@link #getUpdateManagedAttributeMap(ManagedAttribute)}.
     *
     * The resulting Form may be used in a workflow.
     *
     * @param map
     * @param options
     * @return
     * @throws GeneralException
     */
    public Form buildForm(Map<String, Object> map, BuildForm.Option... options) throws GeneralException {
        Form form = new Form();

        boolean edit = Util.isNullOrEmpty((String) MapUtil.get(map, "sys.nativeIdentity"));

        String appName = Util.otoa(MapUtil.get(map, "sys.application"));
        String type = Util.otoa(MapUtil.get(map, "sys.type"));

        Application application = context.getObject(Application.class, appName);

        form.setPageTitle(edit ? "Update entitlement" : "Create entitlement");
        form.setBasePath("formModel");

        Form existingForm = application.getProvisioningForm(edit ? Form.Type.Update : Form.Type.Create, type);
        Formicator formicator = new Formicator(context);

        form.setType(existingForm.getType());
        form.setTitle(existingForm.getTitle());
        form.setObjectType(existingForm.getObjectType());
        form.setOwnerDefinition(existingForm.getOwnerDefinition());
        form.setMergeProfiles(existingForm.isMergeProfiles());
        form.setIncludeHiddenFields(existingForm.isIncludeHiddenFields());
        form.setApplication(application);

        List<Form.Section> sections = existingForm.getSections();

        for(Form.Section section : Util.safeIterable(sections)) {
            Form.Section copied = (Form.Section) section.deepCopy(context);
            form.add(copied);
        }

        for(BuildForm.Option option : options) {
            if (option == BuildForm.Flags.RequireComments) {
                Field commentsField = new Field();
                commentsField.setName("sys.comments");
                commentsField.setType("string");
                commentsField.setRequired(true);
                commentsField.setSection("__comments");

                formicator.assemble(form, Collections.singletonList(commentsField));
            } else if (option == BuildForm.Flags.AllowExit) {
                Form.Button exitButton = new Form.Button();
                exitButton.setAction(Form.ACTION_NEXT);
                exitButton.setLabel("Exit Workflow");
                exitButton.setParameter("exitWorkflow");
                exitButton.setValue("true");
                exitButton.setSkipValidation(true);

                form.add(exitButton);
            } else if (option == BuildForm.Flags.AllowCancel) {
                Form.Button cancelButton = new Form.Button();
                cancelButton.setAction(Form.ACTION_CANCEL);
                cancelButton.setLabel("Cancel");
                cancelButton.setSkipValidation(true);

                form.add(cancelButton);
            } else if (option instanceof BuildForm.Button) {
                BuildForm.Button config = (BuildForm.Button) option;
                Form.Button newButton = new Form.Button();
                newButton.setAction(config.action);
                newButton.setLabel(config.label);
                if (Util.isNotNullOrEmpty(config.argumentName)) {
                    newButton.setParameter(config.argumentName);
                }
                if (Util.isNotNullOrEmpty(config.argumentValue)) {
                    newButton.setValue(config.argumentValue);
                }

                form.add(newButton);
            }
        }

        Form.Button nextButton = new Form.Button();
        nextButton.setAction("next");
        nextButton.setLabel(edit ? "Update" : "Create");

        return form;
    }

    /**
     * Creates a new Map to pass into {@link #buildForm(Map, BuildForm.Option...)}
     *
     * @param applicationName The application name
     * @param attribute The attribute on that application's account schema
     * @return A map representation of a new entitlement
     * @throws GeneralException if anything goes wrong
     * @throws IllegalArgumentException if the inputs are bad
     */
    public Map<String, Object> getCreateManagedAttributeMap(String applicationName, String attribute) throws GeneralException {
        Application application = context.getObject(Application.class, applicationName);
        if (application == null) {
            throw new IllegalArgumentException("No such application: " + applicationName);
        }

        Schema accountSchema = application.getAccountSchema();
        if (accountSchema == null) {
            throw new IllegalArgumentException("Application " + applicationName + " has no account schema??");
        }

        AttributeDefinition attrDef = accountSchema.getAttributeDefinition(attribute);

        String attrSchemaName = attrDef.getSchemaObjectType();

        Schema schema = application.getSchema(attrSchemaName);
        if (schema == null) {
            throw new IllegalArgumentException("Attribute " + attribute + " specifies non-existent schema: " + attrSchemaName);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("attribute", attrDef.getName());
        result.put("value", "");
        result.put("displayName", "");

        MapUtil.put(result, "sys.nativeIdentity", null);
        MapUtil.put(result, "sys.application", application.getId());
        MapUtil.put(result, "sys.type", schema.getName());
        MapUtil.put(result, "sys.attribute", attrDef.getName());

        return result;
    }

    /**
     * Creates a new Map to pass into {@link #buildForm(Map, BuildForm.Option...)}
     *
     * @param ma The {@link ManagedAttribute} being modified
     * @return A map representation of a new entitlement
     * @throws GeneralException if anything goes wrong
     * @throws IllegalArgumentException if the inputs are bad
     */
    public Map<String, Object> getUpdateManagedAttributeMap(ManagedAttribute ma) throws GeneralException {
        if (ma == null) {
            throw new IllegalArgumentException("Cannot provide a null managed attribute");
        }

        Application application = ma.getApplication();
        Schema schema = application.getSchema(ma.getType());

        Map<String, Object> result = new HashMap<>();

        result.put("attribute", ma.getAttribute());
        result.put("value", ma.getValue());
        result.put("displayName", ma.getDisplayName());

        for(String key : schema.getAttributeNames()) {
            Object existing = ma.getAttribute(key);
            if (Utilities.isSomething(existing)) {
                result.put(key, existing);
            }
        }

        MapUtil.put(result, "sys.id", ma.getId());
        MapUtil.put(result, "sys.nativeIdentity", null);
        MapUtil.put(result, "sys.application", application.getId());
        MapUtil.put(result, "sys.type", schema.getName());
        MapUtil.put(result, "sys.attribute", ma.getAttribute());

        return result;
    }
}
