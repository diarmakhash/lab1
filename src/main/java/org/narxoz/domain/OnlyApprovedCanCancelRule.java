package org.narxoz.domain;

public class OnlyApprovedCanCancelRule implements Rule {
    @Override
    public void check(RequestId id, RequestStatus from, RequestStatus to) {
        if (to == RequestStatus.CANCELLED && from != RequestStatus.APPROVED) {
            throw new IllegalStateException("Only APPROVED requests can be CANCELLED");
        }
    }
}