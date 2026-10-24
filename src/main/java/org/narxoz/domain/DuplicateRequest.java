package org.narxoz.domain;

/**
 * A purchase request with this business key already exists.
 * Unchecked on purpose: when it leaves a @Transactional method, Spring rolls back.
 */
public class DuplicateRequest extends RuntimeException {
    public DuplicateRequest(String businessKey) {
        super("duplicate purchase request: " + businessKey);
    }
}
