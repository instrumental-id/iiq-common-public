package com.identityworksllc.iiq.common;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.SailPointContext;
import sailpoint.object.EntitlementCollection;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.search.HybridReflectiveMatcher;
import sailpoint.search.JavaPropertyMatcher;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * This class implements an extension to SailPoint's most advanced matcher, the {@link HybridReflectiveMatcher},
 * permitting in-memory matching of arbitrary SailPointObjects. It can consume an arbitrary Filter, like all
 * other {@link sailpoint.search.Matcher} implementations, but supports a greater variety of filters than any
 * OOTB Matcher implementation.
 *
 * The match semantics for this class are intended to be close to Sailpoint's Hibernate filters. For example,
 * collection-valued properties will match single values they contain using a {@link Filter#eq(String, Object)}
 * filter. This means can still do <code>links.application.name == "Active Directory"</code>. (This requires
 * special handling because 'links.application.name' resolves to a List, which is not equal to a String. In
 * Hibernate, it resolves to an 'exists' SQL query. In this class, it is treated as a 'list contains'.)
 *
 * The property lookup in this class uses {@link Utilities#getProperty(Object, String)}, allowing any semantics
 * of that method's path syntax. Indexed variables may be used to get entries within list or map properties,
 * such as <code>affiliations[0]</code>. Null references are also gracefully ignored without error.
 *
 * SailPoint's filters have a syntax limitation around array indexing, e.g. links[0], and the Filter.compile()
 * API requires valid identifiers at every point in a path. This means that a path like 'links[0]' will fail
 * to compile with an exception. To work around this, you can pass an index in your path as '_X', e.g. 'links._0.name'
 * for index 0. However, If you build the Filter in code, such as with <code>Filter.eq</code>, you can safely use
 * 'forbidden' syntax like links[0].
 *
 * ----
 *
 * If a database lookup is needed, such as a subquery, it will be done transparently (this is the "hybrid" aspect
 * and it mostly delegates to the superclass), but they are avoided if possible. Subquery filters are restricted
 * to database-friendly properties, as they always need to perform a live query. Collection-criteria filters may
 * or may not have such a restriction, depending on the collection.
 *
 * IMPORTANT: The state in memory may differ from database state, such as if changes have not been committed,
 * so use caution when invoking hybrid queries.
 *
 * {@link Filter#collectionCondition(String, Filter)} is NOT supported and will throw an exception.
 *
 * ----
 *
 * If you invoke the constructor with 'allowObjectPropertyEquals', then special self-referential behavior
 * is enabled in 'eq', 'in', 'ne', and 'containsAll' Filters. If the equality check fails against the literal value,
 * the matcher will assume that the value is a property on the original context object. That property's value
 * is retrieved and the comparison is repeated. This isn't something that will come up often, but there is no
 * substitute when it does.
 *
 * ----
 *
 * IMPORTANT IMPORTANT IMPORTANT!!! MAINTAINER NOTE:
 * Do not modify this class unless you know what you are doing, because this matcher sits behind FakeContext,
 * which itself is behind the offline IIQCommon tests. You may break the offline tests. Verify everything.
 */
public class HybridObjectMatcher extends HybridReflectiveMatcher {

    private static final Log logger = LogFactory.getLog(HybridObjectMatcher.class);

    /**
     * Enables the special object property equals behavior if true (default false)
     */
    private final boolean allowObjectPropertyEquals;

    /**
     * The sailpoint context to use for querying if needed
     */
    private final SailPointContext context;

    /**
     * The context object, which must be attached to the context (or entirely detached)
     */
    private Object contextObject;

    /**
     * True if we should use tolerant paths (default true)
     */
    private final boolean tolerantPaths;

    /**
     * Constructor for the basic case where defaults are used for allowObjectPropertyEquals (false) and tolerantPaths (true)
     * @param context The Sailpoint context
     * @param filter The filter to evaluate
     */
    public HybridObjectMatcher(SailPointContext context, Filter filter) {
        this(context, filter, false, true);
    }

    /**
     * Constructor allowing the user to specify whether to allow object properties in equals
     * @param context The Sailpoint context
     * @param filter The filter to evaluate
     * @param allowObjectPropertyEquals If true, object property comparisons will be allowed
     */
    public HybridObjectMatcher(SailPointContext context, Filter filter, boolean allowObjectPropertyEquals) {
        this(context, filter, allowObjectPropertyEquals, true);
    }

    /**
     * Constructor allowing the user to specify all boolean options
     * @param context The Sailpoint context
     * @param filter The filter to evaluate
     * @param allowObjectPropertyEquals If true, object property comparisons will be allowed
     * @param tolerantPaths If true, walking paths resulting in an exception (e.g., can't read an object from the database) partway down the path will return null instead
     */
    public HybridObjectMatcher(SailPointContext context, Filter filter, boolean allowObjectPropertyEquals, boolean tolerantPaths) {
        super(context, filter);
        this.context = context;
        this.allowObjectPropertyEquals = allowObjectPropertyEquals;
        this.tolerantPaths = tolerantPaths;
    }


    /**
     * Walk the property tree and also resolve certain types of objects nested.
     *
     * @param leaf The filter from which to get the information
     * @param o The object to initially walk from
     * @return The property value associated with the path
     * @throws GeneralException If any lookup failures occur during path walking
     */
    @Override
    public Object getPropertyValue(Filter.LeafFilter leaf, Object o) throws GeneralException {
        String propertyPath = leaf.getProperty();
        Object propertyValue = Utilities.getProperty(o, propertyPath, tolerantPaths);
        // Magic conversion for comparing IDs to objects in Filters,
        // like Filter.eq("manager.id", identityObject) or Filter.eq("manager", managerId)
        if (leaf.getValue() != null) {
            if (leaf.getValue() instanceof String && propertyValue instanceof SailPointObject) {
                propertyValue = ((SailPointObject) propertyValue).getId();
            } else if (leaf.getValue() instanceof SailPointObject && propertyValue instanceof String) {
                if (context != null) {
                    SailPointObject spoLeaf = (SailPointObject) leaf.getValue();
                    SailPointObject spo = context.getObject(spoLeaf.getClass(), (String)propertyValue);
                    if (spo != null) {
                        propertyValue = spo;
                    }
                }
            }
        }
        return propertyValue;
    }

    /**
     * Returns true if the properties of the input object satisfy this Matcher's Filter.
     *
     * Filter property names should be specified in the path syntax supported by {@link Utilities#getProperty(Object, String)}.
     *
     * @see HybridReflectiveMatcher#matches(Object)
     */
    @Override
    public boolean matches(Object o) throws GeneralException {
        contextObject = o;
        return super.matches(o);
    }

    /**
     * Handles the 'containsAll' filter type. By default, defers to the superclass.
     *
     * If the normal behavior fails to match, and allowObjectPropertyEquals is true, the relevant
     * property is retrieved and the containsAll operation repeated against it.
     *
     * @param filter The filter to evaluate
     * @throws GeneralException on failures to read the attributes in question
     */
    @Override
    public void visitContainsAll(Filter.LeafFilter filter) throws GeneralException {
        super.visitContainsAll(filter);
        boolean result = this.evaluationStack.peek();
        if (!result) {
            if (logger.isTraceEnabled()) {
                Object actual = this.getPropertyValue(filter, this.objectToMatch);
                logger.trace("Failed to match containsAll() on " + actual);
            }
        }
        if (allowObjectPropertyEquals && !result && contextObject != null && filter.getValue() instanceof String) {
            this.evaluationStack.pop();
            // Try it as a lookup on the context object
            if (logger.isTraceEnabled()) {
                logger.trace("Failed to match containsAll() in default mode; attempting to use " + filter.getValue() + " as a property of the target object " + this.contextObject);
            }
            String propertyPath = (String) filter.getValue();
            Object propertyValue = Utilities.getProperty(contextObject, propertyPath, tolerantPaths);
            List<Object> propertyCollection = new ArrayList<>();
            if (propertyValue instanceof Collection) {
                propertyCollection.addAll((Collection<?>) propertyValue);
            } else if (propertyValue instanceof Object[]) {
                propertyCollection.addAll(Arrays.asList((Object[]) propertyValue));
            } else {
                propertyCollection.add(propertyValue);
            }
            Filter alteredFilter = Filter.containsAll(filter.getProperty(), propertyCollection);
            JavaPropertyMatcher jpm = new JavaPropertyMatcher((Filter.LeafFilter) alteredFilter);
            Object actual = this.getPropertyValue(filter, this.objectToMatch);
            if (logger.isTraceEnabled()) {
                logger.trace("Comparing " + actual + " with " + propertyCollection);
            }
            boolean matches = jpm.matches(actual);
            if (matches) {
                this.matchedValues = EntitlementCollection.mergeValues(filter.getProperty(), jpm.getMatchedValue(), this.matchedValues);
            }
            this.evaluationStack.push(matches);
        }
    }

    /**
     * Extends the OOTB behavior of 'equals' Filters for a couple of specific cases.
     *
     * First, if the object's property value is a Collection and the test parameter is
     * a String or a Boolean, we check to see if the Collection contains the single
     * value. This is the way Filters behave when translated to HQL, thanks to SQL
     * joins, so we want to retain that behavior here.
     *
     * Second, if we have allowed object properties to be used on both sides of the
     * 'equals' Filter, and there is still not a valid match, we attempt to evaluate
     * the test argument as an object property and compare those.
     *
     * @param filter The filter to check for equality
     * @throws GeneralException if an IIQ error occurs
     */
    @Override
    public void visitEQ(Filter.LeafFilter filter) throws GeneralException {
        super.visitEQ(filter);
        boolean result = this.evaluationStack.peek();

        if (!result) {
            if (filter.getValue() instanceof String || filter.getValue() instanceof Boolean) {
                evaluationStack.pop();
                if (logger.isTraceEnabled()) {
                    logger.trace("Failed to match eq() in default mode; attempting a Hibernate-like 'contains'");
                }
                Object actual = this.getPropertyValue(filter, this.objectToMatch);
                Object propertyValue = filter.getValue();

                boolean matches = false;
                if (actual instanceof Collection) {
                    if (filter.isIgnoreCase()) {
                        matches = Utilities.caseInsensitiveContains((Collection<? extends Object>) actual, propertyValue);
                    } else {
                        matches = Util.nullSafeContains(new ArrayList<>((Collection<?>) actual), propertyValue);
                    }
                }
                evaluationStack.push(matches);
            }
        }
        result = this.evaluationStack.peek();
        if (allowObjectPropertyEquals && !result && contextObject != null && filter.getValue() instanceof String) {
            if (logger.isTraceEnabled()) {
                logger.trace("Failed to match eq() in default and contains mode; attempting to use " + filter.getValue() + " as a property of the target object " + this.contextObject);
            }
            this.evaluationStack.pop();
            // Try it as a lookup on the context object
            String propertyPath = (String) filter.getValue();
            Object propertyValue = Utilities.getProperty(contextObject, propertyPath, tolerantPaths);
            Object actual = this.getPropertyValue(filter, this.objectToMatch);

            if (actual != null && propertyValue != null) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Comparing " + actual + " with " + propertyValue);
                }
                Filter derivedFilter = Filter.eq(filter.getProperty(), propertyValue);
                visitEQ((Filter.LeafFilter) derivedFilter);
            } else {
                evaluationStack.push(false);
            }
        }
    }

    /**
     * Performs the 'in' Filter operation, checking whether the value of the given property
     * is in the specified list of values.
     *
     * @param filter The 'in' Filter
     * @throws GeneralException if anything goes wrong
     */
    @Override
    public void visitIn(Filter.LeafFilter filter) throws GeneralException {
        super.visitIn(filter);
        boolean result = this.evaluationStack.peek();
        if (!result) {
            Object actual = this.getPropertyValue(filter, this.objectToMatch);
            if (actual instanceof Collection && filter.getValue() instanceof Collection) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Failed to match in() in default mode; attempting to use a Hibernate-style contains");
                }
                evaluationStack.pop();
                // This makes 'links.application.name.in({"HR"}) work
                boolean matches = false;
                for(Object item : (Collection<Object>)actual) {
                    if (logger.isTraceEnabled()) {
                        logger.trace("Checking whether " + item + " is in collection " + filter.getValue());
                    }
                    JavaPropertyMatcher jpm = new JavaPropertyMatcher(filter);
                    matches = jpm.matches(item);
                    if (matches) {
                        this.matchedValues = EntitlementCollection.mergeValues(filter.getProperty(), jpm.getMatchedValue(), this.matchedValues);
                        break;
                    }
                }
                evaluationStack.push(matches);
            }
        }
        result = this.evaluationStack.peek();
        if (allowObjectPropertyEquals && !result && contextObject != null && filter.getValue() instanceof Collection) {
            this.evaluationStack.pop();
            // Try it as a lookup on the context object
            Collection<?> filterValue = (Collection<?>)filter.getValue();
            if (filterValue != null) {
                for(Object propertyPathObj : filterValue) {
                    String propertyPath = Utilities.safeString(propertyPathObj);
                    if (logger.isTraceEnabled()) {
                        logger.trace("Failed to match in() in default and contains mode; attempting to use " + propertyPath + " as a property of the target object " + this.contextObject);
                    }
                    Object propertyValue = Utilities.getProperty(contextObject, propertyPath, tolerantPaths);
                    List<Object> propertyCollection = new ArrayList<>();
                    if (propertyValue instanceof Collection) {
                        propertyCollection.addAll((Collection<?>) propertyValue);
                    } else if (propertyValue instanceof Object[]) {
                        propertyCollection.addAll(Arrays.asList((Object[]) propertyValue));
                    } else {
                        propertyCollection.add(propertyValue);
                    }
                    if (logger.isTraceEnabled()) {
                        Object actual = this.getPropertyValue(filter, this.objectToMatch);
                        logger.trace("Checking whether " + actual + " is in collection " + propertyCollection);
                    }
                    Filter alteredFilter = Filter.in(filter.getProperty(), propertyCollection);
                    visitIn((Filter.LeafFilter) alteredFilter);
                    result = this.evaluationStack.peek();
                    if (result) {
                        break;
                    } else {
                        evaluationStack.pop();
                    }
                }
            } else {
                this.evaluationStack.push(false);
            }
        }
    }

    /**
     * Performs a 'ne' (not equals) evaluation.
     *
     * @param filter The not-equals Filter to evaluate
     * @throws GeneralException if anything fails
     */
    @Override
    public void visitNE(Filter.LeafFilter filter) throws GeneralException {
        super.visitNE(filter);
        boolean result = this.evaluationStack.peek();
        if (allowObjectPropertyEquals && !result && contextObject != null && filter.getValue() instanceof String) {
            this.evaluationStack.pop();
            // Try it as a lookup on the context object
            String propertyPath = (String) filter.getValue();
            Object propertyValue = Utilities.getProperty(contextObject, propertyPath, tolerantPaths);
            Object actual = this.getPropertyValue(filter, this.objectToMatch);
            if (!Sameness.isSame(propertyValue, actual, filter.isIgnoreCase())) {
                evaluationStack.push(true);
            } else {
                evaluationStack.push(false);
            }
        }
    }

    /**
     * Performs a subquery against the database.
     *
     * @see HybridReflectiveMatcher#visitSubquery
     */
    @Override
    public void visitSubquery(Filter.LeafFilter filter) throws GeneralException {
        if (context != null) {
            if (filter.getSubqueryClass() != null && SailPointObject.class.isAssignableFrom(filter.getSubqueryClass())) {
                QueryOptions options = new QueryOptions();
                options.addFilter(filter.getSubqueryFilter());
                Class<? extends SailPointObject> subqueryClass = (Class<? extends SailPointObject>) filter.getSubqueryClass();
                List<Object> subqueryResults = new ArrayList<>();
                Iterator<Object[]> subqueryResultIterator = context.search(subqueryClass, options, Arrays.asList(filter.getSubqueryProperty()));
                while (subqueryResultIterator.hasNext()) {
                    Object[] row = subqueryResultIterator.next();
                    subqueryResults.add(row[0]);
                }
                if (subqueryResults.isEmpty()) {
                    this.evaluationStack.push(false);
                } else {
                    // Filter.in contains magic, which means it can return a singular IN or
                    // an OR(IN, IN, IN...), depending on how many items are in the list
                    Filter inFilter = Filter.in(filter.getProperty(), subqueryResults);
                    if (inFilter instanceof Filter.LeafFilter) {
                        visitIn((Filter.LeafFilter) inFilter);
                    } else {
                        visitOr((Filter.CompositeFilter) inFilter);
                    }
                }
            } else {
                throw new IllegalArgumentException("Subquery class must be a child of SailPointObject");
            }
        }
    }
}
