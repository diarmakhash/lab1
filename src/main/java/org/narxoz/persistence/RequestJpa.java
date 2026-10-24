package org.narxoz.persistence;

import jakarta.persistence.EntityManager;
import org.narxoz.domain.RequestStatus;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA repository. EntityManager.persist is the INSERT; flush sends it right away,
 * so a duplicate business_key fails inside the service method, where it is caught.
 * Nothing outside persistence sees RequestEntity.
 */
@Repository
public class RequestJpa {
    private final EntityManager em;

    public RequestJpa(EntityManager em) {
        this.em = em;
    }

    public void insert(UUID id, String businessKey, RequestStatus status, String title) {
        em.persist(new RequestEntity(id, businessKey, toText(status), title));
        em.flush(); // without flush the UNIQUE error would appear only at commit
    }

    public int count(String businessKey) {
        Long n = em.createQuery("""
                select count(r) from RequestEntity r
                where r.businessKey = :key
                """, Long.class)
                .setParameter("key", businessKey)
                .getSingleResult();
        return n.intValue();
    }

    public Optional<RequestStatus> findStatus(String businessKey) {
        List<String> rows = em.createQuery("""
                select r.status from RequestEntity r
                where r.businessKey = :key
                """, String.class)
                .setParameter("key", businessKey)
                .getResultList();
        return rows.stream().findFirst().map(RequestJpa::toStatus);
    }

    static String toText(RequestStatus status) {
        return status.name();
    }

    static RequestStatus toStatus(String text) {
        try {
            return RequestStatus.valueOf(text);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new IllegalStateException("unknown status in database: " + text);
        }
    }
}
