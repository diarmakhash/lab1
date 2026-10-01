package org.narxoz.domain;

public class TransitionRule implements Rule {
    @Override
    public void check(RequestId id, RequestStatus from, RequestStatus to) {
        if (from == RequestStatus.DRAFT && to == RequestStatus.CANCELLED) {
            throw new IllegalStateException("Cannot transition from DRAFT to CANCELLED directly");
        }
    }
}