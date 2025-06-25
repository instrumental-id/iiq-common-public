package com.identityworksllc.iiq.common.password;

import com.identityworksllc.iiq.common.logging.SLogger;
import org.apache.commons.logging.Log;
import sailpoint.api.PasswordPolice;
import sailpoint.api.PasswordPolicyException;
import sailpoint.api.SailPointContext;
import sailpoint.api.passwordConstraints.PasswordConstraint;
import sailpoint.object.Identity;
import sailpoint.object.PasswordPolicy;
import sailpoint.tools.GeneralException;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * An extension of the OOTB PasswordPolice that allows adding custom constraints. Note that you
 * should NOT reuse an instance of this class to check two different users' passwords, as the
 * superclass does some odd stuff.
 */
public class ExtendedPasswordPolice extends PasswordPolice {
    /**
     * Logger
     */
    private static final SLogger log = new SLogger(ExtendedPasswordPolice.class);

    /**
     * The extra rules that we've added
     */
    private final List<ExtendedPasswordConstraint> extraRules;

    /**
     * Constructs a new ExtendedPasswordPolice with an empty list of extra rules.
     *
     * @param con the SailPointContext
     * @throws GeneralException if there is an error initializing the PasswordPolice
     */
    public ExtendedPasswordPolice(SailPointContext con) throws GeneralException {
        super(con);
        this.extraRules = new ArrayList<>();
    }

    /**
     * Constructs a new ExtendedPasswordPolice with the given PasswordPolicy and an empty list of extra rules.
     *
     * @param con the SailPointContext
     * @param policy the PasswordPolicy to use
     * @throws GeneralException if there is an error initializing the PasswordPolice
     */
    public ExtendedPasswordPolice(SailPointContext con, PasswordPolicy policy) throws GeneralException {
        super(con, policy);

        this.extraRules = new ArrayList<>();
    }

    /**
     * Adds a new password constraint to the list of rules. Since the _rules field is private,
     * we use reflection to access it and add the new constraint.
     *
     * There is no uniqueness check, so if you call this method twice with the same
     * constraint, you will end up wasting your time.
     *
     * @param constraint the ExtendedPasswordConstraint to add
     * @throws GeneralException if there is an error accessing the _rules field
     */
    @SuppressWarnings("unchecked")
    public void addConstraint(ExtendedPasswordConstraint constraint) throws GeneralException {
        try {
            Field rulesField = PasswordPolice.class.getDeclaredField("_rules");
            rulesField.setAccessible(true);

            List<PasswordConstraint> rules = (List<PasswordConstraint>) rulesField.get(this);
            rules.add(constraint);

            if (log.isDebugEnabled()) {
                log.debug("Added new password constraint: " + constraint.getDescription());
            }
        } catch(Exception e) {
            throw new GeneralException("Failed to access _rules field in PasswordPolice", e);
        }
    }

    @Override
    public void checkPassword(Identity identity, String password, boolean isSystemAdmin) throws GeneralException {
        if (log.isDebugEnabled()) {
            log.debug("Checking password for identity: " + identity.getName() + ", isSystemAdmin: " + isSystemAdmin);
        }
        super.checkPassword(identity, password, isSystemAdmin);
    }

    /**
     * Generates the descriptions of the various configured constraints, including any of our
     * extra rules.
     *
     * @param locale the locale to use for formatting
     * @param timeZone the time zone to use for formatting
     * @param showNoConstraintMessage whether to show the "no constraints" message
     * @return a list of constraint descriptions
     */
    @Override
    public List<String> getIIQPasswordConstraints(Locale locale, TimeZone timeZone, boolean showNoConstraintMessage) throws GeneralException {
        // If we have extra rules, we never want to show the "no constraints" message
        boolean reallyShowNoConstraintMessage = showNoConstraintMessage && this.extraRules.isEmpty();

        List<String> constraints = super.getIIQPasswordConstraints(locale, timeZone, reallyShowNoConstraintMessage);
        for (ExtendedPasswordConstraint constraint : this.extraRules) {
            constraints.add(constraint.getDescription());
        }
        return constraints;
    }

    /**
     * Validates the current state of the PasswordPolice, ensuring that any extra rules
     * have been set to admin mode if the _admin field is true.
     *
     * Note that this will re-add all of the OOTB constraints to _rules, which suggests that
     * SP doesn't particularly care about efficiency here.
     *
     * @throws GeneralException if there is an error accessing the _admin field
     * @throws sailpoint.api.PasswordPolicyException if there are validation errors in the password policy
     */
    @Override
    public void validate() throws GeneralException {
        try {
            Field adminField = PasswordPolice.class.getDeclaredField("_admin");
            adminField.setAccessible(true);

            boolean isAdmin = (boolean) adminField.get(this);

            if (isAdmin) {
                if (log.isDebugEnabled()) {
                    log.debug("Setting admin mode for all extra password constraints");
                }
                for(ExtendedPasswordConstraint constraint : this.extraRules) {
                    constraint.setAdmin(true);
                }
            }
        } catch(Exception e) {
            throw new GeneralException("Failed to access _admin field in PasswordPolice", e);
        }
        try {
            super.validate();
        } catch(PasswordPolicyException e) {
            if (log.isDebugEnabled()) {
                log.debug("Password policy validation failed: {0}", e);
            }
        }
    }
}
