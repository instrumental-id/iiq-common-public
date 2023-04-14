package com.identityworksllc.iiq.common.minimal.auth;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import sailpoint.api.DynamicScopeMatchmaker;
import sailpoint.api.SailPointContext;
import sailpoint.object.Capability;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;
import sailpoint.web.UserContext;

/**
 * Dummy authorization context for use with the authorization methods
 */
public class DummyAuthContext implements UserContext {
	
	/**
	 * The current SailPointContext
	 */
	private SailPointContext context;
	
	/**
	 * The identity name
	 */
	private String identityName;
	
	/**
	 * Constructor
	 * 
	 * @param context The current IIQ context
	 * @param identityName The identity name
	 */
	public DummyAuthContext(SailPointContext context, String identityName) {
		this.context = context;
		this.identityName = identityName;
	}

	@Override
	public SailPointContext getContext() {
		return context;
	}

	@Override
	public Locale getLocale() {
		return Locale.getDefault();
	}

	@Override
	public Identity getLoggedInUser() throws GeneralException {
		return context.getObjectByName(Identity.class, identityName);
	}

	@Override
	public List<Capability> getLoggedInUserCapabilities() {
		try {
			return getLoggedInUser().getCapabilityManager().getEffectiveCapabilities();
		} catch(GeneralException e) {
			return new ArrayList<Capability>();
		}
	}

	@Override
	public List<String> getLoggedInUserDynamicScopeNames() throws GeneralException {
		DynamicScopeMatchmaker matchmaker = new DynamicScopeMatchmaker(context);
		return matchmaker.getMatches(getLoggedInUser());
	}

	@Override
	public String getLoggedInUserName() throws GeneralException {
		return identityName;
	}

	@Override
	public Collection<String> getLoggedInUserRights() {
		try {
			return getLoggedInUser().getCapabilityManager().getEffectiveFlattenedRights();
		} catch(GeneralException e) {
			return new ArrayList<String>();
		}
	}

	@Override
	public TimeZone getUserTimeZone() {
		return TimeZone.getDefault();
	}

	/**
	 * Introduced in 8.1
	 * @return Always false
	 */
	public boolean isMobileLogin() {
		return false;
	}

	@Override
	public boolean isObjectInUserScope(SailPointObject object) throws GeneralException {
		return isObjectInUserScope(object.getId(), object.getClass());
	}

	@Override
	public boolean isObjectInUserScope(String id, @SuppressWarnings("rawtypes") Class clazz) throws GeneralException {
	      QueryOptions scopingOptions = new QueryOptions();
	      scopingOptions.setScopeResults(Boolean.valueOf(true));
	      scopingOptions.add(new Filter[] { Filter.eq("id", id) });
	      
	      @SuppressWarnings("unchecked") 
	      int count = getContext().countObjects(clazz, scopingOptions);
	      
	      return (id == null) || ("".equals(id)) || count > 0;
	}

	@Override
	public boolean isScopingEnabled() throws GeneralException {
		// TODO Auto-generated method stub
		return false;
	}

}
