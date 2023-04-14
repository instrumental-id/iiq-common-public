package com.identityworksllc.iiq.common.minimal;

import org.apache.commons.logging.LogFactory;

import com.identityworksllc.iiq.common.minimal.logging.SLogger;

import sailpoint.api.SailPointContext;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The base class for each Utilities class that contains common functions to all
 */
@SuppressWarnings("unused")
public abstract class AbstractBaseUtility {
	
	/**
	 * The SailPointContext
	 */
	protected final SailPointContext context;

	protected final AtomicBoolean debug;

	/**
	 * The logger for this class
	 */
	protected final SLogger log;
	
	/**
	 * Constructor, builds some common functions
	 */
	public AbstractBaseUtility(SailPointContext context) {
		this.log = new SLogger(LogFactory.getLog(this.getClass()));
		this.context = context;
		this.debug = new AtomicBoolean();
	}

	/**
	 * Injects this object into the given Beanshell context, making all of this
	 * object's methods available as 'static' methods in Beanshell.
	 * @param beanshell The beanshell context to inject into
	 */
	public void inject(bsh.This beanshell) {
		beanshell.getNameSpace().importObject(this);
	}

	/**
	 * Returns true if the debug flag has been set on this utility
	 * @return The debug flag state
	 */
	public boolean isDebug() {
		return this.debug.get();
	}

	/**
	 * Sets or unsets the debug flag
	 * @param debug The new debug flag state
	 */
	public final void setDebug(boolean debug) {
		this.debug.set(debug);
	}
	
}
