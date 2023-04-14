package com.identityworksllc.iiq.common.minimal.threads;

import bsh.This;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.tools.GeneralException;

import java.io.Closeable;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * An object reference wrapped by a ReentrantLock. This allows either directly
 * invoking a function on the locked object or a more procedural lock/unlock
 * pattern. This is fairer and safer than synchronizing on the object, since
 * it can be interrupted (via Thread.interrupt(), e.g. on a task termination)
 * and fairly chooses the longest-waiting thread to execute next.
 *
 * In either usage, threads will wait forever for the lock. Threads will emit
 * an INFO level log message every 30 seconds indicating that they are still
 * waiting. Additionally, threads will emit a DEBUG message upon acquiring
 * the lock. Both messages will have a UUID indicating a unique lock name,
 * for tracing purposes.
 *
 * If you are calling this from Beanshell, you can directly pass the name of a
 * Beanshell method, along with the 'this' reference, such as:
 *
 *   objReference.lockAndAct(this, "beanshellMethodName");
 *
 * On lock acquisition, the specified Beanshell method in the 'this' scope will
 * be invoked, passing the object as the only argument. The object will be
 * automatically freed after the method completes.
 *
 * You can also use this class via a try/finally structure, such as:
 *
 *   Object lockedObject = objReference.lock();
 *   try {
 *       // do stuff here
 *   } finally {
 *       objReference.unlock();
 *   }
 *
 * @param <T> The type of the contained object
 */
public final class LockingObjectReference<T> implements AutoCloseable {
    /**
     * The interface to be implemented by any locked object actions
     * @param <T> The object type
     */
    @FunctionalInterface
    public interface LockedObjectAction<T> {
        /**
         * The action to take for locking the object
         * @param object The object to act on
         * @throws GeneralException if any failures occur
         */
        void accept(T object) throws GeneralException;
    }

    /**
     * A special instance of LockedObjectAction for Beanshell execution purposes
     * @param <T> The object type
     */
    private static class BeanshellLockedObjectAction<T> implements LockedObjectAction<T> {

        private final String methodName;
        private final This thisObject;

        public BeanshellLockedObjectAction(bsh.This thisObject, String methodName) {
            this.thisObject = thisObject;
            this.methodName = methodName;
        }

        @Override
        public void accept(T object) throws GeneralException {
            try {
                Object[] inputs = new Object[] { object };
                thisObject.invokeMethod(methodName, inputs);
            } catch(Exception e) {
                throw new GeneralException(e);
            }
        }
    }

    private final ReentrantLock lock;
    private final Log log;
    private final T object;
    private final String uuid;

    public LockingObjectReference(T object) {
        this.lock = new ReentrantLock(true);
        this.object = object;
        this.log = LogFactory.getLog(this.getClass());
        this.uuid = UUID.randomUUID().toString();
    }

    /**
     * Unlocks the object, allowing use of this class in a try-with-resources context
     */
    @Override
    public void close() {
        unlockObject();
    }

    /**
     * Locks the object, then passes it to {@link LockedObjectAction#accept(Object)}.
     * @param action The function to execute against the object after it is locked
     * @throws GeneralException if any failures occur
     */
    public void lockAndAct(LockedObjectAction<T> action) throws GeneralException {
        T theObject = lockObject();
        try {
            action.accept(theObject);
        } finally {
            unlockObject();
        }
    }

    /**
     * Locks the object, then passes it to the given Beanshell method.
     * @param beanshell The Beanshell context ('this' in a script)
     * @param methodName The name of a Beanshell method in the current 'this' context or a parent
     * @throws GeneralException if any failures occur
     */
    public void lockAndAct(bsh.This beanshell, String methodName) throws GeneralException {
        lockAndAct(new BeanshellLockedObjectAction<>(beanshell, methodName));
    }

    /**
     * Locks the object (waiting forever if necessary), then returns the object
     * @return The object, now exclusive to this thread
     * @throws GeneralException if any failures occur
     */
    public T lockObject() throws GeneralException {
        boolean locked = false;
        final long startTime = System.currentTimeMillis();
        long lastNotification = startTime;
        try {
            while (!locked) {
                locked = lock.tryLock(10, TimeUnit.SECONDS);
                if (!locked) {
                    long notificationElapsed = System.currentTimeMillis() - lastNotification;
                    long totalElapsed = System.currentTimeMillis() - startTime;
                    if (notificationElapsed > (1000L * 30)) {
                        lastNotification = System.currentTimeMillis();
                        if (log.isInfoEnabled()) {
                            log.info("Thread " + Thread.currentThread().getName() + " has been waiting " + (totalElapsed / 1000L) + " seconds for lock " + uuid);
                        }
                    }
                }
            }
            if (log.isDebugEnabled()) {
                log.debug("Thread " + Thread.currentThread().getName() + " acquired the lock " + uuid);
            }
            return object;
        } catch(InterruptedException e) {
            throw new GeneralException(e);
        }
    }

    /**
     * Unlocks the object
     */
    public void unlockObject() {
        if (lock.isLocked()) {
            lock.unlock();
        }
    }
}
