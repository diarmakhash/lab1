package org.narxoz.domain;

public interface Rule {
    void check(RequestId id, RequestStatus from, RequestStatus to);
}