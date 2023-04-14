package com.identityworksllc.iiq.common.minimal;

import sailpoint.object.SailPointObject;

/**
 *
 */
public class TooManyResultsException extends IllegalArgumentException {

	/**
	 * 
	 */
	private static final long serialVersionUID = -8856651429145974695L;

	/**
	 * @param cls The class we were querying
	 * @param queryString The query string being run
	 * @param resultSize The size of the results
	 */
	public TooManyResultsException(Class<? extends SailPointObject> cls, String queryString, int resultSize) {
		super("Too many results for " + cls.getName() + ", query = " + queryString + ", size = " + resultSize);
	}
	
}
