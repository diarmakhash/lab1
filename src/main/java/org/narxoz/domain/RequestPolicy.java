package org.narxoz.domain;

import java.util.List;
import java.util.Objects;

public class RequestPolicy {
    private final List<Rule> rules;

    public RequestPolicy(List<Rule> rules) {
        this.rules = rules;
    }

    public RequestStatus move(RequestId id, RequestStatus from, RequestStatus to) {
        Objects.requireNonNull(id, "ID не может быть null");
        for (Rule rule : rules) {
            rule.check(id, from, to);
        }
        return to;
    }
}