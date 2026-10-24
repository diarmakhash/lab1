package org.narxoz.domain;

import java.util.UUID;

/**
 * Surrogate id of a purchase request. It is the primary key in the database.
 * The human number (PR-19) is a separate BusinessKey.
 */
public record RequestId(String value) {
    public RequestId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ID не может быть пустым");
        }
        UUID.fromString(value); // not a UUID -> IllegalArgumentException
    }

    public static RequestId newId() {
        return new RequestId(UUID.randomUUID().toString());
    }

    public UUID uuid() {
        return UUID.fromString(value);
    }
}
