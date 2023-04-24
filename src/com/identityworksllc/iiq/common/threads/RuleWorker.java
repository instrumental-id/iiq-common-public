package com.identityworksllc.iiq.common.threads;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.logging.Log;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.Attributes;
import sailpoint.object.Rule;
import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.XMLReferenceResolver;

/**
 * A worker that runs a rule and returns its result. The Rule object will be
 * loaded by each {@link RuleWorker} instance (per thread).
 */
@SuppressWarnings("unused")
public class RuleWorker extends SailPointWorker implements Serializable {
	
	/**
	 * Serial version UID
	 */
	private static final long serialVersionUID = 3L;

	/**
	 * Deep-copies a rule so that it can be used as a container for the worker arguments.
	 *
	 * @param input The input rule to copy
	 * @return The copied rule
	 */
	private static Rule copyRule(Rule input) {
		Rule output = new Rule();
		output.setName(input.getName());
		output.setLanguage(input.getLanguage());
		output.setSource(input.getSource());
		output.setType(input.getType());
		if (input.getAttributes() != null) {
			output.setAttributes(new Attributes<>(input.getAttributes()));
		} else {
			output.setAttributes(new Attributes<>());
		}
		if (input.getReferencedRules() != null) {
			output.setReferencedRules(new ArrayList<>(input.getReferencedRules()));
		}
		return output;
	}

	/**
	 * The arguments to pass to the rule
	 */
	protected transient Map<String, Object> arguments;
	/**
	 * The rule name to run
	 */
	protected transient String ruleName;
	/**
	 * The cached rule object (if that constructor is used)
	 */
	protected transient Rule ruleObject;

	/**
	 * Used by the Serializable interface to begin deserialization
	 */
	public RuleWorker() {
		/* nothing by default */
	}

	/**
	 * Constructor
	 * @param ruleName The rule name
	 * @param arguments The rule arguments
	 */
	public RuleWorker(String ruleName, Map<String, Object> arguments) {
		this.ruleName = ruleName;
		if (arguments == null) {
			this.arguments = new HashMap<>();
		} else {
			this.arguments = arguments;
		}
		this.ruleObject = null;
	}

	/**
	 * Constructor
	 * @param theRule The rule object
	 * @param arguments The rule arguments
	 */
	public RuleWorker(Rule theRule, Map<String, Object> arguments) {
		this(theRule.getName(), arguments);
		this.ruleObject = theRule;
	}

	/**
	 * Executes the Rule using the arguments provided and the provided thread context.
	 *
	 * @param threadContext The private context to use for this thread worker
	 * @param logger The log attached to this Worker
	 * @return The result of the rule execution (usually ignored)
	 * @throws Exception if anything goes wrong
	 */
	@Override
	public Object execute(SailPointContext threadContext, Log logger) throws Exception {
		try {
			Rule theRule;
			if (ruleObject != null) {
				theRule = ruleObject;
			} else {
				theRule = threadContext.getObject(Rule.class, ruleName);
			}
			Map<String, Object> amendedArguments = new HashMap<>(arguments);
			amendedArguments.put("context", threadContext);
			amendedArguments.put("log", logger);
			amendedArguments.put("logger", logger);
			return threadContext.runRule(theRule, amendedArguments);
		} catch(Exception e) {
			logger.error("Unable to execute rule", e);
			throw e;
		}
	}

	/**
	 * Reads this RuleWorker as follows:
	 *
	 * Reads the boolean flag indicating whether the worker stored a Rule object.
	 *
	 * Reads and parses the serialized XML string representing a Rule object.
	 *
	 * If the boolean flag is true, stores the resulting Rule in ruleObject.
	 *
	 * If the boolean flag is false, retrieves only the name from the resulting Rule and stores it in ruleName.
	 *
	 * The arguments are extracted from the deserialized Rule object.
	 *
	 * @param in The callback from the object input stream
	 * @throws IOException if anything goes wrong reading the object
	 * @throws ClassNotFoundException if anything goes wrong finding the appropriate classes (unlikely)
	 */
	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		try {
			boolean hasRuleObject = in.readBoolean();
			String ruleXml = in.readUTF();
			Rule deserialized = (Rule) AbstractXmlObject.parseXml(SailPointFactory.getCurrentContext(), ruleXml);
			if (hasRuleObject) {
				ruleObject = deserialized;
			}
			ruleName = deserialized.getName();
			if (deserialized.getAttributes() != null) {
				String argKey = this.getClass().getName() + ".arguments";
				arguments = (Map<String, Object>) deserialized.getAttributeValue(argKey);
				deserialized.getAttributes().remove(argKey);
			}
		} catch(GeneralException e) {
			throw new IOException(e);
		}
	}

	/**
	 * Writes out this RuleWorker as follows:
	 *
	 * If this worker has a Rule object, a boolean true is written to the stream.
	 * If it has only a Rule name, a boolean false is written to the stream.
	 *
	 * If this worker has a Rule object, it is copied.
	 * If this worker has a Rule name, a new Rule object is created with only the name set.
	 *
	 * In both cases, the input Attributes map for this worker is added as an attribute on the
	 * Rule object. (This is why we copied the 'real' one.)
	 *
	 * The Rule object is serialized to XML and written to the stream.
	 *
	 * @param out The callback passed from the output stream
	 * @throws IOException if any failures occur serializing the object
	 */
	private void writeObject(ObjectOutputStream out) throws IOException {
		try {
			Rule writeRule;
			if (ruleObject != null) {
				out.writeBoolean(true);
				writeRule = copyRule(ruleObject);
			} else {
				out.writeBoolean(false);
				writeRule = new Rule();
				writeRule.setName(ruleName);
			}
			writeRule.setAttribute(this.getClass().getName() + ".arguments", arguments);
			out.writeUTF(writeRule.toXml());
		} catch(GeneralException e) {
			throw new IOException(e);
		}
	}
}
