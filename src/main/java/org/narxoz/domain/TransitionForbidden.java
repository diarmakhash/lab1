package org.narxoz.domain;

/**
 * The status table does not allow this move (for example APPROVED -> APPROVED).
 * Unchecked, no Spring: only the web advice knows that this becomes HTTP 409.
 */
public class TransitionForbidden extends RuntimeException {
    public TransitionForbidden(RequestStatus from, RequestStatus to) {
        super("purchase request cannot move from " + from + " to " + to);
    }
}
