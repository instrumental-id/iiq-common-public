package com.identityworksllc.iiq.common.plugin;

/**
 * A meta-object that can be returned by a {@link BaseCommonPluginResource#handle(BaseCommonPluginResource.PluginAction)}
 * implementation to specify both a return object and a status in the return value. This should prevent implementers
 * from needing to return a {@link javax.ws.rs.core.Response} in most cases.
 *
 * @param <T> The type of the wrapped object
 */
public class ErrorResponse<T> {
    /**
     * The response code to use
     */
    private final int responseCode;

    /**
     * The wrapped object, which must be serializable by JAX-RS
     */
    private final T wrappedObject;

    /**
     * Constructs a new error response wrapper object
     *
     * @param responseCode The response code to return to the REST client
     * @param wrappedObject The object to return to the REST client
     */
    public ErrorResponse(int responseCode, T wrappedObject) {
        this.responseCode = responseCode;
        this.wrappedObject = wrappedObject;
    }

    /**
     * Gets the response code for this error response
     * @return The response code
     */
    public int getResponseCode() {
        return responseCode;
    }

    /**
     * Gets the wrapped object for this error response
     * @return The wrapped object
     */
    public T getWrappedObject() {
        return wrappedObject;
    }
}
