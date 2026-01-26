package com.identityworksllc.iiq.common.task;

import com.fasterxml.jackson.core.type.TypeReference;
import com.identityworksllc.iiq.common.Ref;
import com.identityworksllc.iiq.common.Utilities;
import sailpoint.api.SailPointContext;
import sailpoint.object.*;
import sailpoint.task.TaskMonitor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * A threaded task executor to run an iterator rule or script. The rule or script will
 * be invoked in parallel for each of the objects returned by the iterator query.
 * The object will be passed as 'object' to Beanshell.
 *
 * NOTE that if you are iterating over a very large set of Sailpoint objects, it
 * will be vastly more efficient to return a string ID from your object retriever
 * and then look up each object in the iterator rule or script. This will also avoid
 * problems with multiple contexts acting on the same object. The task option
 * 'extractReferences' will do this for you automatically and is true by default.
 */
public class ThreadedRuleRunner extends AbstractThreadedObjectIteratorTask<Object> {

	/**
	 * The rule action type
	 */
	enum RuleActionType {
		rule,
		script
	}
	/**
	 * An optional rule to run after the batch
	 */
	protected Rule afterBatchRule;
	/**
	 * An optional script to run after the batch
	 */
	protected Script afterBatchScript;
	/**
	 * An optional rule to run before the batch
	 */
	protected Rule beforeBatchRule;
	/**
	 * An optional script to run prior to the batch
	 */
	protected Script beforeBatchScript;
	/**
	 * True if we should extract references to the objects
	 */
	private boolean extractReferences;
	/**
	 * The iterator rule, if specified
	 */
	protected Rule iteratorRule;
	/**
	 * The iterator script, if specified
	 */
	protected Script iteratorScript;

	/**
	 * JSON arguments
	 */
	protected Map<String, Object> extraArguments;

	/**
	 * The state that is shared between each item in a batch
	 */
	protected ThreadLocal<Map<String, Object>> threadState = ThreadLocal.withInitial(ConcurrentHashMap::new);

	/**
	 * Invoked by the worker thread after each batch
	 * @param threadContext The context for this thread
	 * @throws GeneralException if anything fails
	 */
	@Override
	public void afterBatch(SailPointContext threadContext) throws GeneralException {
		super.afterBatch(threadContext);

		Map<String, Object> arguments = new HashMap<>();
		if (extraArguments != null) {
			arguments.putAll(this.extraArguments);
		}

		arguments.put("context", threadContext);
		arguments.put("log", log);
		arguments.put("logger", log);
		arguments.put("state", threadState.get());

		if (this.afterBatchRule != null) {
			threadContext.runRule(this.afterBatchRule, arguments);
		} else if (this.afterBatchScript != null) {
			Script tempScript = Utilities.getAsScript(this.afterBatchScript);
			threadContext.runScript(tempScript, arguments);
		}
	}

	/**
	 * Invoked by the worker thread before each batch
	 * @param threadContext The context for this thread
	 * @throws GeneralException if anything fails
	 */
	@Override
	public void beforeBatch(SailPointContext threadContext) throws GeneralException {
		super.beforeBatch(threadContext);
		threadState.get().clear();

		Map<String, Object> arguments = new HashMap<>();
		if (extraArguments != null) {
			arguments.putAll(this.extraArguments);
		}

		arguments.put("context", threadContext);
		arguments.put("log", log);
		arguments.put("logger", log);
		arguments.put("state", threadState.get());

		if (this.beforeBatchRule != null) {
			threadContext.runRule(this.beforeBatchRule, arguments);
		} else if (this.beforeBatchScript != null) {
			Script tempScript = Utilities.getAsScript(this.beforeBatchScript);
			threadContext.runScript(tempScript, arguments);
		}
	}

	/**
	 * If the extract reference flag is set, and this is a SailPointObject, transforms it
	 * into a {@link Reference} object instead. This will make everything
	 * enormously more efficient and avoid weird issues with Hibernate context.
	 *
	 * @param input The input
	 * @return The reference, or the original object
	 */
	@Override
	protected Object convertObject(Object input) {
		if (this.extractReferences && input instanceof SailPointObject) {
			return Ref.of((SailPointObject) input);
		} else {
			return input;
		}
	}

	/**
	 * Extracts the arguments passed to this task. This is the ONLY place that
	 * you ought to use the parent context.
	 *
	 * @param args The arguments to read
	 * @throws Exception if there are any failures during parsing
	 */
	@Override
	protected void parseArgs(Attributes<String, Object> args) throws Exception {
		// Mandatory
		super.parseArgs(args);

		Object ruleConfig = args.get("ruleConfig");
		if (ruleConfig instanceof Map) {
			this.extraArguments = new HashMap<>((Map<String, Object>) ruleConfig);
		} else if (ruleConfig instanceof String) {
			String ruleConfigStr = Util.otoa(ruleConfig).trim();
			if (ruleConfigStr.startsWith("{")) {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                Map<String, Object> jsonMap = mapper.readValue(ruleConfigStr, new TypeReference<>() {});
				if (jsonMap != null) {
					this.extraArguments = jsonMap;
				}
			} else {
				this.extraArguments = new HashMap<>();
				List<String> values = Util.csvToList(ruleConfigStr);
				if ((values.size() % 2) == 1) {
					throw new IllegalArgumentException("If you specify a 'ruleConfig' in CSV format, there must be an even number of key-value pairs");
				}
				for(int i = 0; i < values.size(); i++) {
					String key = values.get(i);
					String value = values.get(i + 1);

					if (Util.isNotNullOrEmpty(key) && Util.isNotNullOrEmpty(value)) {
						this.extraArguments.put(key, value);
					}
				}
			}
		} else if (ruleConfig != null) {
			throw new IllegalArgumentException("If specified, a 'ruleConfig' must be either a String or a Map");
		}

		String actionTypeName = args.getString("iteratorType");
		if (Util.isNullOrEmpty(actionTypeName)) {
			throw new IllegalArgumentException("A non-null 'iteratorType' must be set either 'rule' or 'script'");
		}

		this.extractReferences = args.getBoolean("extractReferences", true);

		if (args.containsKey("beforeBatchRule")) {
			this.beforeBatchRule = context.getObject(Rule.class, args.getString("beforeBatchRule"));
			if (this.beforeBatchRule == null) {
				throw new IllegalArgumentException("The after batch rule specified (" + args.get("beforeBatchRule") + ") does not exist");
			}
			this.beforeBatchRule.load();
		}

		if (args.containsKey("beforeBatchScript")) {
			this.beforeBatchScript = new Script();
			this.beforeBatchScript.setSource(args.getString("beforeBatchScript"));
		}

		if (args.containsKey("afterBatchRule")) {
			this.afterBatchRule = context.getObject(Rule.class, args.getString("afterBatchRule"));
			if (this.afterBatchRule == null) {
				throw new IllegalArgumentException("The after batch rule specified (" + args.get("afterBatchRule") + ") does not exist");
			}
			this.afterBatchRule.load();
		}

		if (args.containsKey("afterBatchScript")) {
			this.afterBatchScript = new Script();
			this.afterBatchScript.setSource(args.getString("afterBatchScript"));
		}

		RuleActionType actionType = RuleActionType.valueOf(actionTypeName);

		if (actionType == RuleActionType.rule) {
			String ruleNameOrId = args.getString("iteratorRule");
			if (Util.isNotNullOrEmpty(ruleNameOrId)) {
				this.iteratorRule = context.getObject(Rule.class, ruleNameOrId);
				if (this.iteratorRule == null) {
					throw new IllegalArgumentException("The iterator rule specified (" + ruleNameOrId + ") does not exist");
				}
				this.iteratorRule.load();
			} else {
				throw new IllegalArgumentException("You must specify a value for iteratorRule for type = rule");
			}
		} else if (actionType == RuleActionType.script) {
			if (Util.isNotNullOrEmpty(args.getString("iteratorScript"))) {
				this.iteratorScript = new Script();
				iteratorScript.setSource(args.getString("iteratorScript"));
			} else {
				throw new IllegalArgumentException("You must specify a value for iteratorScript for type = script");
			}
		} else {
			throw new IllegalArgumentException("Unsupported action type: " + actionType);
		}
	}

	/**
	 * Executes this rule or script against the given object
	 * @param threadContext The context created for this specific thread
	 * @param parameters The parameters created for this thread
	 * @param obj The input object for this thread
	 * @return Always null (no meaningful result)
	 * @throws GeneralException if any failures occur
	 */
	public Object threadExecute(SailPointContext threadContext, Map<String, Object> parameters, Object obj) throws GeneralException {
		if (log.isDebugEnabled()) {
			log.debug("Processing object " + obj);
		}
		TaskMonitor monitor = new TaskMonitor(threadContext, taskResult);

		if (obj instanceof Reference && this.extractReferences) {
			obj = ((Reference)obj).resolve(threadContext);
			if (log.isDebugEnabled()) {
				log.debug("Object reference resolved to " + obj);
			}
		}
		if (!terminated.get()) {
			Map<String, Object> arguments = new HashMap<>();
			if (extraArguments != null) {
				arguments.putAll(this.extraArguments);
			}

			arguments.put("context", threadContext);
			arguments.put("log", Optional.ofNullable(Util.get(parameters, "log")).orElse(log));
			arguments.put("logger", Optional.ofNullable(Util.get(parameters, "log")).orElse(log));
			arguments.put("object", obj);
			arguments.put("state", threadState.get());
			arguments.put("monitor", monitor);
			arguments.put("worker", parameters.get("worker"));
			arguments.put("taskListener", parameters.get("taskListener"));
			arguments.put("terminated", (Supplier<Boolean>) terminated::get);
			if (iteratorRule != null) {
				threadContext.runRule(iteratorRule, arguments);
			} else {
				Script tempScript = Utilities.getAsScript(iteratorScript);
				threadContext.runScript(tempScript, arguments);
			}
		}
		return null;
	}

}
