package org.narxoz.persistence;

import org.narxoz.domain.DuplicateRequest;
import org.narxoz.domain.RequestStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Plain JDBC, no JPA. The only place in the project that knows SQL.
 * Callers never see a Connection or a ResultSet.
 */
@Repository
public class RequestJdbc {
    private static final String INSERT = """
            INSERT INTO purchase_request (id, business_key, status, title)
            VALUES (?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbc;

    public RequestJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID id, String businessKey, RequestStatus status, String title) {
        try {
            jdbc.update(INSERT, id, businessKey, toText(status), title);
        } catch (DuplicateKeyException ex) {
            // SQLState 23505 (a subclass of DataIntegrityViolationException).
            // Rethrow as a domain error, never swallow: the transaction must roll back.
            throw new DuplicateRequest(businessKey);
        }
    }

    public int count(String businessKey) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM purchase_request WHERE business_key = ?",
                Integer.class,
                businessKey);
        return n == null ? 0 : n;
    }

    public Optional<RequestStatus> findStatus(String businessKey) {
        List<String> rows = jdbc.queryForList(
                "SELECT status FROM purchase_request WHERE business_key = ?",
                String.class,
                businessKey);
        return rows.stream().findFirst().map(RequestJdbc::toStatus);
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
