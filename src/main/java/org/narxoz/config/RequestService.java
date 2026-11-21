package org.narxoz.config;

import org.narxoz.domain.BusinessKey;
import org.narxoz.domain.DuplicateRequest;
import org.narxoz.domain.PurchaseRequest;
import org.narxoz.domain.RequestId;
import org.narxoz.domain.RequestNotFound;
import org.narxoz.domain.RequestNotifier;
import org.narxoz.domain.RequestStatus;
import org.narxoz.domain.Rule;
import org.narxoz.domain.TransitionForbidden;
import org.narxoz.persistence.RequestJpa;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class RequestService {
    private static final Logger log = LoggerFactory.getLogger(RequestService.class);

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
    public PurchaseRequest register(RequestId id, String businessKey, String title) {
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
        return new PurchaseRequest(id, key, RequestStatus.DRAFT, title);
    }

    /** Read only: no register, no move, no flush. A missing row is RequestNotFound. */
    @Transactional(readOnly = true)
    public PurchaseRequest get(RequestId id) {
        return requests.findById(id.uuid())
                .orElseThrow(() -> new RequestNotFound(id));
    }

    /**
     * Lab 5: the locked move DRAFT -> APPROVED. The URL rule already lets only APPROVER in;
     * @PreAuthorize is the second lock, so another controller cannot forget the role.
     * The actor comes from the token (Authentication.getName()), never from a JSON field.
     */
    @PreAuthorize("hasRole('APPROVER')")
    @Transactional
    public PurchaseRequest approve(RequestId id, String actor) {
        PurchaseRequest current = requests.findById(id.uuid())
                .orElseThrow(() -> new RequestNotFound(id));
        try {
            rules.check(id, current.status(), RequestStatus.APPROVED);
        } catch (IllegalStateException ex) {
            throw new TransitionForbidden(current.status(), RequestStatus.APPROVED); // the row does not change
        }
        requests.updateStatus(id.uuid(), RequestStatus.APPROVED);
        notifier.statusChanged(id, RequestStatus.APPROVED);
        log.info("purchase request {} approved by {}", id.value(), actor); // the name, never the token
        return new PurchaseRequest(id, current.businessKey(), RequestStatus.APPROVED, current.title());
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
