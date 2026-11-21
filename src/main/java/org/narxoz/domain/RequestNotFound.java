package org.narxoz.domain;

/**
 * No purchase request has this id. Unchecked, no Spring:
 * only the web advice knows that this becomes HTTP 404.
 */
public class RequestNotFound extends RuntimeException {
    public RequestNotFound(RequestId id) {
        super("purchase request not found: " + id.value());
    }
}
