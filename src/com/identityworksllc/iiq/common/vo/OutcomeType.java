package com.identityworksllc.iiq.common.vo;

/**
 * The various outcome types that an operation can have
 */
public enum OutcomeType {
    Pending,
    Running,
    Success,
    Failure,
    Warning,
    Terminated,
    Skipped,
    Multiple;
}
