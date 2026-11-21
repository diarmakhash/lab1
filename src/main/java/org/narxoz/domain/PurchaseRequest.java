package org.narxoz.domain;

/**
 * What the service hands back: a domain view of one purchase request.
 * Not the JPA entity and not the JSON body.
 */
public record PurchaseRequest(RequestId id, BusinessKey businessKey, RequestStatus status, String title) {
}
