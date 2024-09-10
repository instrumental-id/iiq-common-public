package com.identityworksllc.iiq.common.plugin.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A simplified VO object that can wrap an Identity and some of its useful
 * properties.
 */
public class IdentityVO extends ObjectSummary {

	private boolean correlated;
	private boolean inactive;
	private String localIdentifier;
	private boolean workgroup;

	@JsonProperty
	public String getLocalIdentifier() {
		return localIdentifier;
	}

	@JsonProperty
	public boolean isCorrelated() {
		return correlated;
	}

	@JsonProperty
	public boolean isInactive() {
		return inactive;
	}

	@JsonProperty
	public boolean isWorkgroup() {
		return workgroup;
	}

	public void setCorrelated(boolean correlated) {
		this.correlated = correlated;
	}

	public void setInactive(boolean inactive) {
		this.inactive = inactive;
	}

	public void setLocalIdentifier(String localIdentifier) {
		this.localIdentifier = localIdentifier;
	}

	public void setWorkgroup(boolean workgroup) {
		this.workgroup = workgroup;
	}
	
}
