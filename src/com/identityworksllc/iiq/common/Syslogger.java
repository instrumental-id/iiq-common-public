package com.identityworksllc.iiq.common;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.api.logging.SyslogThreadLocal;
import sailpoint.object.SyslogEvent;
import sailpoint.persistence.Sequencer;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A utility for generating and committing Syslog events, even where IIQ would not
 * produce them. The events are saved via an autonomous transaction.
 */
public class Syslogger {
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

    /**
     * Logs a SyslogEvent of ERROR type with a sequential QuickKey, returning that key
     *
     * @param owningClass The class invoking this method, which will be logged as the class name, optionally
     * @param message The message to log (not null)
     * @param error The error, optionally
     * @return The quick key generated for this event
     * @throws GeneralException if any failures occur during logging or creating the private context
     */
    public static String logEvent(final Class<?> owningClass, final String message, final Throwable error) throws GeneralException {
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
            event.setEventLevel("ERROR");
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

    /**
     * Private utility constructor
     */
    private Syslogger() {

    }
}
