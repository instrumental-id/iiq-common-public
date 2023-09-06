package com.identityworksllc.iiq.common.threads;

import com.identityworksllc.iiq.common.TaskCallback;
import com.identityworksllc.iiq.common.Utilities;
import com.identityworksllc.iiq.common.vo.Outcome;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.SailPointContext;
import sailpoint.object.Request;
import sailpoint.object.RequestDefinition;
import sailpoint.task.TaskMonitor;
import sailpoint.tools.Compressor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A worker thread for multi-threaded actions. This class can be used with
 * AbstractThreadedObjectIteratorTask to implement a multi-threaded task
 * or with SailPointWorkerExecutor to distribute the workers across
 * the cluster.
 *
 * If intended for use with a Request, all parts of the subclass, including
 * objects stored in lists, maps, and other structures, must be either Serializable
 * or Externalizable.
 */
public abstract class SailPointWorker implements Runnable, Serializable {

	/**
	 * An interface used as an error callback, specifically for use via the SailPointWorkerExecutor
	 * but potentially usable by testing code as well.
	 */
	@FunctionalInterface
	public interface ExceptionHandler {
		void handleError(Throwable t);
	}

	/**
	 * The attribute used to pass this object in serialized form to {@link com.identityworksllc.iiq.common.request.SailPointWorkerExecutor}.
	 */
	public static final String MULTI_SERIALIZED_WORKERS_ATTR = "serializedWorkers";

	/**
	 * Generated serialVersionUID
	 */
	private static final long serialVersionUID = 3L;

	/**
	 * Submits the task to the given executor and returns its future. If any listeners
	 * are passed, the given task's Future will be registered with the listeners as a
	 * dependency. The listeners will not be able to run until the Future resolves.
	 *
	 * @param executor The executor to submit the task to
	 * @param self The task to submit
	 * @param listeners Any other workers that care about the output of this one
	 * @return The Future for this task
	 */
	public static Future<?> submitWithListeners(ExecutorService executor, SailPointWorker self, SailPointWorker... listeners) {
		Future<?> future = executor.submit(self.toCallable());

		for(SailPointWorker listener : listeners) {
			listener.addDependency(self, future);
		}

		return future;
	}

	/**
	 * Serializes a list of SailPointWorker object into a Request suitable for use with {@link com.identityworksllc.iiq.common.request.SailPointWorkerExecutor}. That executor must be associated with the provided RequestDefinition.
	 * @param requestDefinition The request definition associated with {@link com.identityworksllc.iiq.common.request.SailPointWorkerExecutor}
	 * @param workers A list of SailPointWorkers to pass to the request handler
	 * @return The Request containing a serialized object
	 * @throws GeneralException if any failures occur compressing the object
	 * @throws IOException if any failures occur serializing this {@link SailPointWorker}
	 */
	public static Request toRequest(RequestDefinition requestDefinition, List<SailPointWorker> workers) throws GeneralException, IOException {
		Request request = new Request();
		request.setName("Batch of " + workers.size() + " workers");
		request.setDefinition(requestDefinition);
		List<String> workerStrings = new ArrayList<>();
		int phase = 0;
		int dependentPhase = 0;
		for(SailPointWorker worker : workers) {
			byte[] contents = Util.serializableObjectToBytes(worker);
			String compressed = Compressor.compress(Base64.getEncoder().encodeToString(contents));
			workerStrings.add(compressed);
			if (phase == 0 && worker.phase > 0) {
				phase = worker.phase;
			}
			if (dependentPhase == 0 && worker.dependentPhase > 0) {
				dependentPhase = worker.dependentPhase;
			}
		}
		request.setAttribute(MULTI_SERIALIZED_WORKERS_ATTR, workerStrings);
		if (phase > 0) {
			request.setPhase(phase);
		}
		if (dependentPhase > 0) {
			request.setDependentPhase(dependentPhase);
		}
		return request;
	}
	/**
	 * An optional list of Future objects that will be checked before the task is
	 * run. The resulting objects will be available via getDependencyOutput()
	 */
	/*package*/ List<SailPointWorker> children;
	/**
	 * An optional counter object to increment on a successful execution
	 */
	private transient AtomicInteger completedCounter;
	/**
	 * The default name of this task
	 */
	private final String defaultName;
	/**
	 * A list of registered dependencies by name
	 */
	/*package*/ transient Map<String, Future<?>> dependencies;

	/**
	 * An optional list of Future objects that will be checked before the task is
	 * run. The resulting objects will be available via getDependencyOutput()
	 */
	/*package*/ transient Map<String, Object> dependencyOutput;

	/**
	 * The dependent for this worker, if this is used as a partition
	 */
	private int dependentPhase;
	/**
	 * The optional exception handler for this task
	 */
	private transient ExceptionHandler exceptionHandler;

	/**
	 * A flag indicating that we ought to execute the children prior to this one.
	 * This defaults to true, but will be altered by {@link RecursiveWorkerChildTask}.
	 */
	/*package*/ boolean executeChildren;

	/**
	 * An optional counter object to increment on failures
	 */
	private transient AtomicInteger failedCounter;
	/**
	 * An instance of the TaskMonitor interface
	 */
	protected transient TaskMonitor monitor;

	/**
	 * An Outcome object allowing subclasses to provide output
	 */
	protected transient Outcome outcome;

	/**
	 * The parent task of this one, used to propagate events
	 */
	private SailPointWorker parent;

	/**
	 * The phase this worker is in, if used as a partition
	 */
	private int phase;

	/**
	 * Task callback object to be invoked
	 */
	private List<TaskCallback<SailPointWorker, Object>> taskCallback;

	/**
	 * A flag accessible to subclasses indicating that this task has been terminated
	 */
	private transient AtomicBoolean terminated;

	/**
	 * The amount of time to be spent before timing out
	 */
	private long timeoutMillis;

	/**
	 * The timestamp at which to mark this task as timed out
	 */
	private long timeoutTimestamp;

	/**
	 * Constructs a new Worker with the default name (the class + UUID)
	 */
	protected SailPointWorker() {
		this(null);
	}

	/**
	 * A worker constructor that takes a name
	 * @param name The worker name
	 */
	protected SailPointWorker(String name) {
		outcome = new Outcome();
		terminated = new AtomicBoolean();
		if (Util.isNotNullOrEmpty(name)) {
			this.defaultName = name;
		} else {
			this.defaultName = this.getClass().getSimpleName() + " " + UUID.randomUUID();
		}
		children = new ArrayList<>();
		dependencyOutput = new HashMap<>();
		dependencies = new HashMap<>();
		taskCallback = new ArrayList<>();
		this.executeChildren = true;
	}

	/**
	 * A worker constructor that takes a name
	 * @param name The worker name
	 * @param phase The worker phase
	 */
	protected SailPointWorker(String name, int phase) {
		this(name);
		this.phase = phase;
	}

	/**
	 * A worker constructor that takes a name
	 * @param name The worker name
	 * @param phase The worker phase
	 */
	protected SailPointWorker(String name, int phase, int dependentPhase) {
		this(name, phase);
		this.dependentPhase = dependentPhase;
	}

	/**
	 * Adds a child of this task. All child tasks will be resolved before this task runs
	 * and their output will be available to this one. This could be used to implement
	 * phased execution, for example.
	 *
	 * This is NOT the same as a dependency, which is an asynchronous Future that will
	 * block until completion.
	 *
	 * @param childWorker The worker to add as a child
	 */
	public void addChild(SailPointWorker childWorker) {
		childWorker.setParent(this);
		this.children.add(childWorker);
	}

	/**
	 * Adds a dependency Future which will be invoked prior to any child tasks
	 * and also this task.
	 *
	 * @param dependency The dependency task (used to extract the name)
	 * @param workerFuture The future for that dependency
	 */
	public void addDependency(SailPointWorker dependency, Future<?> workerFuture) {
		this.dependencies.put(dependency.getWorkerName(), workerFuture);
	}

	/**
	 * Adds a task callback, which can be used to receive events
	 * @param taskCallback The task callback
	 */
	public void addTaskCallback(TaskCallback<SailPointWorker, Object> taskCallback) {
		this.taskCallback.add(taskCallback);
	}

	/**
	 * Checks for any of the abnormal termination states, throwing an
	 * {@link CancellationException} if one is encountered. Subclasses that
	 * are working in a loop should invoke this method routinely and allow
	 * the exception to abort whatever loop is being run.
	 *
	 * @throws CancellationException if this thread should abnormally terminate
	 */
	protected void checkCancel() throws CancellationException {
		if (isTerminated() || Thread.currentThread().isInterrupted()) {
			throw new CancellationException("Worker has been terminated");
		}
	}

	/**
	 * Executes this task in a SailPoint context that will be dynamically constructed for
	 * it. A private context and {@link Log} will be passed to this method.
	 * @param context The private context to use for this thread worker
	 * @param logger The log attached to this Worker
	 * @return any object, which will be ignored
	 * @throws Exception any exception, which will be logged
	 */
	public abstract Object execute(SailPointContext context, Log logger) throws Exception;

	/**
	 * Retrieve the output of a dependency (listened-to) or child task of this task.
	 *
	 * @param key The name of the child worker
	 * @return The output
	 */
	protected Object getDependencyOutput(String key) {
		return this.dependencyOutput.get(key);
	}

	/**
	 * Returns the dependent phase of this worker
	 * @return The dependent phase
	 */
	public int getDependentPhase() {
		return dependentPhase;
	}

	/**
	 * Gets the exception handler for this worker
	 * @return The exception handler for this worker
	 */
	public ExceptionHandler getExceptionHandler() {
		return exceptionHandler;
	}

	/**
	 * Retrieves the parent task of this one
	 * @return The parent task
	 */
	public SailPointWorker getParent() {
		return parent;
	}

	/**
	 * Returns the phase of this worker
	 * @return The phase of this worker
	 */
	public int getPhase() {
		return phase;
	}

	/**
	 * Returns the name of this worker, which can be used in log messages or as the
	 * name of a partitioned request object. By default, this is generated from the
	 * name of this concrete class (your subclass) and a random UUID.
	 *
	 * Override this method to provide your own more sensible naming scheme.
	 *
	 * @return The worker name
	 */
	public String getWorkerName() {
		return defaultName;
	}

	/**
	 * The core flow of a SailPointWorker. Renames the thread for logging
	 * clarity, resolves any dependencies, and handles success and failure
	 * outcomes by invoking the appropriate callbacks.
	 *
	 * @return The implementation
	 */
	private Object implementation(boolean rethrow) throws Exception {
		// The non-exceptional output of this task
		AtomicReference<Object> result = new AtomicReference<>();

		// The exceptional output of this task
		AtomicReference<Exception> exceptionResult = new AtomicReference<>();

		Log logger = LogFactory.getLog(this.getClass());
		final String originalThreadName = Thread.currentThread().getName();
		this.timeoutTimestamp = System.currentTimeMillis() + timeoutMillis;
		try {
			if (taskCallback != null) {
				// Can't do this in a lambda forEach because this one can throw an exception
				for(TaskCallback<SailPointWorker, Object> tc : taskCallback) {
					tc.beforeStart(this);
				}
			}
			Thread.currentThread().setName("SailPointWorker: " + getWorkerName());
			try {
				Utilities.withPrivateContext((threadContext) -> {
					try {
						waitForDependencies(logger);
						if (this.executeChildren) {
							// Executes the child tasks, storing their outputs
							runChildren(this, logger, threadContext);
						}
						result.set(execute(threadContext, logger));
						threadContext.commitTransaction();
						if (completedCounter != null) {
							completedCounter.incrementAndGet();
						}
						if (taskCallback != null) {
							taskCallback.forEach((tc) -> tc.onSuccess(this, result.get()));
						}
					} catch (Exception e) {
						if (failedCounter != null) {
							failedCounter.incrementAndGet();
						}
						if (exceptionHandler != null) {
							exceptionHandler.handleError(e);
						}
						if (taskCallback != null) {
							taskCallback.forEach((tc) -> tc.onFailure(this, e.getMessage(), e));
						}
						exceptionResult.set(e);
					}
				});
			} finally {
				Thread.currentThread().setName(originalThreadName);
			}
		} finally {
			if (taskCallback != null) {
				taskCallback.forEach((tc) -> tc.afterFinish(this));
			}
			if (this.parent != null && this.parent.dependencyOutput != null && this.dependencyOutput != null) {
				this.parent.dependencyOutput.putAll(this.dependencyOutput);
			}
		}

		if (rethrow && exceptionResult.get() != null) {
			throw exceptionResult.get();
		}

		return result.get();
	}

	/**
	 * Returns true if this worker has been terminated or if it has timed out.
	 */
	public boolean isTerminated() {
		return isTimedOut() || terminated.get();
	}

	/**
	 * Returns true if this task has timed out.
	 *
	 * @return True if this task has timed out.
	 */
	public boolean isTimedOut() {
		return (this.timeoutMillis > 0 && System.currentTimeMillis() < timeoutTimestamp);
	}

	/**
	 * Java deserialization hook to instantiate the transient fields
	 *
	 * @see Serializable
	 *
	 * @param in The input stream
	 * @throws IOException if anything fails
	 * @throws ClassNotFoundException if this is the wrong clas
	 */
	private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
		this.terminated = new AtomicBoolean();
		this.outcome = new Outcome();
		this.dependencyOutput = new HashMap<>();
		this.dependencies = new HashMap<>();
		in.defaultReadObject();
	}

	/**
	 * Invokes this SailPointWorker by constructing a new private SailPointContext,
	 * then invoking the subclass's {@link #execute(SailPointContext, Log)} method,
	 * then handling success or failure according to the optional objects.
	 *
	 * The subclass is responsible for logging and rethrowing any exceptions if
	 * a failure is to be counted.
	 */
	@Override
	public void run() {
		try {
			implementation(false);
		} catch(Exception ignored) {
			// Already handled due to 'false' above
		}
	}

	/**
	 * Directly runs the child tasks for this task. If any child task throws an exception,
	 * or turns out to have timed out upon completion, an exception will be thrown from
	 * this method and no further child tasks will be executed.
	 *
	 * Each child's {@link #execute(SailPointContext, Log)} method is invoked directly.
	 * Children are run in the same SailPointContext as the parent.
	 *
	 * A tree of nested child tasks will be invoked in depth-first order.
	 *
	 * @param logger The logger
	 * @param context The private context created in {@link #implementation(boolean)}
	 * @throws Exception If a child throws an exception
	 */
	private void runChildren(SailPointWorker parent, Log logger, SailPointContext context) throws Exception {
		for(SailPointWorker child : Util.safeIterable(parent.children)) {
			child.timeoutTimestamp = System.currentTimeMillis() + child.timeoutMillis;

			if (!Util.isEmpty(child.children)) {
				runChildren(child, logger, context);
			}
			Object output = child.execute(context, logger);
			if (child.isTimedOut()) {
				throw new CancellationException("Child task " + child.getWorkerName() + " timed out");
			}
			parent.dependencyOutput.put(child.getWorkerName(), output);
		}
	}

	/**
	 * Gets this object as a Runnable, mainly for use with an ExecutorService
	 * @return This object as a Runnable
	 */
	public final Runnable runnable() {
		return this;
	}

	/**
	 * Set the completed counter on this task and any of its children
	 * @param completedCounter The completed counter
	 */
	public void setCompletedCounter(AtomicInteger completedCounter) {
		this.completedCounter = completedCounter;

		if (this.children != null) {
			for(SailPointWorker child : this.children) {
				child.setCompletedCounter(completedCounter);
			}
		}
	}

	/**
	 * Set the dependent phase for this worker. This is only useful if running
	 * the worker as a partitioned Request.
	 *
	 * @param dependentPhase The completed counter
	 */
	public void setDependentPhase(int dependentPhase) {
		this.dependentPhase = dependentPhase;

		// NOTE: Children don't need to copy the parent's phase because they run in the
		// same thread as this one and thus will be in the same phase by default.
	}

	/**
	 * Sets the exception handler for this class.
	 * @param exceptionHandler The exception handler
	 */
	public void setExceptionHandler(ExceptionHandler exceptionHandler) {
		this.exceptionHandler = exceptionHandler;

		if (this.children != null) {
			for(SailPointWorker child : this.children) {
				child.setExceptionHandler(exceptionHandler);
			}
		}
	}

	/**
	 * Sets the failure counter that will be incremented on any worker failure.
	 *
	 * @param failedCounter The failed counter
	 */
	public void setFailedCounter(AtomicInteger failedCounter) {
		this.failedCounter = failedCounter;

		if (this.children != null) {
			for(SailPointWorker child : this.children) {
				child.setFailedCounter(failedCounter);
			}
		}
	}

	/**
	 * Sets the TaskMonitor for this worker and its children. This will be set by
	 * the SPW request executor, among other places.
	 * @param monitor The task monitor
	 */
	public void setMonitor(TaskMonitor monitor) {
		this.monitor = monitor;

		if (this.children != null) {
			for(SailPointWorker child : this.children) {
				child.setMonitor(monitor);
			}
		}
	}

	/**
	 * Sets the parent task of this one to the given value. This can be null
	 * @param parent The parent task
	 */
	protected void setParent(SailPointWorker parent) {
		this.parent = parent;
	}

	/**
	 * Sets the timeout duration in the specified unit.
	 */
	public void setTimeout(int duration, TimeUnit unit) {
		this.timeoutMillis = TimeUnit.MILLISECONDS.convert(duration, unit);
	}

	/**
	 * Sets the phase for this worker. This is only useful if running as a {@link Request}.
	 * @param phase The phase number for this worker
	 */
	public void setPhase(int phase) {
		this.phase = phase;

		// NOTE: Children don't need to copy the parent's phase because they run in the
		// same thread as this one and thus will be in the same phase by default.
	}

	/**
	 * Attempts to terminate the worker
	 *
	 * @return True, only to satisfy the Terminable interface
	 */
	public boolean terminate() {
		this.terminated.set(true);

		return true;
	}

	/**
	 * Returns a Callable object that will implement the logic of this SailPointWorker,
	 * properly returning a value or an exception for {@link java.util.concurrent.Future} purposes.
	 *
	 * This is used mainly because {@link java.util.concurrent.ExecutorService#submit(Runnable)}
	 * gets messy if the same object implements both Runnable and Callable. This must also be
	 * used if you want to chain workers using {@link java.util.concurrent.Future} and the
	 * dependency function.
	 */
	public Callable<Object> toCallable() {
		return () -> this.implementation(true);
	}

	/**
	 * Creates a new recursive task from this worker, for use with a ForkJoinPool
	 * and a tree of child worker tasks.
	 * @return The recursive task
	 */
	public RecursiveTask<List<Object>> toForkJoinTask() {
		return new RecursiveWorkerContainer(Collections.singletonList(this));
	}

	/**
	 * Creates a FutureTask wrapping this object as a Callable
	 * @return The FutureTask
	 */
	public FutureTask<Object> toFutureTask() {
		return new FutureTask<>(toCallable());
	}

	/**
	 * Serializes this SailPointWorker object into a Request suitable for use with {@link com.identityworksllc.iiq.common.request.SailPointWorkerExecutor}. That executor must be associated with the provided RequestDefinition.
	 * @param requestDefinition The request definition associated with {@link com.identityworksllc.iiq.common.request.SailPointWorkerExecutor}
	 * @return The Request containing a serialized object
	 * @throws GeneralException if any failures occur compressing the object
	 * @throws IOException if any failures occur serializing this {@link SailPointWorker}
	 */
	public Request toRequest(RequestDefinition requestDefinition) throws GeneralException, IOException {
		List<SailPointWorker> singletonList = new ArrayList<>();
		singletonList.add(this);

		return SailPointWorker.toRequest(requestDefinition, singletonList);
	}

	/**
	 * @see Object#toString()
	 */
	@Override
	public String toString() {
		return new StringJoiner(", ", SailPointWorker.class.getSimpleName() + "[", "]")
				.add("name='" + getWorkerName() + "'")
				.add("dependencies='" + this.dependencies.keySet() + "'")
				.add("children='" + this.children + "'")
				.toString();
	}

	/**
	 * Resolves any dependencies of this task, noting errors
	 * @param logger The logger used to log dependency errors
	 * @throws GeneralException if any dependency fails
	 */
	private void waitForDependencies(Log logger) throws GeneralException, InterruptedException {
		boolean dependencyFailure = false;
		if (dependencies != null) {
			for (String key : dependencies.keySet()) {
				long remainingTimeout = this.timeoutTimestamp - System.currentTimeMillis();
				if (isTimedOut()) {
					throw new InterruptedException();
				}

				// Note that this will block until the Future resolves. It will consume
				// a thread in your thread pool, so you should probably use a cachedThreadPool.
				Future<?> future = dependencies.get(key);
				if (future != null) {
					try {
						if (this.timeoutMillis > 0 && remainingTimeout > 0) {
							// Don't hang forever if we've specified a timeout on this worker
							this.dependencyOutput.put(key, future.get(remainingTimeout, TimeUnit.MILLISECONDS));
						} else {
							// Hangs forever (or until interrupted)
							this.dependencyOutput.put(key, future.get());
						}
					} catch(InterruptedException e) {
						throw e;
					} catch(Exception e) {
						// This will almost certainly be an execution exception
						logger.error("Observed dependency task " + key + " had an exception", e);
						dependencyFailure = true;
					}
				}
			}
		}
		if (dependencyFailure) {
			throw new GeneralException("One or more dependencies of worker " + getWorkerName() + " failed. See the logs for details.");
		}
	}

}
