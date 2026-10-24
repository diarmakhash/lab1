# Purchase Request System - Lab 3

## Product
We build a purchase request tracking system. People track a purchase request as it moves through statuses on its way to ordering or cancellation.

- `RequestId` - surrogate id (UUID from `RequestId.newId()`), the primary key in the database.
- `BusinessKey` - the human number people see, for example `PR-19`. Unique in the database.

## Package diagram

```
  dto          client                 handler         config
  (JSON later) LoggingRequestNotifier (HTTP week 9)   Application, AppConfig
               (HTTP later)                           RequestService (@Service, @Transactional)
       \            \                    /                |
        \            \                  /           persistence
         \            \                /            RequestJdbc (JdbcTemplate, SQL)
          \            \              /                   |
                          domain
              RequestId  BusinessKey  RequestStatus  RequestPolicy
              Rule + TransitionRule + OnlyApprovedCanCancelRule
              RequestNotifier (outbound port)  DuplicateRequest
                     (no Spring, no java.sql)
```

Arrows point inward. `domain` never imports `org.springframework` or `java.sql`.

## Statuses
- `DRAFT`
- `APPROVED`
- `ORDERED`
- `REJECTED`
- `CANCELLED`

The same list is the `CHECK` constraint in `src/main/resources/db/schema.sql`.

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

## Database (Lab 3)

One table, `purchase_request`, written by hand in `src/main/resources/db/schema.sql`:
surrogate `id uuid`, `business_key text NOT NULL UNIQUE`, `status text` with a `CHECK`.

Start PostgreSQL (local install or Docker with only Postgres):

```
docker compose up -d
```

Local URL: `jdbc:postgresql://localhost:5432/css`, user `css`, password `css` (classroom default).

The tests apply `schema.sql` themselves on a clean database.

### Rollback by hand (psql)

```sql
-- psql -h localhost -U css css -f src/main/resources/db/schema.sql
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
