package com.identityworksllc.iiq.common.minimal.logging;

/**
 * An interface to abstract the log capturing between 7.x (which uses log4j) and 8.x (which uses log4j2)
 */
public interface CaptureLogs {
	
	/**
	 * Capture the most interesting loggers
	 */
	public void captureMost();

	/**
	 * Capture a specific set of loggers
	 * @param names The names of the loggers to capture
	 */
	public void capture(String... names);
	
}
