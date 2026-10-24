package org.narxoz.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.narxoz.config.Application;
import org.narxoz.config.RequestService;
import org.narxoz.domain.DuplicateRequest;
import org.narxoz.domain.RequestId;
import org.narxoz.domain.RequestStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs against the real PostgreSQL from application.yml.
 * NO @Transactional here: every count must see what was really committed.
 */
@SpringBootTest(classes = Application.class)
class RequestPersistenceTest {

    @Autowired
    private RequestService service;

    @Autowired
    private RequestJdbc requests;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() throws IOException {
        jdbc.execute("DROP TABLE IF EXISTS purchase_request");
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/schema.sql")));
    }

    @Test
    void secondStatementRollsBack() {
        assertThrows(DuplicateRequest.class, () -> service.insertTwice("PR-19"));

        assertEquals(0, requests.count("PR-19"));
    }

    @Test
    void secondRequestKeepsTheFirst() {
        service.register(RequestId.newId(), "PR-19", "Laptops for the lab");

        assertThrows(DuplicateRequest.class,
                () -> service.register(RequestId.newId(), "PR-19", "Laptops again"));

        assertEquals(1, requests.count("PR-19"));
    }

    @Test
    void statusIsStoredAsTextAndReadBack() {
        service.register(RequestId.newId(), "PR-20", "Monitors");

        assertEquals(Optional.of(RequestStatus.DRAFT), requests.findStatus("PR-20"));
    }

    @Test
    void checkConstraintRejectsUnknownStatus() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO purchase_request (id, business_key, status, title) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), "PR-21", "LOST", "Bad status"));

        assertEquals(0, requests.count("PR-21"));
    }

    @Test
    void unknownStatusTextFromDatabaseThrows() {
        assertThrows(IllegalStateException.class, () -> RequestJdbc.toStatus("LOST"));
    }

    @Test
    void blankBusinessKeyIsRejectedBeforeSql() {
        assertThrows(IllegalArgumentException.class,
                () -> service.register(RequestId.newId(), "  ", "Empty key"));

        assertEquals(0, requests.count("  "));
    }
}
