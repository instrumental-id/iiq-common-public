package com.identityworksllc.iiq.common.password;

import sailpoint.api.passwordConstraints.PasswordConstraint;

/**
 * An extended password constraint interface that includes a method to get a description
 */
public interface ExtendedPasswordConstraint extends PasswordConstraint {
    /**
     * Returns a description of the password constraint, used by the UI to display
     * the constraint to the user.
     *
     * @return A description of the password constraint
     */
    String getDescription();

    /**
     * Configures the admin flag for this constraint. This may be used to skip a constraint
     * if the admin is changing the password for a user.
     *
     * @param isAdmin true if the current check is being done by an admin, false otherwise.
     */
    void setAdmin(boolean isAdmin);
}
