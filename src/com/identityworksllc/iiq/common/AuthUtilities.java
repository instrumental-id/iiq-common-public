package com.identityworksllc.iiq.common;

import com.identityworksllc.iiq.common.auth.DummyAuthContext;
import sailpoint.api.SailPointContext;
import sailpoint.authorization.LcmRequestAuthorizer;
import sailpoint.authorization.QuickLinkLaunchAuthorizer;
import sailpoint.authorization.UnauthorizedAccessException;
import sailpoint.object.Identity;
import sailpoint.object.Identity.CapabilityManager;
import sailpoint.object.QuickLink;
import sailpoint.tools.GeneralException;

import java.util.Objects;

/**
 * Utilities for authorization, e.g., whether a user can view a QuickLink. For the
 * Common Security style of authorization, use ThingAccessUtils instead.
 */
public class AuthUtilities extends AbstractBaseUtility {

	/**
	 * Indicates the type of access to a QuickLink we are checking
	 */
	public enum QuickLinkAccessType {
		/**
		 * Check ANY type of access (self or other)
		 */
		ANY,
		/**
		 * Check whether this user can invoke this QuickLink on other users
		 */
		OTHER,
		/**
		 * Check whether this user can invoke this QuickLink on themselves
		 */
		SELF
	}

	/**
	 * Constructs a new instance of AuthUtilities
	 * @param c
	 */
	public AuthUtilities(SailPointContext c) {
		super(c);
	}

	/**
	 * Returns true if the given person could launch the given QuickLink against the given target.
	 *
	 * You may pass a null target or a target the same as the launcher to infer an access type of
	 * SELF. If the target is not null and does not match the launcher, an access type of OTHER
	 * will be inferred.
	 *
	 * @param launcher The person we're testing for access
	 * @param target The (optional) target against whom the QuickLink would be executed
	 * @param what The QuickLink we're testing
	 * @return true if the launcher could perform this QL operation
	 * @throws GeneralException if any IIQ failure occurs
	 */
	public boolean canAccessQuicklink(Identity launcher, Identity target, QuickLink what) throws GeneralException {
		Objects.requireNonNull(launcher, "The 'launcher' Identity must not be null");
		if (target == null || target.getId().equals(launcher.getId())) {
			return canAccessQuicklink(launcher, launcher, what, QuickLinkAccessType.SELF);
		} else {
			return canAccessQuicklink(launcher, target, what, QuickLinkAccessType.SELF);
		}
	}

	/**
	 * Returns true if the given person could launch the given QuickLink against the given target. Pass a
	 * null target to take the QuickLinkAccessType into account, e.g. if you just need a general "can see
	 * in any circumstance" answer.
	 * 
	 * @param launcher The person we're testing for access
	 * @param target The (optional) target against whom the QuickLink would be executed
	 * @param what The QuickLink we're testing
	 * @param accessType The access type we're interested in
	 * @return true if the launcher could perform this QL operation
	 * @throws GeneralException if any IIQ failure occurs
	 */
	public boolean canAccessQuicklink(Identity launcher, Identity target, QuickLink what, QuickLinkAccessType accessType) throws GeneralException {
		Objects.requireNonNull(launcher, "The 'launcher' Identity must not be null");
		DummyAuthContext authContext = new DummyAuthContext(context, launcher.getName());
		if (accessType == QuickLinkAccessType.ANY) {
			return canViewQuicklink(launcher, what, accessType);
		} else {
			String lcmAction = null;
			if (!(what.getAction().equals(QuickLink.ACTION_WORKFLOW) || what.getAction().equals(QuickLink.ACTION_EXTERNAL))) {
				lcmAction = what.getAction();
			}
			if (target == null && accessType == QuickLinkAccessType.SELF) {
				target = launcher;
			}
			LcmRequestAuthorizer authorizer = new LcmRequestAuthorizer(target);
			authorizer.setQuickLinkName(what.getName());
			authorizer.setAction(lcmAction);
			try {
				authorizer.authorize(authContext);
				return true;
			} catch(UnauthorizedAccessException e) {
				return false;
			}
		}
	}

	/**
	 * Returns true if the user in question can view the QuickLink under any circumstances (i.e. if it would be displayed on their sidebar).
	 * @param launcher The user to query
	 * @param what The QuickLink to check
	 * @param accessType The access type to check for
	 * @return If the user would have access to this QuickLink, true, otherwise false
	 * @throws GeneralException if any IIQ failure occurs
	 */
	public boolean canViewQuicklink(Identity launcher, QuickLink what, QuickLinkAccessType accessType) throws GeneralException {
		DummyAuthContext authContext = new DummyAuthContext(context, launcher.getName());
		try {
			QuickLinkLaunchAuthorizer authorizer = new QuickLinkLaunchAuthorizer(what, accessType == QuickLinkAccessType.ANY || accessType == QuickLinkAccessType.SELF);
			authorizer.authorize(authContext);
			return true;
		} catch(UnauthorizedAccessException e) {
			if (accessType == QuickLinkAccessType.ANY) {
				try {
					QuickLinkLaunchAuthorizer authorizer = new QuickLinkLaunchAuthorizer(what, false);
					authorizer.authorize(authContext);
					return true;
				} catch(UnauthorizedAccessException e2) {
					return false;
				}
			}
			return false;
		}
	}
	
	/**
	 * Throws an exception if the given Identity does not have the given right (optionally
	 * also allowing sysadmins).
	 * 
	 * @param who The identity to test
	 * @param what The SPRight to test for
	 * @param allowAdmins If true, SystemAdministrators will also be allowed, even without the SPRight
	 * @throws UnauthorizedAccessException if the user does not have access
	 */
	public void checkAuthorization(Identity who, String what, boolean allowAdmins) throws UnauthorizedAccessException {
		boolean allowed = false;
		CapabilityManager cm = who.getCapabilityManager();
		if (allowAdmins) {
			allowed = cm.hasCapability("SystemAdministrator");
		}
		if (!allowed) {
			allowed = cm.hasRight(what);
		}
		if (!allowed) {
			throw new UnauthorizedAccessException("Access to this resource requires SPRight " + what);
		}
	}
	
}
