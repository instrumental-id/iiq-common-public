package com.identityworksllc.iiq.common.context;

import java.util.Objects;

/**
 * A simple context class to hold information about an action being performed. This
 * can be used in conjunction with ScopedValues to provide a way to store and access key
 * information about a REST API call or task action throughout the codebase without
 * needing to pass state around through method calls.
 *
 * The fields are intentionally generic and can be used to store whatever information
 * your application needs. The acitonKey and actionName fields are intended to be a
 * system-friendly name and a more human-friendly name, but they can be used however
 * you would like.
 *
 * ActionContext should be constructed only via its Builder class, accessible via {@link #builder()}.
 */
public class ActionContext {
    /**
     * A builder class for constructing instances of ActionContext
     */
    public static final class Builder {
        private String actionKey;
        private String actionName;
        private String source;
        private String target;

        /**
         * Sets the actionKey field in this Builder
         * @param actionKey The action key
         * @return this Builder instance for method chaining
         */
        public Builder actionKey(String actionKey) {
            this.actionKey = actionKey;
            return this;
        }

        /**
         * Sets the acitonName field in this Builder
         * @param actionName The action name
         * @return this Builder instance for method chaining
         */
        public Builder actionName(String actionName) {
            this.actionName = actionName;
            return this;
        }

        /**
         * Returns an immutable instance of ActionContext with the values set in this Builder.
         * @return a new ActionContext instance with the values set in this Builder
         */
        public ActionContext build() {
            return new ActionContext(actionKey, actionName, source, target);
        }

        /**
         * Sets the source field in this Builder
         * @param source The source of the action, such as the identity performing the action or the class/method where the action is being performed
         * @return this Builder instance for method chaining
         */
        public Builder source(String source) {
            this.source = source;
            return this;
        }

        /**
         * Sets the target field in this Builder
         * @param target The target of the action, such as the identity being acted on or the object being modified
         * @return this Builder instance for method chaining
         */
        public Builder target(String target) {
            this.target = target;
            return this;
        }
    }

    /**
     * Returns an instanceof ActionContext.Builder, which is used to construct an immutable
     * instance of ActionContext.
     *
     * @return a new Builder instance for constructing an ActionContext
     */
    public static Builder builder() {
        return new Builder();
    }
    /**
     * A key to identify the type of action being performed. For example, this might be the
     * REST API endpoint or task name being executed.
     */
    private final String actionKey;

    /**
     * A more descriptive action name of some sort
     */
    private final String actionName;

    /**
     * The source of the action, such as the identity performing the action or the class/method where the action is being performed
     */
    private final String source;

    /**
     * The target of the action, such as the identity being acted on or the object being modified
     */
    private final String target;

    /**
     * Private constructor for ActionContext. Instances should be constructed via the Builder.
     * @param actionKey a key to identify the type of action being performed
     * @param actionName an identifier of some sort (e.g., a descriptive name, a class name, etc) of the action being performed
     * @param source the source of the action, such as the identity performing the action or the class/method where the action is being performed
     * @param target the target of the action, such as the identity being acted on or the object being modified
     */
    private ActionContext(String actionKey, String actionName, String source, String target) {
        this.actionKey = actionKey;
        this.actionName = actionName;
        this.source = source;
        this.target = target;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ActionContext that = (ActionContext) o;

        return Objects.equals(this.actionKey, that.actionKey) &&
               Objects.equals(this.actionName, that.actionName) &&
               Objects.equals(this.source, that.source) &&
               Objects.equals(this.target, that.target);
    }

    /**
     * Returns whatever you have set for actionKey for this ActionContext
     * @return the action key for this ActionContext
     */
    public String getActionKey() {
        return actionKey;
    }

    /**
     * Returns whatever you have set for actionName for this ActionContext
     * @return the action name for this ActionContext
     */
    public String getActionName() {
        return actionName;
    }

    /**
     * Returns the source of the action, such as the identity performing the action or the class/method where the action is being performed.
     * @return the source of the action
     */
    public String getSource() {
        return source;
    }

    /**
     * Returns the target of the action, such as the identity being acted on or the object being modified.
     * @return the target of the action
     */
    public String getTarget() {
        return target;
    }

    /**
     * Returns a hash code value for this ActionContext, based on all fields.
     * @return a hash code value for this ActionContext
     */
    @Override
    public int hashCode() {
        return Objects.hash(actionKey, actionName, source, target);
    }

    /**
     * Returns a string representation of this ActionContext, including all fields.
     * @return a string representation of this ActionContext
     */
    public String toString() {
        return "ActionContext{" +
               "actionKey='" + actionKey + '\'' +
               ", actionName='" + actionName + '\'' +
               ", source='" + source + '\'' +
               ", target='" + target + '\'' +
               '}';
    }
}
