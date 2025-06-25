package com.identityworksllc.iiq.common.password;

import sailpoint.api.passwordConstraints.PasswordConstraintAttribute;
import sailpoint.object.Attributes;
import sailpoint.object.Identity;
import sailpoint.tools.Util;

/**
 * Extends the OOTB PasswordConstraintAttribute to create a constraint for
 * a single identity attribute. (The existing one is far too broad and
 * will include attributes we really don't care about, like flags with
 * value of "true" and such.)
 */
public class IdentityAttributeConstraint extends PasswordConstraintAttribute implements ExtendedPasswordConstraint {

    /**
     * The name of the identity attribute that this constraint checks against.
     */
    private final String attributeName;

    /**
     * Constructs a new IdentityAttributeConstraint for the given identity and attribute name.
     * @param target The identity to check against
     * @param attributeName The name of the attribute to check against
     */
    public IdentityAttributeConstraint(Identity target, String attributeName) {
        super(buildAttributes(target, attributeName));

        this.attributeName = attributeName;
    }

    /**
     * Builds the attributes for the constraint based on the target identity and attribute name.
     * This is the value required for the superclass constructor.
     *
     * @param target The identity to check against
     * @param attributeName The name of the attribute to check against
     * @return An Attributes object containing the attribute name and its value
     */
    private static Attributes<String, Object> buildAttributes(Identity target, String attributeName) {
        Attributes<String, Object> attributes = new Attributes<>();
        String value = Util.otoa(target.getAttribute(attributeName));
        attributes.put(attributeName, value);
        return attributes;
    }

    @Override
    public String getDescription() {
        return "Password cannot contain the value of identity attribute: " + attributeName;
    }

    /**
     * Sets the admin flag for this constraint. In our case, this is a no-op, because we
     * want this constraint to still apply when an admin is changing the password.
     */
    @Override
    public void setAdmin(boolean isAdmin) {
        // Do nothing here
    }
}
