package com.identityworksllc.iiq.common;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import sailpoint.api.DynamicScopeMatchmaker;
import sailpoint.api.EntitlementCorrelator;
import sailpoint.api.IdentityService;
import sailpoint.api.Matchmaker;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.Bundle;
import sailpoint.object.CompoundFilter;
import sailpoint.object.DynamicScope;
import sailpoint.object.Filter;
import sailpoint.object.GroupDefinition;
import sailpoint.object.Identity;
import sailpoint.object.IdentityFilter;
import sailpoint.object.IdentitySelector;
import sailpoint.object.Link;
import sailpoint.object.Profile;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.object.Filter.BaseFilterVisitor;
import sailpoint.persistence.HibernatePersistenceManager;
import sailpoint.search.MapMatcher;
import sailpoint.tools.GeneralException;

/**
 * Utilities for matching objects using IIQ APIs
 */
public class MatchUtilities extends AbstractBaseUtility {

	/**
	 * Basic constructor
	 * @param c The IIQ context
	 */
	public MatchUtilities(SailPointContext c) {
		super(c);
	}
	
	/**
	 * Returns the list of matching Link objects by application for this role. Each profile
	 * may match more than one Link. Each Link will only be returned once, even if it matches
	 * multiple profiles.
	 *
	 * Use {@link #identityMatchesSimpleProfiles(Bundle, Identity)} first to determine an overall
	 * match and then this method to extract the matches.
	 *
	 * The result will be empty if the role has no profiles or is null.
	 *
	 * @param role The role from which profiles should be checked
	 * @param identity The identity from which to extract account details
	 * @return A map of Application Name -> List of matching Links
	 * @throws GeneralException if any lookup failures occur
	 */
	public Map<String, List<Link>> findMatchingLinksByApplication(Bundle role, Identity identity) throws GeneralException {
		Map<String, List<Link>> results = new HashMap<>();
		// Don't match an empty role
		if (role == null || role.getProfiles() == null || role.getProfiles().isEmpty()) {
			return results;
		}
		IdentityService ids = new IdentityService(context);
		for(Profile profile : role.getProfiles()) {
			List<Link> links = new ArrayList<>();
			if (profile.getApplication() != null) {
				links = ids.getLinks(identity, profile.getApplication());
			}
			if (links != null && links.size() > 0) {
				final String application = profile.getApplication().getName();
				for(Link link : links) {
					boolean matched = linkMatchesProfile(profile, link);
					if (matched) {
						if (!results.containsKey(application)) {
							results.put(application, new ArrayList<>());
						}
						if (!results.get(application).contains(link)) {
							results.get(application).add(link);
						}
					}
				}
			}
		}
		return results;
	}

	/**
	 * Performs some reflection digging into the Sailpoint API to transform a standard
	 * {@link Filter} into a Hibernate HQL query.
	 * @param target The target filter to transform
	 * @param targetClass The target class (determines the queried tables)
	 * @param columns The columns to return
	 * @return The HQL query
	 * @throws GeneralException if any failures occur accessing the internal APIs
	 */
	public String getFilterHQL(Filter target, Class<? extends SailPointObject> targetClass, String... columns) throws GeneralException {
		SailPointContext privateSession = SailPointFactory.createPrivateContext();
		try {
			HibernatePersistenceManager manager = HibernatePersistenceManager.getHibernatePersistenceManager(privateSession);
			Method visitHQLFilter = manager.getClass().getDeclaredMethod("visitHQLFilter", Class.class, QueryOptions.class, List.class);
			visitHQLFilter.setAccessible(true);
			try {
				manager.startTransaction();
				try {
					QueryOptions qo = new QueryOptions();
					qo.addFilter(target);
					List<String> cols = new ArrayList<>(Arrays.asList(columns));
					BaseFilterVisitor visitor = (BaseFilterVisitor) visitHQLFilter.invoke(manager, targetClass, qo, cols);
					Method getQueryString = visitor.getClass().getDeclaredMethod("getQueryString");
					getQueryString.setAccessible(true);
					return (String)getQueryString.invoke(visitor);
				} catch(InvocationTargetException e) {
					if (e.getTargetException() instanceof GeneralException) {
						throw (GeneralException)e.getTargetException();
					} else {
						throw new GeneralException(e.getTargetException());
					}
				} catch (IllegalAccessException | IllegalArgumentException e) {
					throw new GeneralException(e);
				} finally {
					manager.commitTransaction();
				}
			} finally {
				visitHQLFilter.setAccessible(false);
			}
		} catch (NoSuchMethodException | SecurityException e1) {
			throw new GeneralException(e1);
		} finally {
			SailPointFactory.releasePrivateContext(privateSession);
		}
	}

	/**
	 * Returns true if the given identity has accounts matching all of the profiles on the given role
	 * @param role The role to check
	 * @param identity The identity whose links to check against the role
	 * @return True if the link matches all of the Bundle profiles
	 * @throws GeneralException if any failures occur
	 */
	public boolean identityMatchesSimpleProfiles(Bundle role, Identity identity) throws GeneralException {
		Objects.requireNonNull(identity, "Cannot match profiles against a null Identity");
		int matchedCount = 0;
		// Don't match an empty role
		if (role == null || role.getProfiles() == null || role.getProfiles().isEmpty()) {
			return false;
		}
		IdentityService ids = new IdentityService(context);
		for(Profile profile : role.getProfiles()) {
			List<Link> links = new ArrayList<>();
			if (profile.getApplication() != null) {
				links = ids.getLinks(identity, profile.getApplication());
			}
			if (links != null && links.size() > 0) {
				// Check each Link of the appropriate type against this Profile. The Profile may have multiple filters and they must ALL match to count.
				for(Link link : links) {
					boolean matched = linkMatchesProfile(profile, link);
					// If the link matched all filters, count it as a match toward this Profile
					// and don't check any further Links.
					if (matched) {
						matchedCount++;
						break;
					}
				}
			}
		}
		if (role.isOrProfiles() && matchedCount > 1) {
			return true;
		} else if (matchedCount == role.getProfiles().size()) {
			return true;
		}
		return false;
	}
	
	/**
	 * Returns true if the given identity would be a member of the given IdentityFilter (e.g. used for searching)
	 * @param test The identity to test
	 * @param target The filter to check
	 * @param requestParameters Any parameters required by the filter script, if one exists
	 * @return true if the identity matches the filter criteria
	 * @throws GeneralException if any match failure occurs
	 */
	public boolean identitySelectorMatches(Identity test, IdentityFilter target, Map<String, Object> requestParameters) throws GeneralException {
		QueryOptions qo = target.buildQuery(requestParameters, context);
		qo.addFilter(Filter.eq("Identity.id", test.getId()));
		return (context.countObjects(Identity.class, qo) > 0);
	}
	
	/**
	 * Returns true if the given identity would be a member of the given IdentityFilter (e.g. used for searching)
	 * @param test The identity to test
	 * @param selectorName The Identity Selector to query
	 * @param requestParameters Any parameters required by the filter script, if one exists
	 * @return true if the identity matches the filter criteria
	 * @throws GeneralException if any match failure occurs
	 */
	public boolean identitySelectorMatches(Identity test, String selectorName, Map<String, Object> requestParameters) throws GeneralException {
		IdentityService ids = new IdentityService(context);
		Map<String, Object> suggestParams = new HashMap<String, Object>();
		suggestParams.putAll(requestParameters);
		suggestParams.put("suggestId", selectorName);
		QueryOptions qo = ids.getIdentitySuggestQueryOptions(suggestParams, context.getObject(Identity.class, context.getUserName()));
		qo.addFilter(Filter.eq("Identity.id", test.getId()));
		return (context.countObjects(Identity.class, qo) > 0);
	}
	
	/**
	 * Returns true if the given Profile matches the given Link via {@link MapMatcher}.
	 * @param profile The profile
	 * @param link The link
	 * @return true if the link matches the profile
	 * @throws GeneralException if any failures occur during matching (should never happen)
	 */
	public boolean linkMatchesProfile(Profile profile, Link link) throws GeneralException {
		boolean matched = true;
		List<Filter> filters = profile.getConstraints();
		if (filters != null) {
			for (Filter filter : filters) {
				MapMatcher matcher = new MapMatcher(filter);
				if (!matcher.matches(link.getAttributes())) {
					matched = false;
					break;
				}
			}
		}
		return matched;
	}
	
	/**
	 * Returns true if the given link matches the profiles on the given role
	 * @param role The role to check
	 * @param link The link to check against the role
	 * @return True if the link matches all of the Bundle profiles
	 * @throws GeneralException if any failures occur
	 */
	public boolean linkMatchesSimpleProfiles(Bundle role, Link link) throws GeneralException {
		int matchedCount = 0;
		// Don't match an empty role
		if (role.getProfiles() == null || role.getProfiles().isEmpty()) {
			return false;
		}
		for(Profile profile : role.getProfiles()) {
			boolean matched = true;
			List<Filter> filters = profile.getConstraints();
			if (filters != null) {
				for (Filter filter : filters) {
					MapMatcher matcher = new MapMatcher(filter);
					if (!matcher.matches(link.getAttributes()))  {
						matched = false;
					}
				}
			}
			if (matched) {
				matchedCount++;
			}
		}
		if (role.isOrProfiles() && matchedCount > 0) {
			return true;
		} else if (matchedCount == role.getProfiles().size()) {
			return true;
		}
		return false;
	}
	
	/**
	 * Returns true if the given Identity would be a member of the target Bundle, either by assignment or detection
	 * @param target The role to check
	 * @param test The identity to test
	 * @return true if the identity matches the role criteria
	 * @throws GeneralException if any match failure occurs
	 */
	public boolean matches(Bundle target, Identity test) throws GeneralException {
		return matches(test, target);
	}
	
	/**
	 * Returns true if the given Filter would by matched by the given Identity
	 * @param target The filter to run 
	 * @param test The Identity to test against the filter
	 * @return true if the identity matches the Filter
	 * @throws GeneralException if a query exception occurs
	 */
	public boolean matches(Filter target, Identity test) throws GeneralException {
		return matches(test, target);
	}
	
	/**
	 * Returns true if the given Identity would be a member of the target Bundle, either by assignment or detection
	 * @param target The identity to test
	 * @param role The bundle to test
	 * @return true if the identity matches the role criteria
	 * @throws GeneralException if any match failure occurs
	 */
	public boolean matches(Identity target, Bundle role) throws GeneralException {
		boolean isAssignmentRole = true;
		if (role.getSelector() == null) {
			isAssignmentRole = false;
			if (role.getProfiles() == null || role.getProfiles().isEmpty()) {
				throw new IllegalArgumentException("Role " + role.getName() + " has no selector and no profiles");
			}
		}
		if (isAssignmentRole) {
			// Business role needs assignment selector
			Matchmaker matcher = new Matchmaker(context);
			matcher.setArgument("iiqNoCompilationCache", "true");
			matcher.setArgument("roleName", role.getName());
			matcher.setArgument("identity", target);
			return matcher.isMatch(role.getSelector(), target);
		} else {
			EntitlementCorrelator correlator = new EntitlementCorrelator(context);
			correlator.setDoRoleAssignment(true);
			correlator.setNoPersistentIdentity(true);
			correlator.analyzeIdentity(target);
			
			Identity dummyTarget = new Identity();
			correlator.saveDetectionAnalysis(dummyTarget);

			return dummyTarget.getDetectedRoles().stream().map(SailPointObject::getName).collect(Collectors.toList()).contains(role.getName());
		}
	}
	
	/**
	 * Returns true if the given identity would match the target filter
	 * @param target The target identity
	 * @param filter The compound filter to check
	 * @return True if the identity matches the filer
	 * @throws GeneralException if any match failure occurs
	 */
	public boolean matches(Identity target, CompoundFilter filter) throws GeneralException {
		IdentitySelector selector = new IdentitySelector();
		selector.setFilter(filter);
		return matches(target, selector);
	}
	
	/**
	 * Returns true if the given identity matches the given Dynamic Scope
	 * @param target The target to check
	 * @param scope The scope to match against
	 * @return true if the identity matches the dynamic scope filter
	 * @throws GeneralException if any match failure occurs
	 */
	public boolean matches(Identity target, DynamicScope scope) throws GeneralException {
		DynamicScopeMatchmaker matchmaker = new DynamicScopeMatchmaker(context);
		return matchmaker.isMatch(scope, target);
	}
	
	/**
	 * Returns true if the given identity would match the target filter
	 * @param target The target identity
	 * @param filter The compound filter to check
	 * @return True if the identity matches the filer
	 * @throws GeneralException if any match failure occurs
	 */	
	public boolean matches(Identity target, Filter filter) throws GeneralException {
		CompoundFilter compound = new CompoundFilter();
		compound.setFilter(filter);
		return matches(target, compound);
	}
	
	/**
	 * Returns true if the given identity would match the target population's filter
	 * @param target The target identity
	 * @param population The population to check
	 * @return True if the identity matches the filer
	 * @throws GeneralException if any match failure occurs
	 */
	public boolean matches(Identity target, GroupDefinition population) throws GeneralException {
		if (population.getFilter() == null) {
			throw new IllegalArgumentException("Population " + population.getName() + " has no filter defined");
		}
		IdentitySelector selector = new IdentitySelector();
		selector.setPopulation(population);
		return matches(target, selector);
	}
	
	/**
	 * Returns true if the given identity would match the target filter
	 * @param target The target identity
	 * @param selector The selector / filter to check
	 * @return True if the identity matches the filer
	 * @throws GeneralException if any match failure occurs
	 */	
	public boolean matches(Identity target, IdentitySelector selector) throws GeneralException {
		Matchmaker matcher = new Matchmaker(context);
		matcher.setArgument("iiqNoCompilationCache", "true");
		matcher.setArgument("identity", target);
		return matcher.isMatch(selector, target);
	}
	
	/**
	 * Returns true if the given identity would match the target filter
	 * @param target The target identity
	 * @param filter The filter string to compile and check
	 * @return True if the identity matches the filer
	 * @throws GeneralException if any match failure occurs
	 */	
	public boolean matches(Identity target, String filter) throws GeneralException {
		Filter f = Filter.compile(filter);
		return matches(target, f);
	}
	
	/**
	 * Returns true if the given identity would match the target filter
	 * @param target The target selector
	 * @param test The identity to examine
	 * @return True if the identity matches the filer
	 * @throws GeneralException if any match failure occurs
	 */	
	public boolean matches(IdentitySelector target, Identity test) throws GeneralException {
		return matches(test, target);
	}

}
