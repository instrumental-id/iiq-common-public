package com.identityworksllc.iiq.common;

import sailpoint.object.TaskResult;
import sailpoint.task.TaskMonitor;
import sailpoint.tools.GeneralException;

public class TaskUtil {

    /**
     * The interface used for lock-and-callback utilities
     */
    public interface TaskResultConsumer {
        /**
         * Does something with the task result
         *
         * @param taskResult The task result to do something to
         * @throws GeneralException if anything fails
         */
        void accept(TaskResult taskResult) throws GeneralException;
    }

    /**
     * Executes the given action with a locked master result
     *
     * @param monitor  The TaskMonitor used to retrieve the locked result
     * @param consumer An action to run against the locked partition result
     * @throws GeneralException if anything fails
     */
    public static void withLockedMasterResult(TaskMonitor monitor, TaskResultConsumer consumer) throws GeneralException {
        TaskResult lockedMasterResult = monitor.lockMasterResult();
        try {
            consumer.accept(lockedMasterResult);
        } catch (GeneralException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new GeneralException(t);
        } finally {
            monitor.commitMasterResult();
        }
    }

    /**
     * Executes the given action with a locked partition result
     *
     * @param monitor  The TaskMonitor used to retrieve the locked result
     * @param consumer An action to run against the locked partition result
     * @throws GeneralException if anything fails
     */
    public static void withLockedPartitionResult(TaskMonitor monitor, TaskResultConsumer consumer) throws GeneralException {
        TaskResult lockedPartitionResult = monitor.lockPartitionResult();
        try {
            consumer.accept(lockedPartitionResult);
        } catch (GeneralException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new GeneralException(t);
        } finally {
            monitor.commitPartitionResult();
        }
    }
}
