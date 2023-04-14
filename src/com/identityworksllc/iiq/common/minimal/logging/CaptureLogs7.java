package com.identityworksllc.iiq.common.minimal.logging;

import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;

import org.apache.log4j.Appender;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Category;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Layout;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.Priority;
import org.apache.log4j.spi.LoggingEvent;

/**
 * Captures the most interesting logs for IIQ 7.x by hooking into Log4j 1.x
 */
public class CaptureLogs7 implements CaptureLogs {
	
	/**
	 * The new appender that will log the messages if we're tracking them and also pass
	 * them on to the original logger if we're higher than the original level.
	 */
	@SuppressWarnings("javadoc")
	public static class CaptureLogs7Appender extends AppenderSkeleton {
		
		private PatternLayout formatter;
		private List<Appender> internalAppenders;
		
		public CaptureLogs7Appender(Level level, List<Appender> appenders) {
			this.internalAppenders = appenders;
			this.formatter = new PatternLayout("%d{ISO8601} %5p %t %c{4}:%L - %m%n");
			super.setThreshold(level);
		}

		@Override
		protected void append(LoggingEvent event) {
			if (LogCapture.messages.get() != null) {
				StringBuilder builder = new StringBuilder();
				builder.append(formatter.format(event).replace(Layout.LINE_SEP, ""));
				if (formatter.ignoresThrowable()) {
					String[] lines = event.getThrowableStrRep();
					if (lines != null) {
						for(String l : lines) {
							builder.append(Layout.LINE_SEP + l.replace(Layout.LINE_SEP, ""));
						}
					}
				}
				LogCapture.messages.get().add(builder.toString());
			} else if (LogCapture.listener.get() != null) {
				LogListener listener = LogCapture.listener.get();
				if (listener != null) {
					LogListener.LogMessage message = new LogListener.LogMessage(new Date(event.getTimeStamp()), event.getLevel().toString(), event.getLoggerName(), String.valueOf(event.getMessage()), event.getThrowableStrRep());
					listener.logMessageReceived(message);
				}
			}
			if (event.getLevel().isGreaterOrEqual(threshold)) {
				if (internalAppenders != null) {
					for(Appender a : internalAppenders) {
						a.doAppend(event);
					}
				}
			}
		}
		
		@Override
		public void close() {
			for(Appender a : internalAppenders) {
				if (!(a instanceof ConsoleAppender)) {
					a.close();
				}
			}
		}

		@Override
		public boolean isAsSevereAsThreshold(Priority priority) {
			// No filtering by level!
			return true;
		}

		@Override
		public boolean requiresLayout() {
			return true;
		}
	}
	
	@Override
	public void capture(String... names) {
		for(String name : names) {
			captureLogger(name);
		}
	}

	/**
	 * Captures the named logger, constructing it if it doesn't already exist
	 * @param loggerName The logger name to capture
	 */
	private void captureLogger(String loggerName) {
		// We want to construct the logger by name if it doesn't already exist
		// Otherwise, we might end up with weird defaults later due to logger inheritance
		Logger logger = Logger.getLogger(loggerName);
		List<Appender> existingAppenders = new ArrayList<>();
		boolean alreadyCaptured = getEffectiveAppenders(logger, existingAppenders);
		if (!alreadyCaptured) {
			CaptureLogs7Appender appender = new CaptureLogs7Appender(logger.getEffectiveLevel(), existingAppenders);
			// This is all required to prevent the logger from emitting messages on its own
			logger.addAppender(appender);
			// We have to remove the appenders one at a time. Logger.removeAllAppenders also closes the appenders, which is a problem.
			for(Appender a : existingAppenders) {
				logger.removeAppender(a);
			}
			logger.setLevel(Level.DEBUG);
			logger.setAdditivity(false);
		}
	}

	@Override
	public void captureMost() {
		captureLogger("org.hibernate.SQL");
		captureLogger("hibernate.hql.ast.AST");
		captureLogger("sailpoint.api.Aggregator");
		captureLogger("sailpoint.api.Identitizer");
		captureLogger("sailpoint.api.Workflower");
		captureLogger("sailpoint.provisioning.AssignmentExpander");
		captureLogger("sailpoint.provisioning.ApplicationPolicyExpander");
		captureLogger("sailpoint.provisioning.IIQEvaluator");
		captureLogger("sailpoint.provisioning.Provisioner");
		captureLogger("sailpoint.provisioning.PlanCompiler");
		captureLogger("sailpoint.persistence.hql");
		captureLogger("sailpoint.connector.AbstractConnector");
		captureLogger("sailpoint.connector.ADLDAPConnector");
		captureLogger("sailpoint.connector.LDAPConnector");
		captureLogger("sailpoint.connector.AbstractIQServiceConnector");
		captureLogger("sailpoint.connector.DelimitedFileConnector");
		captureLogger("sailpoint.connector.AbstractFileBasedConnector");
		captureLogger("sailpoint.connector.JDBCConnector");
		captureLogger("sailpoint.connector.webservices.WebServicesConnector");
		captureLogger("sailpoint.connector.webservices.AbstractHTTPRequestBuilder");
		captureLogger("sailpoint.connector.webservices.JSONRequestBuilder");
		captureLogger("sailpoint.connector.webservices.JSONResponseParser");
		captureLogger("sailpoint.connector.webservices.WebServicesClient");
		captureLogger("sailpoint.connector.webservices.paging.WebServicesPaginator");
		captureLogger("sailpoint.connector.webservices.paging.WebServicesPaginator");
		captureLogger("openconnector.connector.okta.OktaConnector");
		captureLogger("openconnector.connector.WorkDay");
		captureLogger("openconnector.AbstractConnector");
		// Prevent spam
		Logger.getLogger("sailpoint.web").setLevel(Level.ERROR);
		Logger.getLogger("sailpoint.server.Servicer").setLevel(Level.ERROR);
	}

	/**
	 * Gets the appenders for this logger by walking up the hierarchy of loggers
	 * @param logger The logger to check
	 * @param existingAppenders The list to which appenders should be added
	 * @return True if this logger or any of its parents has already been captured
	 */
	private boolean getEffectiveAppenders(Category logger, List<Appender> existingAppenders) {
		@SuppressWarnings("unchecked")
		Enumeration<Appender> appenders = logger.getAllAppenders();
		boolean alreadyCaptured = false;
		while(appenders.hasMoreElements()) {
			Appender appender = appenders.nextElement();
			if (appender.getClass().equals(CaptureLogs7Appender.class)) {
				alreadyCaptured = true;
			}
			existingAppenders.add(appender);
		}
		
		if (logger.getParent() != null) {
			boolean parentCaptured = getEffectiveAppenders(logger.getParent(), existingAppenders);
			if (!alreadyCaptured && parentCaptured) {
				alreadyCaptured = true;
			}
		}
		
		return alreadyCaptured;
	}
	
}
