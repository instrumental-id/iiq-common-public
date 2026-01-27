package com.identityworksllc.iiq.common.plugin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.identityworksllc.iiq.common.*;
import com.identityworksllc.iiq.common.logging.LogCapture;
import com.identityworksllc.iiq.common.logging.SLogger;
import com.identityworksllc.iiq.common.plugin.annotations.AuthorizeAll;
import com.identityworksllc.iiq.common.plugin.annotations.AuthorizeAny;
import com.identityworksllc.iiq.common.plugin.annotations.AuthorizedBy;
import com.identityworksllc.iiq.common.plugin.annotations.NoReturnValue;
import com.identityworksllc.iiq.common.plugin.annotations.ResponsesAllowed;
import com.identityworksllc.iiq.common.plugin.vo.ExpandedDate;
import com.identityworksllc.iiq.common.plugin.vo.RestObject;
import org.apache.commons.logging.LogFactory;
import org.apache.logging.log4j.ThreadContext;
import org.springframework.core.annotation.AnnotationUtils;
import sailpoint.api.Matchmaker;
import sailpoint.api.Meter;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.api.logging.SyslogThreadLocal;
import sailpoint.authorization.Authorizer;
import sailpoint.authorization.CapabilityAuthorizer;
import sailpoint.authorization.UnauthorizedAccessException;
import sailpoint.object.*;
import sailpoint.rest.BaseResource;
import sailpoint.rest.plugin.BasePluginResource;
import sailpoint.tools.GeneralException;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;

import javax.faces.FactoryFinder;
import javax.faces.component.UIViewRoot;
import javax.faces.context.FacesContext;
import javax.faces.context.FacesContextFactory;
import javax.faces.lifecycle.Lifecycle;
import javax.faces.lifecycle.LifecycleFactory;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.*;

/**
 * This class is the common base class for all IIQCommon-compliant plugin REST
 * resources. It contains numerous enhancements over IIQ's original base plugin
 * resource class, notably {@link #handle(PluginAction)}.
 *
 * See the provided documentation `PLUGIN-RESOURCES.adoc` for much more detail.
 *
 * Your plugins REST resource classes must extend this class to make use of its
 * functions. The following is an example of handle():
 *
 * ```
 * {@literal @}GET
 * {@literal @}Path("endpoint/path")
 * public Response endpointMethod(@QueryParam("param") String parameter) {
 *     return handle(() -> {
 *     	   // Your lambda body can do anything you need. Here we just call
 *     	   // some example method.
 *         List<String> output = invokeBusinessLogicHere();
 *
 *         // Will be automatically transformed into a JSON response
 *         return output;
 *     });
 * }
 * ```
 */
@SuppressWarnings("unused")
public abstract class BaseCommonPluginResource extends BasePluginResource implements CommonExtendedPluginContext {

	/**
	 * The interface that must be implemented by any plugin action passed
	 * to {@link #handle(Authorizer, PluginAction)}. In most cases, you will
	 * implement this as a lambda expression.
	 */
	@FunctionalInterface
	public interface PluginAction {
		/**
		 * Executes the action
		 * @return Any values to return to the client
		 * @throws Exception if any failure occur
		 */
		Object execute() throws Exception;
	}

	/**
	 * A hack to set the current FacesContext object. The method to set the current
	 * instance is "protected", so it can only be accessed from within a subclass.
	 */
	private static abstract class InnerFacesContext extends FacesContext {
		/**
		 * Sets the current Faces Context within the context of this class,
		 * which allows us access to a protected superclass static method
		 * @param facesContext The new FacesContext
		 */
		protected static void setFacesContextAsCurrentInstance(FacesContext facesContext) {
			FacesContext.setCurrentInstance(facesContext);
		}
	}
	/**
	 * If true, logs will be captured in handle() and appended to any error messages
	 */
	private final ThreadLocal<Boolean> captureLogs;
	/**
	 * The constructed FacesContext if there is one
	 */
	private final ThreadLocal<FacesContext> constructedContext;
	/**
	 * If true, logs will be captured in handle() and logged to the usual logger output even if they would not normally be
	 */
	private final ThreadLocal<Boolean> forwardLogs;
	/**
	 * An enhanced logger available to all plugins
	 */
	protected final SLogger log;
	/**
	 * Log messages captured to return with any errors
	 */
	private final ThreadLocal<List<String>> logMessages;
	/**
	 * A plugin authorization checker, if present
	 */
	private PluginAuthorizationCheck pluginAuthorizationCheck;
	/**
	 * Information about what resource is about to be invoked
	 */
	@Context
	protected ResourceInfo resourceInfo;
	/**
	 * The {@link HttpServletResponse} associated with the current invocation
	 */
	@Context
	protected HttpServletResponse response;
	/**
	 * The servlet context associated with the current invocation
	 */
	@Context
	protected ServletContext servletContext;
	/**
	 * The plugin resource
	 */
	public BaseCommonPluginResource() {
		this.log = new SLogger(LogFactory.getLog(this.getClass()));
		this.logMessages = new ThreadLocal<>();
		this.captureLogs = new ThreadLocal<>();
		this.forwardLogs = new ThreadLocal<>();
		this.constructedContext = new ThreadLocal<>();
	}
	
	/**
	 * The plugin resource
	 * @param parent The parent resource, used to inherit IIQ configuration
	 */
	public BaseCommonPluginResource(BaseResource parent) {
		super(parent);
		this.log = new SLogger(LogFactory.getLog(this.getClass()));
		this.logMessages = new ThreadLocal<>();
		this.captureLogs = new ThreadLocal<>();
		this.forwardLogs = new ThreadLocal<>();
		this.constructedContext = new ThreadLocal<>();
	}

	/**
	 * Transforms a date response into a known format
	 *
	 * @param response The date response
	 * @return if any failures occur
	 */
	private static Response transformDate(Date response) {
		return Response.ok().entity(new ExpandedDate(response)).build();
	}

	/**
	 * Authorizes the given endpoint class and method according to the custom
	 * {@link AuthorizedBy}, {@link AuthorizeAll}, or {@link AuthorizeAny} annotations
	 * present on it.
	 *
	 * If authorization fails, an {@link UnauthorizedAccessException} will be thrown.
	 * Otherwise the method will return silently.
	 *
	 * If the logged in user is null, authorization silently succeeds.
	 *
	 * @param endpointClass The endpoint class from {@link ResourceInfo}
	 * @param endpointMethod The endpoint method from {@link ResourceInfo}
	 * @throws UnauthorizedAccessException if authorization fails
	 * @throws GeneralException if any other system failures occur during authorization
	 */
	private void authorize(Class<?> endpointClass, Method endpointMethod) throws UnauthorizedAccessException, GeneralException {
		Identity me = super.getLoggedInUser();
		if (me == null) {
			return;
		}

		// NOTE: The difference between get and find is that find searches up the class
		// hierarchy and get searches only the local level.
		AuthorizedBy authorizedBy = AnnotationUtils.getAnnotation(endpointMethod, AuthorizedBy.class);
		AuthorizeAll authorizeAll = null;
		AuthorizeAny authorizeAny = null;

		if (authorizedBy == null) {
			authorizeAll = AnnotationUtils.getAnnotation(endpointMethod, AuthorizeAll.class);
			authorizeAny = AnnotationUtils.getAnnotation(endpointMethod, AuthorizeAny.class);
		}

		if (authorizedBy == null && authorizeAll == null && authorizeAny == null) {
			authorizedBy = AnnotationUtils.findAnnotation(endpointClass, AuthorizedBy.class);

			if (authorizedBy == null) {
				authorizeAll = AnnotationUtils.findAnnotation(endpointClass, AuthorizeAll.class);
				authorizeAny = AnnotationUtils.findAnnotation(endpointClass, AuthorizeAny.class);
			}
		}

		if (authorizedBy != null) {
			if (!isAuthorized(authorizedBy, me)) {
				throw new UnauthorizedAccessException("User is not authorized to access this endpoint");
			}
		} else if (authorizeAll != null) {
			if (authorizeAny != null) {
				throw new GeneralException("BAD CONFIGURATION: Endpoint method " + endpointClass.getName() + "." + endpointMethod.getName() + " is attached to both @AuthorizeAll and @AuthorizeAny annotations");
			}
			for(AuthorizedBy ab : authorizeAll.value()) {
				if (!isAuthorized(ab, me)) {
					throw new UnauthorizedAccessException("User is not authorized to access this endpoint");
				}
			}
		} else if (authorizeAny != null) {
			boolean match = false;
			for(AuthorizedBy ab : authorizeAny.value()) {
				if (isAuthorized(ab, me)) {
					match = true;
					break;
				}
			}
			if (!match) {
				throw new UnauthorizedAccessException("User is not authorized to access this endpoint");
			}
		}
	}

	/**
	 * Builds a new FacesContext based on the HTTP request and response. The value
	 * returned by this method will be cleaned up (released) automatically after
	 * running the action in {@link #handle(PluginAction)}.
	 *
	 * See here: <a href="https://myfaces.apache.org/wiki/core/user-guide/jsf-and-myfaces-howtos/backend/access-facescontext-from-servlet.html">Access FacesContext from Servlet</a>
	 *
	 * @return A new FacesContext
	 */
	private FacesContext buildFacesContext()
	{
		FacesContextFactory contextFactory = (FacesContextFactory)FactoryFinder.getFactory(FactoryFinder.FACES_CONTEXT_FACTORY);
		LifecycleFactory lifecycleFactory = (LifecycleFactory)FactoryFinder.getFactory(FactoryFinder.LIFECYCLE_FACTORY);
		Lifecycle lifecycle = lifecycleFactory.getLifecycle(LifecycleFactory.DEFAULT_LIFECYCLE);
		FacesContext facesContext = contextFactory.getFacesContext(this.servletContext, request, response, lifecycle);
		InnerFacesContext.setFacesContextAsCurrentInstance(facesContext);
		UIViewRoot view = facesContext.getApplication().getViewHandler().createView(facesContext, "/login");
		facesContext.setViewRoot(view);
		constructedContext.set(facesContext);
		return facesContext;
	}

	/**
	 * Checks access to the given "thing" and throws an {@link UnauthorizedAccessException} if access is not granted.
	 *
	 * @param thingAccessConfig The thing access configuration map
	 * @throws GeneralException if the access check fails for reasons unrelated to unauthorized access (e.g. script failure)
	 */
	protected void checkThingAccess(Map<String, Object> thingAccessConfig) throws GeneralException {
		checkThingAccess(null, thingAccessConfig);
	}


	/**
	 * Checks access to the given "thing" and throws an {@link UnauthorizedAccessException} if access is not granted
	 *
	 * @param target The target Identity, if any
	 * @param thingAccessConfig The thing access configuration map
	 * @throws GeneralException if the access check fails for reasons unrelated to unauthorized access (e.g. script failure)
	 */
	protected void checkThingAccess(Identity target, Map<String, Object> thingAccessConfig) throws GeneralException {
		if (!ThingAccessUtils.checkThingAccess(this, target, thingAccessConfig)) {
			throw new UnauthorizedAccessException();
		}
	}

    /**
     * Allows subclasses to add custom audit information to the audit map
     * @param auditMap The audit map
     */
    protected void customizeAuditMap(Map<String, Object> auditMap) {
        // Subclasses may override to add custom audit information
    }

	/**
	 * Allows successful responses to be customized by overriding this method. By
	 * default, simply returns the input Response object.
	 * <p>
	 * Customizations should generally begin by invoking {@link Response#fromResponse(Response)}.
	 *
	 * @param actionResult The output of the {@link PluginAction} implementation in handle()
	 * @param restResponse The API output
	 */
	protected Response customizeResponse(Object actionResult, Response restResponse) {
		return restResponse;
	}

	/**
	 * Gets the attributes of either a {@link Configuration} or {@link Custom} object
	 * with the given name, in that order. If no such object exists, an empty Attributes
	 * object will be returned.
	 *
	 * This method should never return null.
	 *
	 * @param name Configuration name
	 * @return The attributes of the configuration
	 * @throws GeneralException if any lookup failures occur
	 */
	public Attributes<String, Object> getConfiguration(String name) throws GeneralException {
		Configuration c1 = getContext().getObjectByName(Configuration.class, name);
		if (c1 != null && c1.getAttributes() != null) {
			return c1.getAttributes();
		}
		Custom c2 = getContext().getObjectByName(Custom.class, name);
		if (c2 != null && c2.getAttributes() != null) {
			return c2.getAttributes();
		}
		return new Attributes<>();
	}

	/**
	 * Gets the configuration setting from the default plugin Configuration object or else from the plugin settings
	 * @param settingName The setting to retrieve
	 * @return The setting value
	 * @see #getConfigurationName()
	 */
	@Override
	public boolean getConfigurationBool(String settingName) {
		return new ExtendedPluginContextHelper(getPluginName(), getContext(), this, this::getConfigurationName).getConfigurationBool(settingName);
	}

	/**
	 * Gets the configuration setting from the default plugin Configuration object or else from the plugin settings
	 * @param settingName The setting to retrieve
	 * @return The setting value
	 * @see #getConfigurationName()
	 */
	@Override
	public int getConfigurationInt(String settingName) {
		return new ExtendedPluginContextHelper(getPluginName(), getContext(), this, this::getConfigurationName).getConfigurationInt(settingName);
	}
	
	/**
	 * Returns the name of the Configuration object for this plugin. It defaults to
	 * `Plugin Configuration [plugin name]`, but a subclass may want to override it.
	 *
	 * The configuration object named here is used by the following methods:
	 *
	 * * {@link #getConfigurationObject(String)}
	 * * {@link #getConfigurationBool(String)}
	 * * {@link #getConfigurationString(String)}
	 * * {@link #getConfigurationInt(String)}
	 *
	 * @return The name of the plugin configuration
	 */
	protected String getConfigurationName() {
		return "Plugin Configuration " + getPluginName();
	}
	
	/**
	 * Gets the given configuration setting as an Object
	 * @param settingName The setting to retrieve as an Object
	 * @return The object
	 * @see #getConfigurationName()
	 */
	@Override
	public <T> T getConfigurationObject(String settingName) {
		return (T)new ExtendedPluginContextHelper(getPluginName(), getContext(), this, this::getConfigurationName).getConfigurationObject(settingName);
	}
	
	/**
	 * Gets the configuration setting from the default plugin Configuration object or else from the plugin settings
	 * @param settingName The setting to retrieve
	 * @return The setting value
	 * @see #getConfigurationName()
	 */
	@Override
	public String getConfigurationString(String settingName) {
		return new ExtendedPluginContextHelper(getPluginName(), getContext(), this, this::getConfigurationName).getConfigurationString(settingName);
	}

	/**
	 * Returns a distinct object matching the given filter. If the results are not distinct
	 * (i.e. more than one result is returned), a 400 error will be thrown. If there are no
	 * matching objects, a 404 error will be thrown.
	 *
	 * @param <T> The class to search for
	 * @param cls The class to search for
	 * @param filter A Filter for use with searching
	 * @return The resulting object (unless an error occurs)
	 * @throws GeneralException if an error occurs, or if there are no results, or if there are too many results
	 */
	protected <T extends SailPointObject> T getDistinctObject(Class<T> cls, Filter filter) throws GeneralException {
		QueryOptions qo = new QueryOptions();
		qo.add(filter);
		qo.setResultLimit(2);
		List<T> results = getContext().getObjects(cls, qo);
		if (results == null || results.size() == 0) {
			throw new ObjectNotFoundException(cls, filter.toString());
		} else if (results.size() > 1) {
			throw new TooManyResultsException(cls, filter.toString(), results.size());
		}
		return results.get(0);
	}
	
	/**
	 * Returns a distinct object of the given type matching the given filter. If the results are not distinct (i.e. more than one result is returned), a 400 error will be thrown. If there are no matching objects, a 404 error will be thrown.
	 * @param <T> The class to search for
	 * @param cls The class to search for
	 * @param filter A QueryOptions for use with searching
	 * @return The resulting object (unless an error occurs)
	 * @throws GeneralException if an error occurs, or if there are no results, or if there are too many results
	 */
	protected <T extends SailPointObject> T getDistinctObject(Class<T> cls, QueryOptions filter) throws GeneralException {
		List<T> results = getContext().getObjects(cls, filter);
		if (results == null || results.size() == 0) {
			throw new ObjectNotFoundException(cls, filter.toString());
		} else if (results.size() > 1) {
			throw new TooManyResultsException(cls, filter.toString(), results.size());
		}
		return results.get(0);
	}
	
	/**
	 * Get the standard exception mapping for output to the REST API caller. By
	 * default this will include information about the exception, the quick key
	 * used to look it up in the syslog query UI, and any log messages if log
	 * capturing is enabled.
	 *
	 * Subclasses may override this to add behavior, but most API clients written
	 * by IDW are expecting this output.
	 *
	 * @param t The exception to map
	 * @return The resulting map
	 */
	protected Map<String, Object> getExceptionMapping(Throwable t) {
		Map<String, Object> responseMap = CommonPluginUtils.getExceptionMapping(t, false);
		responseMap.put("logs", logMessages.get());
		return responseMap;
	}
	
	/**
	 * Gets the current FacesContext if there is one or builds one if there is not.
	 * If the method needs to build a temporary FacesContext, it will be destroyed
	 * at the end of your `handle()` call to avoid memory leaks.
	 *
	 * @return A working FacesContext
	 */
	protected FacesContext getFacesContext() {
		FacesContext fc = FacesContext.getCurrentInstance();
		if (fc != null) {
			return fc;
		}
		return buildFacesContext();
	}
	
	/**
	 * A wrapper around {@link SailPointContext#getObject(Class, String)} that
	 * will throw a 404 exception if the search returns no records.
	 *
	 * @param <T> The class to search for
	 * @param cls The class to search for
	 * @param nameOrId The name or ID to search
	 * @return The object
	 * @throws ObjectNotFoundException if no results are found (results in a 404 in handle())
	 * @throws GeneralException if a search failure occurs
	 */
	protected <T extends SailPointObject> T getObject(Class<T> cls, String nameOrId) throws GeneralException {
		T object = getContext().getObject(cls, nameOrId);
		if (object == null) {
			throw new ObjectNotFoundException(cls, nameOrId);
		}
		return object;
	}

	/**
	 * A wrapper around {@link SailPointContext#getObject(Class, String)} that will throw a 404 exception if the search returns no records
	 * @param <T> The class to search for
	 * @param cls The class to search for
	 * @param nameOrId The name or ID to search
	 * @return The object
	 * @throws ObjectNotFoundException if no results are found (results in a 404 in handle())
	 * @throws GeneralException if a search failure occurs
	 */
	protected <T extends SailPointObject> T getObjectById(Class<T> cls, String nameOrId) throws GeneralException {
		T object = getContext().getObjectById(cls, nameOrId);
		if (object == null) {
			throw new ObjectNotFoundException(cls, nameOrId);
		}
		return object;
	}

	/**
	 * A wrapper around {@link SailPointContext#getObject(Class, String)} that will throw a 404 exception if the search returns no records
	 * @param <T> The class to search for
	 * @param cls The class to search for
	 * @param nameOrId The name or ID to search
	 * @return The object
	 * @throws ObjectNotFoundException if no results are found (results in a 404 in handle())
	 * @throws GeneralException if a search failure occurs
	 */
	protected <T extends SailPointObject> T getObjectByName(Class<T> cls, String nameOrId) throws GeneralException {
		T object = getContext().getObjectByName(cls, nameOrId);
		if (object == null) {
			throw new ObjectNotFoundException(cls, nameOrId);
		}
		return object;
	}

	/**
	 * Retrieves the {@link PluginAuthorizationCheck} previously set by a subclass.
	 * @return The configured PluginAuthorizationCheck, or null if none is set.
	 */
	public PluginAuthorizationCheck getPluginAuthorizationCheck() {
		return pluginAuthorizationCheck;
	}

	/**
	 * Safely retrieves the utility class in question. The utility class should implement a
	 * one-argument constructor taking a SailPointContext.
	 *
	 * @param <T> The utility type
	 * @param cls The class to retrieve
	 * @return An instance of the utility class
	 */
	protected <T extends AbstractBaseUtility> T getUtility(Class<T> cls) {
		try {
			return cls.getConstructor(SailPointContext.class).newInstance(getContext());
		} catch(Exception e) {
			throw new IllegalArgumentException(cls.getName() + " does not appear to be a utility", e);
		}
	}

	/**
	 * This entry point method is responsible for executing the given action after checking
     * the Authorizers. The action should be specified as a Java lambda expression.
     *
	 * ```
	 * {@literal @}GET
	 * {@literal @}Path("endpoint/path")
	 * public Response endpointMethod(@QueryParam("param") String parameter) {
	 *     return handle(() -> {
	 *         List<String> output = invokeBusinessLogicHere();
	 *
	 *         // Will be automatically transformed into a JSON response
	 *         return output;
	 *     });
	 * }
	 * ```
	 *
	 * This method performs the following actions:
	 *
	 * 1) If log forwarding or capturing is enabled, switches it on for the current thread.
	 * 2) Starts a meter for checking performance of your action code.
	 * 3) Checks any Authorizers specified via the 'authorizer' parameter, class configuration, or an annotation.
	 * 4) Executes the provided {@link PluginAction}, usually a lambda expression.
	 * 5) Transforms the return value into a {@link Response}, handling both object output and thrown exceptions.
	 * 6) Finalizes the Meters and log capturing.
	 *
	 * If any authorizer fails, a 403 Forbidden response will be returned.
	 *
	 * @param authorizer If not null, the given authorizer will run first. A {@link Status#FORBIDDEN} {@link Response} will be returned if the authorizer fails
	 * @param action The action to execute, which should return the output of this API endpoint.
	 * @return The REST {@link Response}, populated according to the contract of this method
	 */
	protected final Response handle(Authorizer authorizer, PluginAction action) {
		if (resourceInfo != null) {
			Class<?> endpointClass = this.getClass();
			Method endpointMethod = resourceInfo.getResourceMethod();

			if (log.isTraceEnabled()) {
				log.trace("Entering handle() for REST API endpoint: Class = " + endpointClass.getName() + ", method = " + endpointMethod.getName());
			}
		}
		final String _meterName = "pluginRest:" + request.getRequestURI();
		boolean shouldMeter = shouldMeter(request);
        boolean shouldAudit = shouldAudit(request);

        Map<String, Object> auditMap = new HashMap<>();
        try {
			if (forwardLogs.get() != null && forwardLogs.get()) {
				LogCapture.addLoggers(this.getClass().getName());
				LogCapture.startInterception(message -> log.warn("{0}  {1} {2} - {3}", message.getDate(), message.getLevel(), message.getSource(), message.getMessage()));
			} else if (captureLogs.get() != null && captureLogs.get()) {
				LogCapture.addLoggers(this.getClass().getName());
				LogCapture.startInterception();
			}
			if (shouldMeter) {
				Meter.enter(_meterName);
			}

            // Allows us to trace these operations through their whole existence
            ThreadContext.push(LoggingConstants.LOG_CTX_ID, UUID.randomUUID().toString());
            ThreadContext.put(LoggingConstants.LOG_MDC_USER, getLoggedInUserName());
            ThreadContext.put(LoggingConstants.LOG_MDC_USER_DISPLAY, getLoggedInUser().getDisplayName());
            ThreadContext.put(LoggingConstants.LOG_MDC_PLUGIN, getPluginName());
            ThreadContext.put(LoggingConstants.LOG_MDC_URI, request.getRequestURI());

            try {
                auditMap.put(LoggingConstants.LOG_MDC_URI, request.getRequestURI());
                auditMap.put("httpMethod", request.getMethod());
                auditMap.put(LoggingConstants.LOG_MDC_USER, getLoggedInUserName());
                auditMap.put(LoggingConstants.LOG_MDC_PLUGIN, getPluginName());

				boolean hasReturnValue = true;
				List<Class<?>> allowedReturnTypes = null;
				try {
					if (resourceInfo != null) {
						Class<?> endpointClass = this.getClass();
						Method endpointMethod = resourceInfo.getResourceMethod();

                        auditMap.put(LoggingConstants.LOG_MDC_ENDPOINT_CLASS, endpointClass.getName());
                        auditMap.put(LoggingConstants.LOG_MDC_ENDPOINT_METHOD, endpointMethod.getName());

                        ThreadContext.put(LoggingConstants.LOG_MDC_ENDPOINT_CLASS, endpointClass.getName());
                        ThreadContext.put(LoggingConstants.LOG_MDC_ENDPOINT_METHOD, endpointMethod.getName());

						authorize(endpointClass, endpointMethod);
						if (endpointClass.isAnnotationPresent(NoReturnValue.class) || endpointMethod.isAnnotationPresent(NoReturnValue.class)) {
							hasReturnValue = false;
						}
						List<Class<?>> allowedClasses = new ArrayList<>();
						if (endpointClass.isAnnotationPresent(ResponsesAllowed.class)) {
							Class<?>[] data = endpointClass.getAnnotation(ResponsesAllowed.class).value();
							if (data != null) {
								allowedClasses.addAll(Arrays.asList(data));
							}
						}
						if (endpointMethod.isAnnotationPresent(ResponsesAllowed.class)) {
							Class<?>[] data = endpointMethod.getAnnotation(ResponsesAllowed.class).value();
							if (data != null) {
								allowedClasses.addAll(Arrays.asList(data));
							}
						}

						if (!allowedClasses.isEmpty()) {
							allowedReturnTypes = allowedClasses;
							if (log.isTraceEnabled()) {
								log.trace("Allowed return value types: " + allowedReturnTypes);
							}
						}
					}
					if (authorizer != null) {
						// Method-level authorizer
						authorize(authorizer);
					}
					if (pluginAuthorizationCheck != null) {
						// Class-level default authorizer
						pluginAuthorizationCheck.checkAccess();
					}
					if (this instanceof PluginAuthorizationCheck) {
						((PluginAuthorizationCheck) this).checkAccess();
					}
					if (this instanceof Authorizer) {
						authorize((Authorizer)this);
					}

                    if (shouldAudit) {
                        customizeAuditMap(auditMap);
                        com.fasterxml.jackson.databind.ObjectMapper mapper = new ObjectMapper();
                        String auditJson = mapper.writeValueAsString(auditMap);
                        log.warn("API Audit: {0}", auditJson);
                        Syslogger.logEvent(this.getClass(), auditJson, null, Syslogger.EVENT_LEVEL_WARN);
                    }

					Object actionResult = action.execute();

					Response restResult;

					try {
						if (log.isTraceEnabled()) {
							log.trace("Entering user-defined handle() body");
						}
						restResult = handleResult(actionResult, hasReturnValue, allowedReturnTypes);
					} finally {
						if (log.isTraceEnabled()) {
							log.trace("Exiting user-defined handle() body");
						}
					}

					return customizeResponse(actionResult, restResult);
				} catch(Exception e) {
					// Log so that it makes it into the captured logs, if any exist
					log.handleException(e);
					Syslogger.logEvent(this.getClass(), "Error in REST API: " + e.getClass().getName(), e);
					throw e;
				}
			} finally {
				logMessages.set(LogCapture.stopInterception());
				if (constructedContext.get() != null && !constructedContext.get().isReleased()) {
					constructedContext.get().release();
				}
			}
		} catch(UnauthorizedAccessException | SecurityException e) {
            if (shouldAudit) {
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new ObjectMapper();
                    String auditJson = mapper.writeValueAsString(auditMap);
                    log.warn("Unauthorized access to API: {0}", auditJson);
                } catch(JsonProcessingException e2) {
                    log.error("Caught a JSON exception attempting to audit a previous exception", e2);
                }
            }
			return handleException(Response.status(Status.FORBIDDEN), e);
		} catch(ObjectNotFoundException | NotFoundException e) {
			return handleException(Response.status(Status.NOT_FOUND), e);
		} catch(IllegalArgumentException e) {
			return handleException(Response.status(Status.BAD_REQUEST), e);
		} catch(Exception e) {
			return handleException(e);
		} finally {
            ThreadContext.pop();
            ThreadContext.clearMap();
			if (shouldMeter) {
				Meter.exit(_meterName);
			}
			Meter.publishMeters();
			// Allow garbage collection
            forwardLogs.remove();
            captureLogs.remove();
            logMessages.remove();
            constructedContext.remove();
		}
	}

    /**
	 * A wrapper method to handle plugin inputs. This is identical to invoking
	 * {@link #handle(Authorizer, PluginAction)} with a null Authorizer. See
	 * the documentation for that variant for more detail.
	 *
	 * Your {@link PluginAction} should be a Java lambda containing the actual
	 * business logic of your API endpoint. All method parameters will be available
	 * to it, as well as any class level attributes and effectively-final variables
	 * in the endpoint method.
	 *
	 * @param action The action to execute after passing authorization checks
	 * @return A valid JAX-RS {@link Response} object depending on the output of the PluginAction
	 */
	protected final Response handle(PluginAction action) {
		return handle(null, action);
	}

	/**
	 * Handles an exception by logging it and returning a Response with exception details
	 * @param t The exception to handle
	 * @return A JAX-RS response appropriate to the exception
	 */
	protected final Response handleException(ResponseBuilder responseBuilder, Throwable t) {
		Map<String, Object> responseMap = getExceptionMapping(t);
		return responseBuilder.entity(responseMap).build();
	}

	/**
	 * Handles an exception by logging it and returning a Response with exception details
	 * @param t The exception to handle
	 * @return A JAX-RS response appropriate to the exception
	 */
	protected Response handleException(Throwable t) {
		return handleException(Response.serverError(), t);
	}

	/**
	 * Handles the result value by wrapping recognized objects into a Response and returning
	 * that. Response entities are processed via IIQ's {@link sailpoint.rest.jaxrs.JsonMessageBodyWriter},
	 * which passes them through the Flexjson library.
	 *
	 * @param hasReturnValue True unless the method specifies {@literal '@'}{@link NoReturnValue}
	 * @param response The output returned from the body of the handle method
	 * @return The resulting Response object
	 * @throws GeneralException if any failures occur processing the response
	 */
	private Response handleResult(Object response, boolean hasReturnValue, List<Class<?>> allowedReturnTypes) throws GeneralException {
		if (hasReturnValue) {
			if (response instanceof Response) {
				return ((Response) response);
			} else if (response instanceof ErrorResponse) {
				// Special wrapper allowing methods to return a non-OK, but still non-exceptional response
				ErrorResponse<?> errorResponse = (ErrorResponse<?>) response;
				Response metaResponse = handleResult(errorResponse.getWrappedObject(), true, allowedReturnTypes);

				return Response.fromResponse(metaResponse).status(errorResponse.getResponseCode()).build();
			} else if (response instanceof Map || response instanceof Collection || response instanceof String || response instanceof Number || response instanceof Enum) {
				return Response.ok().entity(response).build();
			} else if (response instanceof Date) {
				return transformDate((Date) response);
			} else if (response instanceof AbstractXmlObject) {
				Map<String, Object> responseMap = new HashMap<>();
				responseMap.put("xml", ((AbstractXmlObject) response).toXml());
				if (response instanceof SailPointObject) {
					SailPointObject spo = ((SailPointObject) response);
					responseMap.put("id", spo.getId());
					responseMap.put("type", ObjectUtil.getTheRealClass(spo).getSimpleName());
					responseMap.put("name", spo.getName());
				}
				return Response.ok().entity(responseMap).build();
			} else if (response instanceof RestObject) {
				return Response.ok().entity(response).build();
			} else if (response instanceof Exception) {
				return handleException((Exception) response);
			}

			if (response != null) {
				if (!Util.isEmpty(allowedReturnTypes)) {
					for (Class<?> type : allowedReturnTypes) {
						if (Utilities.isAssignableFrom(type, response.getClass())) {
							return Response.ok().entity(response).build();
						}
					}
				}
			}

			// NOTE: It is plausible that 'response' is null here. This is the only way to
			// allow both null and non-null outputs from the same REST API method.
			if (isAllowedOutput(response)) {
				return Response.ok().entity(response).build();
			}

			if (response == null) {
				log.warn("REST API output is null, but null outputs were not explicitly allowed with @NoReturnValue or isAllowedOutput()");
			} else {
				log.warn("REST API output type is not recognized: " + response.getClass());
			}
		}
		return Response.ok().build();
	}
	
	/**
	 * A method allowing subclasses to specify whether a particular output object that
	 * is not part of the default set can be serialized and returned. This can be used
	 * when you don't have control of the object to extend RestObject.
	 *
	 * A subclass should extend this method and return true if a particular object is
	 * of an expected and supported type.
	 *
	 * @param response The response object
	 * @return True if the object should be accepted as valid output
	 */
	protected boolean isAllowedOutput(@SuppressWarnings("unused") Object response) {
		return false;
	}
	
	/**
	 * Checks the provided annotation to see whether the given Identity is authorized
	 * per that annotation's criteria. Only one criterion may be specified per annotation.
	 * If more than one is specified, the highest criterion on this list will be the one
	 * used:
	 *
	 *  * systemAdmin
	 *  * right
	 *  * rights
	 *  * capability
	 *  * capabilities
	 *  * authorizerClass
	 *  * attribute
	 *  * population
	 *  * authorizerRule
	 *
	 * @param annotation The annotation to check
	 * @param me The (non-null) identity to check
	 * @return True if the user is authorized by the annotation's criteria
	 * @throws GeneralException if any Sailpoint errors occur
	 * @throws NullPointerException if the provided Identity is null
	 */
	protected final boolean isAuthorized(AuthorizedBy annotation, Identity me) throws GeneralException {
		Objects.requireNonNull(me, "This method can only be used when someone is logged in");
		boolean authorized = false;
		Identity.CapabilityManager capabilityManager = me.getCapabilityManager();
		List<Capability> caps = capabilityManager.getEffectiveCapabilities();
		if (annotation.systemAdmin()) {
			authorized = Capability.hasSystemAdministrator(caps);
		} else if (Util.isNotNullOrEmpty(annotation.right())) {
			authorized = capabilityManager.hasRight(annotation.right());
		} else if (annotation.rightsList().length > 0) {
			for(String right : annotation.rightsList()) {
				if (capabilityManager.hasRight(right)) {
					authorized = true;
					break;
				}
			}
		} else if (Util.isNotNullOrEmpty(annotation.capability())) {
			authorized = Capability.hasCapability(annotation.capability(), caps);
		} else if (annotation.capabilitiesList().length > 0) {
			for(String capability : annotation.capabilitiesList()) {
				if (Capability.hasCapability(capability, caps)) {
					authorized = true;
					break;
				}
			}
		} else if (!Authorizer.class.equals(annotation.authorizerClass())) {
			try {
				Class<? extends Authorizer> authorizerClass = annotation.authorizerClass();
				Authorizer authorizer = authorizerClass.getConstructor().newInstance();
				authorize(authorizer);
				authorized = true;
			} catch(GeneralException e) {
				/* Authorization failed, ignore this */
			} catch(Exception e) {
				log.warn("Error during authorizer construction", e);
			}
		} else if (Util.isNotNullOrEmpty(annotation.attribute())) {
			String attributeName = annotation.attribute();
			Object attributeValue = me.getAttribute(attributeName);
			if (Util.isNotNullOrEmpty(annotation.attributeValue())) {
				String testValue = annotation.attributeValue();
				if (attributeValue instanceof List) {
					@SuppressWarnings("unchecked")
					List<Object> attributeValues = (List<Object>)attributeValue;
					authorized = Util.nullSafeContains(attributeValues, testValue);
				}
				if (!authorized) {
					if (Sameness.isSame(testValue, attributeValue, false)) {
						authorized = true;
					}
				}
			} else if (annotation.attributeValueIn().length > 0) {
				for(String testValue : annotation.attributeValueIn()) {
					if (Sameness.isSame(testValue, attributeValue, false)) {
						authorized = true;
						break;
					}
				}
			} else {
				throw new IllegalArgumentException("If an attribute is defined in an AuthorizedBy annotation, either an attributeValue or an attributeValueIn clause must also be present");
			}
		} else if (Util.isNotNullOrEmpty(annotation.population())) {
			GroupDefinition population = getContext().getObject(GroupDefinition.class, annotation.population());
			if (population != null) {
				if (population.getFilter() != null) {
					Matchmaker matcher = new Matchmaker(getContext());
					IdentitySelector selector = new IdentitySelector();
					selector.setPopulation(population);
					authorized = matcher.isMatch(selector, me);
				} else {
					log.warn("AuthorizedBy annotation specifies non-filter population " + annotation.population());
				}
			} else {
				log.warn("AuthorizedBy annotation specifies non-existent population " + annotation.population());
			}
		} else if (Util.isNotNullOrEmpty(annotation.authorizerRule())) {
			Map<String, Object> ruleParams = new HashMap<>();
			ruleParams.put("resourceInfo", resourceInfo);
			ruleParams.put("identity", me);
			ruleParams.put("log", log);
			ruleParams.put("userContext", this);
			ruleParams.put("annotation", annotation);

			Rule theRule = getContext().getObject(Rule.class, annotation.authorizerRule());

			if (theRule != null) {
				Object output = getContext().runRule(theRule, ruleParams);
				authorized = Util.otob(output);
			} else {
				log.warn("Authorizer rule " + annotation.authorizerRule() + " not found");
			}
		}
		return authorized;
	}

	/**
	 * If true, we should capture logs
	 * @return True if we should capture logs
	 */
	public Boolean isCaptureLogs() {
		return captureLogs.get();
	}

	/**
	 * Should we forward logs to the regular log destination?
	 * @return If true, we should forward logs to the regular log destinations
	 */
	public Boolean isForwardLogs() {
		return forwardLogs.get();
	}

	/**
	 * Sets the log capture flag. If true, logs will be captured and attached to any
	 * error outputs. If the response is not an error, logs will not be returned.
	 *
	 * @param captureLogs The logs
	 */
	public void setCaptureLogs(boolean captureLogs) {
		this.captureLogs.set(captureLogs);
	}

	/**
	 * Sets the forward logs flag. If true, logs from other classes will be intercepted
	 * and forwarded to the Logger associated with this class.
	 *
	 * @param forwardLogs If true, we should forward logs
	 */
	public void setForwardLogs(boolean forwardLogs) {
		this.forwardLogs.set(forwardLogs);
	}

	/**
	 * Sets the plugin authorization checker.
	 *
	 * This method is intended to be used in the constructor of subclasses.
	 *
	 * @param pluginAuthorizationCheck The plugin authorization checker
	 */
	public final void setPluginAuthorizationCheck(PluginAuthorizationCheck pluginAuthorizationCheck) {
		this.pluginAuthorizationCheck = pluginAuthorizationCheck;
	}

    /**
     * Returns true if we ought to audit API calls to this resource. Subclasses
     * can override this method to do their own detection.
     *
     * @param request The inbound servlet request
     * @return True if we should audit
     */
    protected boolean shouldAudit(HttpServletRequest request) {
        return false;
    }

	/**
	 * Returns true if we ought to meter API calls to this resource. Subclasses
	 * can override this method to do their own detection.
     *
     * UPDATE 2026-01-22: Change default from true to false to reduce noise
     *
	 * @param request The inbound servlet request
     * @return True if we should meter
	 */
	protected boolean shouldMeter(HttpServletRequest request) {
		return false;
	}

	/**
	 * Performs some validation against the input, throwing an IllegalArgumentException if
	 * the validation logic returns false.
	 * @param check The check to execute
	 * @throws IllegalArgumentException if the check fails (returns false) or throws an exception
	 */
	protected final void validate(PluginValidationCheck check) throws IllegalArgumentException {
		validate(null, check);
	}

	/**
	 * Performs some validation against the input, throwing an IllegalArgumentException if
	 * the validation logic returns false. The exception will contain the failure message by
	 * default. You will provide a {@link PluginValidationCheck} implementation, typically
	 * a lambda within your REST API entry point.
	 *
	 * If the validation check itself throws an exception, the output is the same as if the
	 * check did not pass, except that the exception will be logged.
	 *
	 * @param failureMessage The failure message, or null to use a default
	 * @param check The check to execute
	 * @throws IllegalArgumentException if the check fails (returns false) or throws an exception
	 */
	protected final void validate(String failureMessage, PluginValidationCheck check) throws IllegalArgumentException {
		try {
			boolean result = check.test();
			if (!result) {
				if (Util.isNotNullOrEmpty(failureMessage)) {
					throw new IllegalArgumentException(failureMessage);
				} else {
					throw new IllegalArgumentException("Failed a validation check");
				}
			}
		} catch(Exception e) {
			log.handleException(e);
			if (Util.isNotNullOrEmpty(failureMessage)) {
				throw new IllegalArgumentException(failureMessage, e);
			} else {
				throw new IllegalArgumentException(e);
			}
		}
	}
}
