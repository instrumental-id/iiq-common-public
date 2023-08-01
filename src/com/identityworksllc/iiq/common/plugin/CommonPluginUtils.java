package com.identityworksllc.iiq.common.plugin;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.Server;
import sailpoint.object.ServiceDefinition;
import sailpoint.server.ServicerUtil;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Some utilities to avoid boilerplate
 */
@SuppressWarnings("unused")
public class CommonPluginUtils {
	/**
	 * The executor passed to {@link #singleServerExecute(SailPointContext, ServiceDefinition, SingleServerExecute)}
	 * mainly so that we can extend it with those default methods and throw an exception
	 * from run(). None of the out of box functional interfaces have exception throwing.
	 *
	 * You can use this either as a lambda or by implementing this interface in
	 * your Service class.
	 */
	@FunctionalInterface
	public interface SingleServerExecute {
		/**
		 * Wraps the implementation in start/stop timeout tracking code, saving
		 * those timestamps and the last run host on the ServiceDefinition after
		 * completion. This may be used for recurring services that need to know
		 * when they last ran (e.g., to do an incremental action).
		 *
		 * @param target The target ServiceDefinition to update
		 * @return The wrapped functional interface object
		 */
		default SingleServerExecute andSaveTimestamps(ServiceDefinition target) {
			return (context) -> {
				long lastStart = System.currentTimeMillis();
				try {
					this.singleServerExecute(context);
				} finally {
					Attributes<String, Object> attributes = target.getAttributes();
					if (attributes == null) {
						attributes = new Attributes<>();
						target.setAttributes(attributes);
					}
					attributes.put("lastStart", lastStart);
					attributes.put("lastStop", System.currentTimeMillis());
					attributes.put("lastHost", Util.getHostName());
					context.saveObject(target);
					context.commitTransaction();
				}
			};
		}

		/**
		 * The main implementation of this service
		 * @param context The sailpoint context for the current run
		 * @throws GeneralException if any failures occur
		 */
		void singleServerExecute(SailPointContext context) throws GeneralException;
	}
	/**
	 * Log
	 */
	private static final Log log = LogFactory.getLog(CommonPluginUtils.class);

	/**
	 * Executes the task given by the functional {@link SingleServerExecute} if this
	 * server is the alphabetically lowest server on which this Service is allowed
	 * to run. Server names are sorted by the database using an 'order by' on query.
	 *
	 * This is intended to be used as the bulk of the execute() method of a Service
	 * class. You can either pass a lambda/closure to this method or implement the
	 * SingleServerExecute interface in your Service class (in which case you'd
	 * simply pass 'this').
	 *
	 * @param context The context
	 * @param self The current ServiceDefinition
	 * @param executor The executor to run
	 * @throws GeneralException if any failures occur
	 */
	public static void singleServerExecute(SailPointContext context, ServiceDefinition self, SingleServerExecute executor) throws GeneralException {
		Server target = null;
		QueryOptions qo = new QueryOptions();
		qo.addOrdering("name", true);
		if (!Util.nullSafeCaseInsensitiveEq(self.getHosts(), "global")) {
			qo.addFilter(Filter.in("name", Util.csvToList(self.getHosts())));
		}
		List<Server> servers = context.getObjects(Server.class, qo);
		for(Server s : servers) {
			if (!s.isInactive() && ServicerUtil.isServiceAllowedOnServer(context, self, s.getName())) {
				target = s;
				break;
			}
		}
		if (target == null) {
			// This would be VERY strange, since we are, in fact, running the service
			// right now, in this very code
			log.warn("There does not appear to be an active server allowed to run service " + self.getName());
		}
		String hostname = Util.getHostName();
		if (target == null || target.getName().equals(hostname)) {
			executor.singleServerExecute(context);
		}
	}

	/**
	 * Gets a map / JSON object indicating a status response
	 * @param message The message to associate with the response
	 * @return The response object
	 */
	public static Map<String, String> toStatusResponse(String message) {
		return toStatusResponse(message, null);
	}
	
	/**
	 * Gets a map / JSON object indicating a status response with an optional error 
	 * @param message The message to associate with the response
	 * @param error The error to associate with the response
	 * @return The response object
	 */
	public static Map<String, String> toStatusResponse(String message, Throwable error) {
		Map<String, String> result = new TreeMap<>();
		result.put("message", message);
		if (error != null) {
			result.put("exception", error.toString());
			if (error.getCause() != null) {
				result.put("exceptionImmediateCause", error.getCause().toString());
			}
		}
		return result;
	}
}
