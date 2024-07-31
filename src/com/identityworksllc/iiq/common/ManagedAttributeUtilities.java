package com.identityworksllc.iiq.common;

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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ManagedAttributeUtilities extends AbstractBaseUtility {
    public enum BuildFormOption {
        RequireComments,
        AllowExit,
        AllowCancel
    }
    public ManagedAttributeUtilities(SailPointContext context) {
        super(context);
    }

    public Form buildForm(Map<String, Object> map, BuildFormOption... options) throws GeneralException {
        Form form = new Form();

        boolean edit = Util.isNullOrEmpty((String) MapUtil.get(map, "sys.nativeIdentity"));

        String appName = Util.otoa(MapUtil.get(map, "sys.application"));
        String type = Util.otoa(MapUtil.get(map, "sys.type"));

        Application application = context.getObject(Application.class, appName);

        form.setPageTitle(edit ? "Update entitlement" : "Create entitlement");
        form.setBasePath("formModel");

        Form existingForm = application.getProvisioningForm(edit ? Form.Type.Update : Form.Type.Create, type);
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

        Formicator formicator = new Formicator(context);

        for(BuildFormOption option : options) {
            if (option == BuildFormOption.RequireComments) {
                Field commentsField = new Field();
                commentsField.setName("sys.comments");
                commentsField.setType("string");
                commentsField.setRequired(true);
                commentsField.setSection("__comments");

                formicator.assemble(form, List.of(commentsField));
            } else if (option == BuildFormOption.AllowExit) {
                Form.Button exitButton = new Form.Button();
                exitButton.setAction("next");
                exitButton.setLabel("Exit Workflow");
                exitButton.setParameter("exitWorkflow");
                exitButton.setValue("true");
                exitButton.setSkipValidation(true);

                form.add(exitButton);
            } else if (option == BuildFormOption.AllowCancel) {
                Form.Button cancelButton = new Form.Button();
                cancelButton.setAction("cancel");
                cancelButton.setLabel("Cancel");

                form.add(cancelButton);
            }
        }

        Form.Button nextButton = new Form.Button();
        nextButton.setAction("next");
        nextButton.setLabel(edit ? "Update" : "Create");

        return form;
    }

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
