package org.narxoz.config;

import org.narxoz.domain.BusinessKey;
import org.narxoz.domain.DuplicateRequest;
import org.narxoz.domain.RequestId;
import org.narxoz.domain.RequestNotifier;
import org.narxoz.domain.RequestStatus;
import org.narxoz.domain.Rule;
import org.narxoz.persistence.RequestJpa;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class RequestService {
    private final Rule rules;
    private final RequestJpa requests;
    private final RequestNotifier notifier;

    public RequestService(Rule rules, RequestJpa requests, RequestNotifier notifier) {
        this.rules = rules;
        this.requests = requests;
        this.notifier = notifier;
    }

    public RequestStatus processMove(RequestId id, RequestStatus from, RequestStatus to) {
        rules.check(id, from, to);
        notifier.statusChanged(id, to);
        return to;
    }

    /** One transaction per call: a new purchase request starts as DRAFT. */
    @Transactional
    public void register(RequestId id, String businessKey, String title) {
        BusinessKey key = new BusinessKey(businessKey);
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Title не может быть пустым");
        }
        try {
            requests.insert(id.uuid(), key.value(), RequestStatus.DRAFT, title);
        } catch (DataIntegrityViolationException ex) {
            // SQLState 23505. Rethrow as a domain error, never swallow.
            throw new DuplicateRequest(key.value());
        }
    }

    /**
     * Lab demo: ONE transaction, two INSERTs with the same key (two different UUIDs).
     * The second one throws DuplicateRequest -> the whole transaction rolls back (count 0).
     */
    @Transactional
    public void insertTwice(String businessKey) {
        requests.insert(UUID.randomUUID(), businessKey, RequestStatus.DRAFT, "Laptops for the lab");
        try {
            requests.insert(UUID.randomUUID(), businessKey, RequestStatus.DRAFT, "Laptops again");
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateRequest(businessKey); // unchecked -> rollback
        }
    }
}
