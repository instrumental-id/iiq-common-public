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
 * A singleton utility for retrieving AccessHistory-related objects, gracefully
 * failing in pre-8.4 versions of IIQ.
 *
 * In versions of IIQ where Access History is not supported, the various
 * methods will return empty {@link Optional} objects.
 */
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
        boolean is84 = Utilities.isIIQVersionAtLeast(CommonConstants.VERSION_8_4);
        if (this.isAccessHistoryEnabled()) {
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
                log.warn("This environment appears to be 8.4, but AccessHistoryUtil was not found", e);
                throw new GeneralException("This environment appears to be 8.4, but AccessHistoryUtil was not found", e);
            } catch (InvocationTargetException e) {
                throw new GeneralException("Exception thrown while constructing the AH Environment", e);
            }
        } else {
            log.debug("Access history is not available or is not enabled (version = " + Version.getVersion() + ")");
        }

        return Optional.empty();
    }

    /**
     * Returns the Environment associated with Access History
     * @return The access history environment, or an empty optional if not available
     */
    public Optional<Environment> getAccessHistoryEnvironment() throws GeneralException {
        if (this.isAccessHistoryEnabled()) {
            try {
                if (cachedAccessHistoryEnvGetMethod.get() == null) {
                    Method method = Environment.class.getMethod("getEnvironmentAccessHistory");
                    cachedAccessHistoryEnvGetMethod.set(method);
                }
                Environment ahEnvironment = (Environment) cachedAccessHistoryEnvGetMethod.get().invoke(null);

                return Optional.of(ahEnvironment);
            } catch (NoSuchMethodException | SecurityException | IllegalAccessException e) {
                this.accessHistoryEnabled.set(false);
                log.warn("Caught an exception accessing the 8.4 access history feature", e);
            } catch (InvocationTargetException e) {
                this.accessHistoryEnabled.set(false);
                log.warn("Caught an exception accessing the 8.4 access history feature", e);
                throw new GeneralException("Exception thrown while constructing the AH Environment", e);
            }
        } else {
            log.debug("Access history is not available and/or enabled (version = " + Version.getVersion() + ")");
        }

        return Optional.empty();
    }

    /**
     * Returns true if access history is available and enabled. The result will be cached
     * so that reflection is not used for every call of this method.
     *
     * @return True, if this is 8.4 or higher, and access history is enabled
     */
    public boolean isAccessHistoryEnabled() {
        if (accessHistoryEnabled.get() != null) {
            return accessHistoryEnabled.get();
        }
        boolean is84 = Utilities.isIIQVersionAtLeast(CommonConstants.VERSION_8_4);
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
