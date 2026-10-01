package org.narxoz.config;

import org.narxoz.domain.RequestId;
import org.narxoz.domain.RequestStatus;
import org.narxoz.domain.Rule;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RequestService {
    private final List<Rule> rules;

    public RequestService(List<Rule> rules) {
        this.rules = rules;
    }

    public RequestStatus processMove(RequestId id, RequestStatus from, RequestStatus to) {
        for (Rule rule : rules) {
            rule.check(id, from, to);
        }
        return to;
    }
}