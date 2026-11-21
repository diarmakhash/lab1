# Purchase Request System - Lab 4 (REST contract + Spring Boot platform)

## Product
We build a purchase request tracking system. People track a purchase request as it moves through statuses on its way to ordering or cancellation.

- `RequestId` - surrogate id (UUID from `RequestId.newId()`), the primary key in the database.
- `BusinessKey` - the human number people see, for example `PR-19`. Unique in the database.

## Package diagram

```
  dto          client                      handler                         config
  (unused)     LoggingRequestNotifier      RequestController (/api/v1)     Application, AppConfig
               DownstreamProperties        CreateRequestBody, RequestResponse,  RequestService
               (app.downstream from yaml)  ErrorResponse, ApiExceptionHandler   (@Service, @Transactional)
       \            \                        /                                 |
        \            \                      /                            persistence
         \            \                    /                             RequestEntity, RequestJpa
          \            \                  /                                    |
                                   domain
              RequestId  BusinessKey  RequestStatus  RequestPolicy  PurchaseRequest
              Rule + TransitionRule + OnlyApprovedCanCancelRule
              RequestNotifier (outbound port)  DuplicateRequest  RequestNotFound
              (no Spring, no ResponseEntity, no jakarta.validation, no jakarta.persistence)
```

Arrows point inward. `domain` never imports `org.springframework`, `java.sql`, `jakarta.persistence` or `jakarta.validation`.
HTTP is an adapter in `handler`: the controller picks the status code, the service does the work, Jackson never sees the entity.
The entity is the row, not the domain: `RequestEntity` lives in `persistence` and never leaves it.

## Statuses
- `DRAFT`
- `APPROVED`
- `ORDERED`
- `REJECTED`
- `CANCELLED`

The same list is the `CHECK` constraint in `src/main/resources/db/migration/V1__create_purchase_request.sql`.

## Status table
| From | To | Allowed |
| --- | --- | --- |
| DRAFT | APPROVED | Allowed |
| DRAFT | REJECTED | Allowed |
| APPROVED | ORDERED | Allowed |
| APPROVED | CANCELLED | Allowed |
| DRAFT | ORDERED | Forbidden |
| DRAFT | CANCELLED | Forbidden |
| REJECTED | CANCELLED | Forbidden |

## Rules (behind the `Rule` interface)
- **TransitionRule** - implements the status table: only the listed transitions are allowed.
- **OnlyApprovedCanCancelRule** - stop-factor: only a request currently `APPROVED` can move to `CANCELLED`.

## REST API (Lab 4)

| Call | Success | Failures |
| --- | --- | --- |
| `POST /api/v1/purchase-requests` | **201** + `Location: /api/v1/purchase-requests/{id}` | 400 `VALIDATION`, 409 `DUPLICATE`, 415 `UNSUPPORTED` |
| `GET /api/v1/purchase-requests/{id}` | **200** | 400 `VALIDATION` (`id must be a UUID`), 404 `NOT_FOUND` |

The path id is the UUID. The business key (`PR-19`) stays in the body.

```bash
curl -i -X POST http://localhost:8080/api/v1/purchase-requests \
  -H 'Content-Type: application/json' \
  -d '{"businessKey":"PR-19","title":"Laptops for the lab"}'
```

Every failure body is only `code`, `message`, `correlationId`. If the caller sends `X-Correlation-Id`, the body repeats it; otherwise a new one is minted. No stack trace, no exception class, no entity on the wire.

A second POST of the same business key is **409** and the row count for that key stays **1** (the Lab 3 duplicate, now visible to the caller).

OpenAPI: `/swagger-ui.html` (UI) and `/v3/api-docs` (spec).

## Configuration and profiles

| File | Holds |
| --- | --- |
| `application.yaml` | shared: `ddl-auto: validate`, Flyway, error-leak settings, actuator exposure. No password. `spring.profiles.default: dev` |
| `application-dev.yaml` | laptop: PostgreSQL `localhost:5432/css`, `css`/`css`; `app.downstream.base-url: http://localhost:9090`, `timeout: 2s` |
| `application-prod.yaml` | env vars only: `CSS_DB_URL`, `CSS_DB_USER`, `CSS_DB_PASSWORD`, `CSS_DOWNSTREAM_URL` |

`app.downstream` is bound by `@ConfigurationProperties` (`DownstreamProperties`) and injected into the outbound adapter. No network call yet (Lab 6). Java has no URL literals.

Production: `SPRING_PROFILES_ACTIVE=prod` plus the env vars.

## Actuator

Only `health` and `info` are exposed. `GET /actuator/health` is 200 `UP` without details; `env`, `beans`, `heapdump`, `shutdown` are 404.

## Database (Lab 3)

One table, `purchase_request`, written by hand in the Flyway migration
`src/main/resources/db/migration/V1__create_purchase_request.sql`:
surrogate `id uuid`, `business_key text NOT NULL UNIQUE`, `status text` with a `CHECK`.

- **Flyway** runs `V1` once on startup and records it in `flyway_schema_history`.
- **Hibernate** maps `RequestEntity` to that table with `ddl-auto: validate`: it checks the table, it never creates or changes it.
- A new column is a new file (`V2__...sql`). `V1` is never edited after it ran.

Local URL: `jdbc:postgresql://localhost:5432/css`, user `css`, password `css` (classroom default).

Start from an empty database (connected to `postgres`, not to `css`):

```sql
DROP DATABASE IF EXISTS css WITH (FORCE);
CREATE DATABASE css OWNER css;
```

Then `mvn -q verify`: Flyway creates the table, the tests clean rows with `DELETE FROM`, not `DROP TABLE`.

### Rollback by hand (psql)

```sql
BEGIN;
INSERT INTO purchase_request (id, business_key, status, title)
VALUES ('11111111-1111-1111-1111-111111111111', 'PR-19', 'DRAFT', 'Laptops');
INSERT INTO purchase_request (id, business_key, status, title)
VALUES ('22222222-2222-2222-2222-222222222222', 'PR-19', 'DRAFT', 'Laptops again');
-- ERROR: duplicate key value violates unique constraint
ROLLBACK;
SELECT count(*) FROM purchase_request WHERE business_key = 'PR-19';
-- 0
```

### Two transaction tests
- `secondStatementRollsBack` - `insertTwice("PR-19")` is one transaction; the second INSERT fails, everything rolls back, count is **0**.
- `secondRequestKeepsTheFirst` - two `register` calls, one transaction each; the first commits, the second throws `DuplicateRequest`, count is **1**.

## Run

```
mvn spring-boot:run
```

## Test

PostgreSQL must be running.

```
mvn -q verify
```
