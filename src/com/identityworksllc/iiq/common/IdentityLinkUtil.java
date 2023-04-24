package com.identityworksllc.iiq.common;

import sailpoint.api.IdentityService;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.QueryOptions;
import sailpoint.object.Schema;
import sailpoint.tools.GeneralException;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * A utility class for efficiently reading various types of information from
 * Link objects. This class supplements IdentityService by providing additional
 * methods for retrieving attributes of various types.
 *
 * This is very common logic in virtually all IIQ instances, and this will prevent
 * us from having to reimplement it for both Beanshell and Java every time. It
 * should also increase efficiency by being in compiled Java and not Beanshell.
 *
 * There are essentially two modes of operation, depending on your purposes. If
 * you need to pre-load all Identity Links prior to running an operation, set the
 * forceLoad flag to true using {@link #setForceLoad(boolean)}. If you do not do
 * this, this class will echo the logic used by {@link IdentityService#getLinks(Identity, Application)}.
 *
 * You will generally NOT want to set forceLoad=true unless you need to repeatedly
 * query the same Links from the Identity. The most notable example is when you
 * are reading values from Links in more than a handful of IdentityAttribute rules.
 *
 * In all cases, the get-attribute methods take the 'failOnMultiple' flag into
 * account. If the flag is false, as is default, the value from the newest Link
 * will be retrieved.
 */
public class IdentityLinkUtil {

    /**
     * Returns a Filter object for a Link
     * @param applicationName The application name
     * @param nativeIdentity The native ID
     * @return the resulting Filter
     */
    public static Filter getLinkFilter(String applicationName, String nativeIdentity) {
        return Filter.and(
                Filter.eq("application.name", applicationName),
                Filter.eq("nativeIdentity", nativeIdentity)
        );
    }

    /**
     * Gets a unique Link by Native ID and Application or else throws an exception
     * @param context The context for querying
     * @param applicationName The application name
     * @param nativeIdentity The native ID
     * @return Null if no matches, a single Link if there is a match
     * @throws GeneralException if there is a query failure
     * @throws TooManyResultsException if more than one Link matches the criteria
     */
    public static Link getUniqueLink(SailPointContext context, String applicationName, String nativeIdentity) throws GeneralException {
        QueryOptions qo = new QueryOptions();
        Filter theFilter = getLinkFilter(applicationName, nativeIdentity);

        qo.addFilter(theFilter);

        List<Link> links = context.getObjects(Link.class, qo);
        if (links == null || links.size() == 0) {
            return null;
        } else if (links.size() == 1) {
            return links.get(0);
        } else {
            throw new TooManyResultsException(Link.class, theFilter.getExpression(true), links.size());
        }
    }

    /**
     * The Sailpoint context
     */
    private final SailPointContext context;
    /**
     * If true, the various get-attribute methods will fail if the user has more
     * than one of the same type.
     */
    private boolean failOnMultiple;
    /**
     * If true, the identity's Links will be forcibly loaded by calling load()
     * on the whole collection before running any operation. This will make
     * subsequent operations on the same object potentially faster.
     */
    private boolean forceLoad;
    /**
     * The global link filter, to be applied to any queries for a Link by application
     */
    private Filter globalLinkFilter;
    /**
     * The Identity associated with this utility
     */
    private final Identity identity;

    /**
     * Identity Link utility constructor
     * @param context The Sailpoint context
     * @param identity the Identity
     */
    public IdentityLinkUtil(SailPointContext context, Identity identity) {
        this(context, identity, null);
    }

    /**
     * Identity Link utility constructor
     * @param context The Sailpoint context
     * @param identity the Identity
     */
    public IdentityLinkUtil(SailPointContext context, Identity identity, Filter globalLinkFilter) {
        this.context = Objects.requireNonNull(context);
        this.identity = Objects.requireNonNull(identity);
        this.forceLoad = false;
        this.failOnMultiple = false;
        this.globalLinkFilter = globalLinkFilter;
    }

    /**
     * Iterates over the list of Links on this Identity and loads them all
     */
    private void checkLoaded() {
        Iterable<Link> links = Util.safeIterable(identity.getLinks());
        for (Link l : links) {
            l.load();
        }
    }

    /**
     * Gets the applied (possibly null) global link filter
     * @return The applied global link filter
     */
    public Filter getGlobalLinkFilter() {
        return globalLinkFilter;
    }

    /**
     * Gets the Link from the Identity by native identity
     * @param application The application type of the Link
     * @param nativeIdentity The native identity of the Link
     * @return The Link
     * @throws GeneralException if any failures occur
     */
    public Link getLinkByNativeIdentity(Application application, String nativeIdentity) throws GeneralException {
        if (forceLoad) {
            checkLoaded();
        }

        IdentityService ids = new IdentityService(context);
        return ids.getLink(identity, application, null, nativeIdentity);
    }

    /**
     * @see #getLinkByNativeIdentity(Application, String)
     */
    public Link getLinkByNativeIdentity(String applicationName, String nativeIdentity) throws GeneralException {
        Application application = context.getObject(Application.class, applicationName);

        if (application == null) {
            throw new ObjectNotFoundException(Application.class, applicationName);
        }

        return getLinkByNativeIdentity(application, nativeIdentity);
    }

    /**
     * @see #getLinksByApplication(Application, Filter)
     */
    public List<Link> getLinksByApplication(Application application) throws GeneralException {
        return getLinksByApplication(application, null);
    }

    /**
     * Gets the list of Links of the given application type, applying the given optional
     * filter to the links. If a filter is present, only Links matching the filter will be
     * returned.
     *
     * @param application The application object
     * @param linkFilter The filter object, optional
     * @return A non-null list of links (optionally filtered) on this user of the given application type
     * @throws GeneralException if any failures occur
     */
    public List<Link> getLinksByApplication(Application application, Filter linkFilter) throws GeneralException {
        if (forceLoad) {
            checkLoaded();
        }

        IdentityService ids = new IdentityService(context);
        List<Link> links = ids.getLinks(identity, application);

        if (links == null) {
            links = new ArrayList<>();
        } else {
            // Ensure that the list is mutable and detached from the Identity
            links = new ArrayList<>(links);
        }
        Filter finalFilter = null;

        if (this.globalLinkFilter != null && linkFilter != null) {
            finalFilter = Filter.and(this.globalLinkFilter, linkFilter);
        } else if (this.globalLinkFilter != null) {
            finalFilter = this.globalLinkFilter;
        } else if (linkFilter != null) {
            finalFilter = linkFilter;
        }

        if (finalFilter != null) {
            List<Link> newList = new ArrayList<>();
            HybridObjectMatcher matcher = new HybridObjectMatcher(context, finalFilter);
            for(Link l : links) {
                if (matcher.matches(l)) {
                    newList.add(l);
                }
            }
            links = newList;
        }

        return links;
    }

    /**
     * @see #getLinksByApplication(Application, Filter)
     */
    public List<Link> getLinksByApplication(String applicationName) throws GeneralException {
        Application application = context.getObject(Application.class, applicationName);

        if (application == null) {
            throw new ObjectNotFoundException(Application.class, applicationName);
        }

        return getLinksByApplication(application);
    }

    /**
     * @see #getLinksByApplication(Application, Filter)
     */
    public List<Link> getLinksByApplication(String applicationName, Filter linkFilter) throws GeneralException {
        Application application = context.getObject(Application.class, applicationName);

        if (application == null) {
            throw new ObjectNotFoundException(Application.class, applicationName);
        }

        return getLinksByApplication(application, linkFilter);
    }

    /**
     * @see #getMultiValueLinkAttribute(Application, String, Filter)
     */
    public List<String> getMultiValueLinkAttribute(String applicationName, String attributeName) throws GeneralException {
        return getMultiValueLinkAttribute(applicationName, attributeName, null);
    }

    /**
     * @see #getMultiValueLinkAttribute(Application, String, Filter)
     */
    public List<String> getMultiValueLinkAttribute(String applicationName, String attributeName, Filter linkFilter) throws GeneralException {
        Application application = context.getObject(Application.class, applicationName);

        if (application == null) {
            throw new ObjectNotFoundException(Application.class, applicationName);
        }

        return getMultiValueLinkAttribute(application, attributeName, linkFilter);
    }

    /**
     * Gets the value of a multi-valued attribute from one Link of the given type
     * belonging to this Identity. The actual type of the attribute doesn't matter.
     * A CSV single-valued String will be converted to a List here.
     *
     * @param application The application type of the Links
     * @param attributeName The attribute name to grab
     * @param linkFilter The Link filter, optional
     * @return The value of the attribute, or null
     * @throws GeneralException if any errors occur
     */
    public List<String> getMultiValueLinkAttribute(Application application, String attributeName, Filter linkFilter) throws GeneralException {
        List<Link> links = getLinksByApplication(application, linkFilter);

        if (Util.isEmpty(links)) {
            return null;
        } else if (links.size() == 1) {
            Object value = links.get(0).getAttribute(attributeName);
            return Util.otol(value);
        } else {
            if (failOnMultiple) {
                throw new GeneralException("Too many accounts of type " + application.getName());
            } else {
                SailPointObjectDateSorter.sort(links);
                Object value = links.get(0).getAttribute(attributeName);
                return Util.otol(value);
            }
        }
    }

    /**
     * @see #getMultiValueLinkAttribute(Application, String, Filter)
     */
    public List<String> getMultiValueLinkAttribute(Application application, String attributeName) throws GeneralException {
        return getMultiValueLinkAttribute(application, attributeName, null);
    }

    /**
     * @see #getSingleValueLinkAttribute(Application, String, Filter)
     */
    public String getSingleValueLinkAttribute(String applicationName, String attributeName) throws GeneralException {
        Application application = context.getObject(Application.class, applicationName);

        if (application == null) {
            throw new ObjectNotFoundException(Application.class, applicationName);
        }

        return getSingleValueLinkAttribute(application, attributeName, null);
    }

    /**
     * @see #getSingleValueLinkAttribute(Application, String, Filter)
     */
    public String getSingleValueLinkAttribute(String applicationName, String attributeName, Filter linkFilter) throws GeneralException {
        Application application = context.getObject(Application.class, applicationName);

        if (application == null) {
            throw new ObjectNotFoundException(Application.class, applicationName);
        }

        return getSingleValueLinkAttribute(application, attributeName, linkFilter);
    }

    /**
     * @see #getSingleValueLinkAttribute(Application, String, Filter)
     */
    public String getSingleValueLinkAttribute(Application application, String attributeName) throws GeneralException {
        return getSingleValueLinkAttribute(application, attributeName, null);
    }

    /**
     * Gets the value of a single-valued attribute from one Link of the given type
     * belonging to this Identity.
     *
     * @param application The application type of the Links
     * @param attributeName The attribute name to grab
     * @param linkFilter The Link filter, optional
     * @return The value of the attribute, or null
     * @throws GeneralException if any errors occur
     */
    public String getSingleValueLinkAttribute(Application application, String attributeName, Filter linkFilter) throws GeneralException {
        List<Link> links = getLinksByApplication(application, linkFilter);

        if (Util.isEmpty(links)) {
            return null;
        } else if (links.size() == 1) {
            Object value = links.get(0).getAttribute(attributeName);
            return Util.otoa(value);
        } else {
            if (failOnMultiple) {
                throw new GeneralException("Too many accounts of type " + application.getName());
            } else {
                SailPointObjectDateSorter.sort(links);
                Object value = links.get(0).getAttribute(attributeName);
                return Util.otoa(value);
            }
        }
    }

    public boolean isFailOnMultiple() {
        return failOnMultiple;
    }

    public boolean isForceLoad() {
        return forceLoad;
    }

    /**
     * @see #mergeLinkAttributes(Application, String, Filter)
     */
    public List<String> mergeLinkAttributes(String applicationName, String attributeName) throws GeneralException {
        Application application = context.getObject(Application.class, applicationName);

        if (application == null) {
            throw new ObjectNotFoundException(Application.class, applicationName);
        }

        return mergeLinkAttributes(application, attributeName, null);
    }

    /**
     * @see #mergeLinkAttributes(Application, String, Filter)
     */
    public List<String> mergeLinkAttributes(String applicationName, String attributeName, Filter linkFilter) throws GeneralException {
        Application application = context.getObject(Application.class, applicationName);

        if (application == null) {
            throw new ObjectNotFoundException(Application.class, applicationName);
        }

        return mergeLinkAttributes(application, attributeName, linkFilter);
    }

    /**
     * Extracts the named attribute from each Link of the given application and adds
     * all values from each Link into a common List.
     *
     * @param application The application to query
     * @param attributeName The attribute name to query
     * @param linkFilter The link filter, optional
     * @return The merged set of attributes from each application
     * @throws GeneralException if any failures occur
     */
    public List<String> mergeLinkAttributes(Application application, String attributeName, Filter linkFilter) throws GeneralException {
        List<Link> links = getLinksByApplication(application, linkFilter);

        boolean isMultiValued = false;

        Schema accountSchema = application.getAccountSchema();
        if (accountSchema != null) {
            AttributeDefinition attributeDefinition = accountSchema.getAttributeDefinition(attributeName);
            if (attributeDefinition != null) {
                isMultiValued = attributeDefinition.isMultiValued();
            }
        }

        List<String> values = new ArrayList<>();
        for(Link l : links) {
            Object value = l.getAttribute(attributeName);
            if (isMultiValued) {
                value = Util.otol(value);
            } else {
                value = Util.otoa(value);
            }

            if (value instanceof String) {
                values.add((String)value);
            } else if (value instanceof Collection) {
                values.addAll((Collection<String>)value);
            }
        }
        return values;
    }

    /**
     * If true, and the Identity has more than one (post-filter) Link of a given
     * Application type, the get-attribute methods will throw an exception.
     *
     * @param failOnMultiple True if we should fail on multiple accounts
     */
    public void setFailOnMultiple(boolean failOnMultiple) {
        this.failOnMultiple = failOnMultiple;
    }

    /**
     * If true, the Identity's `links` container will be populated before searching for
     * items. This will make the IdentityService faster in some circumstances, notably
     * repeated queries of links in Identity Attributes.
     *
     * @param forceLoad True if we should always load the Link objects
     */
    public void setForceLoad(boolean forceLoad) {
        this.forceLoad = forceLoad;
    }

    /**
     * Sets a global link filter, allowing use of a constant
     * @param globalLinkFilter The filter to apply to any operation
     */
    public void setGlobalLinkFilter(Filter globalLinkFilter) {
        this.globalLinkFilter = globalLinkFilter;
    }
}
