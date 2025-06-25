package com.identityworksllc.iiq.common.password;

import sailpoint.api.passwordConstraints.AbstractPasswordConstraint;

/**
 * An abstract class for extended password constraints, which mainly exists to provide
 * a default for the {@link #generate()} method and the admin flag functionality.
 */
public abstract class AbstractExtendedPasswordConstraint extends AbstractPasswordConstraint implements ExtendedPasswordConstraint {

    /**
     * Indicates whether the current check is being done by an admin.
     * If true, the constraint may be skipped.
     */
    protected boolean isAdmin = false;

    /**
     * Sets the admin flag for this constraint.
     * @param isAdmin true if the current check is being done by an admin, false otherwise.
     */
    @Override
    public void setAdmin(boolean isAdmin) {
        this.isAdmin = isAdmin;
    }
}
