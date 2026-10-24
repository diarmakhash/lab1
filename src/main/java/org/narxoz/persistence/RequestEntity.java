package org.narxoz.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One row of purchase_request. This is NOT the domain:
 * it only names the columns that V1__create_purchase_request.sql created.
 */
@Entity
@Table(name = "purchase_request")
public class RequestEntity {

    @Id
    private UUID id; // the UUID from RequestId.newId(); no @GeneratedValue

    @Column(name = "business_key", nullable = false, unique = true)
    private String businessKey;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String title;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt; // filled by DEFAULT now() in PostgreSQL

    protected RequestEntity() {
        // for Hibernate
    }

    public RequestEntity(UUID id, String businessKey, String status, String title) {
        this.id = id;
        this.businessKey = businessKey;
        this.status = status;
        this.title = title;
    }

    public UUID getId() { return id; }
    public String getBusinessKey() { return businessKey; }
    public String getStatus() { return status; }
    public String getTitle() { return title; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
