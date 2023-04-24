package com.identityworksllc.iiq.common.logging;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * Capture logs in IIQ 8, which uses Log4J 2.x. That version has entirely
 * different Logger semantics, requiring separate code.
 */
public class CaptureLogs8 implements CaptureLogs {
    /**
     * The layout used for captured messages
     */
    private static final PatternLayout formatter = PatternLayout.newBuilder().withPattern(PatternLayout.SIMPLE_CONVERSION_PATTERN).build();

    /**
     * The appender that will log messages to either the messages queue
     * or the listener.
     */
    private static class CapturingAppender extends AbstractAppender {

        /**
         * @see AbstractAppender#AbstractAppender(String, Filter, Layout, boolean, Property[]) 
         */
        protected CapturingAppender(String name, Filter filter, Layout<? extends Serializable> layout, boolean ignoreExceptions, Property[] properties) {
            super(name, filter, layout, ignoreExceptions, properties);
        }

        /**
         * @see AbstractAppender#append(LogEvent)
         */
        @Override
        public void append(LogEvent event) {
            if (LogCapture.messages.get() != null) {
                StringBuilder builder = new StringBuilder();
                formatter.serialize(event, builder);
                LogCapture.messages.get().add(builder.toString());
            } else if (LogCapture.listener.get() != null) {
                LogListener listener = LogCapture.listener.get();
                if (listener != null) {
                    LogListener.LogMessage message = new LogListener.LogMessage(new Date(event.getTimeMillis()), event.getLevel().toString(), event.getLoggerName(), String.valueOf(event.getMessage()), null);
                    listener.logMessageReceived(message);
                }
            }

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

        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        final Configuration config = ctx.getConfiguration();

        config.getLoggerConfig("sailpoint.web").setLevel(Level.ERROR);
        config.getLoggerConfig("sailpoint.server.Servicer").setLevel(Level.ERROR);

        ctx.updateLoggers(config);
    }

    @Override
    public void capture(String... names) {
        for(String name : names) {
            captureLogger(name);
        }
    }

    /**
     * Captures the logger with the given name
     * @param loggerName The logger
     */
    public static void captureLogger(String loggerName) {
        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        final Configuration config = ctx.getConfiguration();
        PatternLayout layout =
                PatternLayout.newBuilder().withPattern(PatternLayout.SIMPLE_CONVERSION_PATTERN).withConfiguration(config).withCharset(StandardCharsets.UTF_8).build();

        Appender appender = config.getAppender("idw-writer-appender");

        if (appender == null) {
            appender = new CapturingAppender("idw-writer-appender", null, layout, false, null);
                    //WriterAppender.newBuilder().setName("idw-writer-appender").setTarget(new CapturedLogsWriter()).setLayout(layout).setConfiguration(config).setFilter().build();
            appender.start();
            config.addAppender(appender);
        }

        AppenderRef ref = AppenderRef.createAppenderRef("idw-writer-appender", Level.DEBUG, null);
        AppenderRef[] refs = new AppenderRef[] {ref};
        LoggerConfig loggerConfig = config.getLoggerConfig(loggerName);
        if (loggerConfig == null) {
            loggerConfig = LoggerConfig.createLogger(false, Level.DEBUG, loggerName, "true", refs, null, config, null);
            loggerConfig.start();
            config.addLogger(loggerName, loggerConfig);
        }

        Level originalLevel = loggerConfig.getLevel();

        if (!loggerConfig.getName().equals(loggerName)) {
            LoggerConfig specificConfig = new LoggerConfig(loggerName, Level.DEBUG, false);
            specificConfig.setParent(loggerConfig);
            config.addLogger(loggerName, specificConfig);
            loggerConfig = specificConfig;
        }

        wrapAppenders(loggerConfig, loggerConfig, originalLevel);

        loggerConfig.setAdditive(false);

        if (!loggerConfig.getAppenders().containsKey("idw-writer-appender")) {
            loggerConfig.addAppender(appender, Level.DEBUG, null);
        }

        ctx.updateLoggers(config);
    }

    /**
     * Wraps the appenders for the logger and any of its parents, preventing
     * the default appenders from logging at a strange level.
     *
     * @param toModify The LoggerConfig to modify (the one we're hooking)
     * @param current The LoggerConfig to consider (may be a parent)
     * @param originalLevel The original level to set on the appenders
     */
    private static void wrapAppenders(LoggerConfig toModify, LoggerConfig current, Level originalLevel) {
        Map<String, Appender> appenders = current.getAppenders();
        for(String name : appenders.keySet()) {
            if (!name.equals("idw-writer-appender")) {
                Appender existing = appenders.get(name);
                toModify.removeAppender(name);
                toModify.addAppender(existing, originalLevel, null);
            }
        }
        if (current.getParent() != null) {
            wrapAppenders(toModify, current.getParent(), originalLevel);
        }
    }
}
