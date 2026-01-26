package com.identityworksllc.iiq.common;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.api.logging.SyslogThreadLocal;
import sailpoint.object.SyslogEvent;
import sailpoint.persistence.Sequencer;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import javax.validation.constraints.NotNull;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A utility for generating and committing Syslog events, even where IIQ would not
 * produce them. The events are saved via an autonomous transaction.
 */
public class Syslogger {
    /**
     * Argument VO for syslog events
     */
    @Setter
    @Getter
    @Builder
    public static final class SyslogArgs {
        /**
         * The error, optionally
         */
        private Throwable error;

        /**
         * The event level (ERROR, INFO, WARN)
         */
        private String eventLevel;

        /**
         * The logged in user, optionally
         */
        private String loggedInUser;

        /**
         * The message to log (not null)
         */
        @NotNull
        private String message;

        /**
         * The owning class, optionally
         */
        private Class<?> owningClass;
    }

    /**
     * Event level error
     */
    public static final String EVENT_LEVEL_ERROR = "ERROR";

    /**
     * Event level info
     */
    public static final String EVENT_LEVEL_INFO = "INFO";

    /**
     * Event level warn
     */
    public static final String EVENT_LEVEL_WARN = "WARN";

    /**
     * Private utility constructor
     */
    private Syslogger() {

    }

    /**
     * @see #logEvent(Class, String, Throwable)
     */
    public static String logEvent(Class<?> owningClass, String message) throws GeneralException {
        return logEvent(owningClass, message, null);
    }

    /**
     * @see #logEvent(Class, String, Throwable)
     */
    public static String logEvent(String message) throws GeneralException {
        return logEvent(null, message, null);
    }

    /**
     * @see #logEvent(Class, String, Throwable)
     */
    public static String logEvent(String message, Throwable error) throws GeneralException {
        return logEvent(null, message, error);
    }

    public static String logEvent(final Class<?> owningClass, final String message, final Throwable error) throws GeneralException {
        return logEvent(owningClass, message, error, EVENT_LEVEL_ERROR);
    }

    /**
     * Logs a SyslogEvent of ERROR type with a sequential QuickKey, returning that key
     *
     * @param owningClass The class invoking this method, which will be logged as the class name, optionally
     * @param message The message to log (not null)
     * @param error The error, optionally
     * @return The quick key generated for this event
     * @throws GeneralException if any failures occur during logging or creating the private context
     */
    public static String logEvent(final Class<?> owningClass, final String message, final Throwable error, final String eventLevel) throws GeneralException {
        final String authenticatedUser;
        final SailPointContext currentContext = SailPointFactory.getCurrentContext();
        if (currentContext != null && currentContext.getUserName() != null) {
            authenticatedUser = currentContext.getUserName();
        } else {
            authenticatedUser = "???";
        }
        final AtomicReference<String> quickKeyRef = new AtomicReference<>();
        Utilities.withPrivateContext((context) -> {
            Sequencer sequencer = new Sequencer();
            String quickKey = sequencer.generateId(context, new SyslogEvent());
            quickKeyRef.set(quickKey);
            SyslogThreadLocal.set(Util.stripLeadingChar(quickKey, '0'));
            SyslogEvent event = new SyslogEvent();
            event.setQuickKey(quickKey);
            event.setUsername(authenticatedUser);
            event.setServer(Util.getHostName());
            event.setEventLevel(eventLevel);
            event.setThread(Thread.currentThread().getName());
            event.setMessage(message);
            if (owningClass != null) {
                event.setClassname(owningClass.getName());
            }
            if (error != null) {
                try (StringWriter writer = new StringWriter()) {
                    try (PrintWriter printWriter = new PrintWriter(writer)) {
                        error.printStackTrace(printWriter);
                    }
                    writer.flush();
                    event.setStacktrace(writer.toString());
                }
            }

            context.saveObject(event);
            context.commitTransaction();
        });
        return quickKeyRef.get();
    }
}
