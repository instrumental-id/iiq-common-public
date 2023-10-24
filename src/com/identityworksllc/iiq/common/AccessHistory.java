package com.identityworksllc.iiq.common;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.Version;
import sailpoint.api.SailPointContext;
import sailpoint.server.Environment;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A singleton utility for retrieving AccessHistory data, even in versions
 * of IIQ less than 8.4. This is useful for creating multi-version plugins.
 */
@SuppressWarnings("JavaReflectionMemberAccess")
public class AccessHistory {

    /**
     * The Versioned singleton instance
     */
    private static AccessHistory INSTANCE;

    /**
     * A lock used to prevent more than one class from constructing an instance of
     * this class at the same time.
     */
    private static final Lock lock = new ReentrantLock();

    /**
     * The logger
     */
    private static final Log log = LogFactory.getLog(AccessHistory.class);

    /**
     * Gets an instance of the utility class with version-specific implementation
     * classes already configured.
     *
     * @return An instance of {@link AccessHistory}
     * @throws GeneralException on failures
     */
    public static AccessHistory get() throws GeneralException {
        if (INSTANCE == null) {
            try {
                lock.lockInterruptibly();
                try {
                    if (INSTANCE == null) {
                        INSTANCE = new AccessHistory();
                    }
                } finally {
                    lock.unlock();
                }
            } catch(Exception e) {
                throw new GeneralException(e);
            }
        }
        return INSTANCE;
    }


    /**
     * The cached flag indicating that access history is available
     */
    private final AtomicReference<Boolean> accessHistoryEnabled;
    /**
     * The cached Method to get the context
     */
    private final AtomicReference<Method> cachedAccessHistoryContextGetMethod;
    /**
     * The cached Method to get the environment
     */
    private final AtomicReference<Method> cachedAccessHistoryEnvGetMethod;

    private AccessHistory() {
        accessHistoryEnabled = new AtomicReference<>();
        cachedAccessHistoryEnvGetMethod = new AtomicReference<>();
        cachedAccessHistoryContextGetMethod = new AtomicReference<>();
    }


    /**
     * Returns the SailPointContext associated with Access History
     * @return The access history context, or an empty optional if not available
     */
    public Optional<SailPointContext> getAccessHistoryContext() throws GeneralException {
        String version = Version.getVersion();
        boolean is84 = Util.isNotNullOrEmpty(version) && (version.compareTo("8.4") >= 0);
        if (is84 && this.isAccessHistoryEnabled()) {
            try {
                if (cachedAccessHistoryContextGetMethod.get() == null) {
                    Class<?> ahu = Class.forName("sailpoint.accesshistory.AccessHistoryUtil");
                    Method method = ahu.getMethod("getAccessHistoryContext");
                    cachedAccessHistoryContextGetMethod.set(method);
                }
                SailPointContext ahContext = (SailPointContext) cachedAccessHistoryContextGetMethod.get().invoke(null);

                return Optional.of(ahContext);
            } catch (NoSuchMethodException | SecurityException | IllegalAccessException e) {
                log.debug("Caught an exception constructing the 8.4 access history feature", e);
            } catch(ClassNotFoundException e) {
                throw new GeneralException("This environment appears to be 8.4, but AccessHistoryUtil was not found", e);
            } catch (InvocationTargetException e) {
                throw new GeneralException("Exception thrown while constructing the AH Environment", e);
            }
        } else {
            log.debug("Access history is not available and/or enabled (version = " + version + ")");
        }

        return Optional.empty();
    }

    /**
     * Returns the Environment associated with Access History
     * @return The access history environment, or an empty optional if not available
     */
    public Optional<Environment> getAccessHistoryEnvironment() throws GeneralException {
        String version = Version.getVersion();
        boolean is84 = Util.isNotNullOrEmpty(version) && (version.compareTo("8.4") >= 0);
        if (is84 && this.isAccessHistoryEnabled()) {
            try {
                if (cachedAccessHistoryEnvGetMethod.get() == null) {
                    Method method = Environment.class.getMethod("getEnvironmentAccessHistory");
                    cachedAccessHistoryEnvGetMethod.set(method);
                }
                Environment ahEnvironment = (Environment) cachedAccessHistoryEnvGetMethod.get().invoke(null);

                return Optional.of(ahEnvironment);
            } catch (NoSuchMethodException | SecurityException | IllegalAccessException e) {
                log.debug("Caught an exception constructing the 8.4 access history feature", e);
            } catch (InvocationTargetException e) {
                throw new GeneralException("Exception thrown while constructing the AH Environment", e);
            }
        } else {
            log.debug("Access history is not available and/or enabled (version = " + version + ")");
        }

        return Optional.empty();
    }

    /**
     * Returns true if access history is enabled. The result will be cached
     * so that reflection is not used for every call of this method.
     *
     * @return True, if this is 8.4 and access history is enabled
     */
    public boolean isAccessHistoryEnabled() {
        if (accessHistoryEnabled.get() != null) {
            return accessHistoryEnabled.get();
        }
        String version = Version.getVersion();
        boolean is84 = Util.isNotNullOrEmpty(version) && (version.compareTo("8.4") >= 0);
        if (is84) {
            try {
                boolean enabled = (boolean) Version.class.getMethod("isAccessHistoryEnabled").invoke(null);
                accessHistoryEnabled.set(enabled);
                return enabled;
            } catch(Exception e) {
                log.debug("Caught an exception checking whether access history is enabled", e);
            }
        }

        accessHistoryEnabled.set(false);
        return false;
    }


}
