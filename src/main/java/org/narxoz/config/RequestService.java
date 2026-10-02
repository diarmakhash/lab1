package org.narxoz.config;

import org.narxoz.domain.RequestId;
import org.narxoz.domain.RequestStatus;
import org.narxoz.domain.Rule;
import org.springframework.stereotype.Service;

@Service
public class RequestService {
    private final Rule rules;

    public RequestService(Rule rules) {
        this.rules = rules;
    }

    public RequestStatus processMove(RequestId id, RequestStatus from, RequestStatus to) {
        rules.check(id, from, to);
        return to;
    }
}