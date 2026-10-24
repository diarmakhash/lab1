package org.narxoz.domain;

/**
 * The human number of a purchase request (PR-19).
 * The database says NOT NULL UNIQUE; blank strings are rejected here, in Java.
 */
public record BusinessKey(String value) {
    public BusinessKey {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Business key не может быть пустым");
        }
    }
}
