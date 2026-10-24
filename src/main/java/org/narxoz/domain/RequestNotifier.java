package org.narxoz.domain;

/**
 * Outbound port: tells the outside world that a request changed status.
 * Later (HTTP) it will live in the client package; in tests it is a Mockito mock.
 */
public interface RequestNotifier {
    void statusChanged(RequestId id, RequestStatus to);
}
