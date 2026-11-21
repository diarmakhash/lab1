# Purchase Request System - Lab 5 (Spring Security + JWT + auth page)

## Product
We build a purchase request tracking system. People track a purchase request as it moves through statuses on its way to ordering or cancellation.

- `RequestId` - surrogate id (UUID from `RequestId.newId()`), the primary key in the database.
- `BusinessKey` - the human number people see, for example `PR-19`. Unique in the database.

## Package diagram

```
  security                     client                      handler                         config
  SecurityConfig (chain,       LoggingRequestNotifier      RequestController (/api/v1)     Application, AppConfig
   JWT beans, dev users)       DownstreamProperties        AuthController, MeController    RequestService
  TokenService (issues JWT)    (app.downstream from yaml)  CreateRequestBody, LoginRequest  (@Service, @Transactional,
  JwtProperties (app.jwt)                                  RequestResponse, TokenResponse    @PreAuthorize on approve)
       \                             \                     ErrorResponse, ApiExceptionHandler      |
        \                             \                    SecurityErrorHandlers (401/403)   persistence
         \                             \                        /                      RequestEntity, RequestJpa
          \                             \                      /                               |
                                             domain
              RequestId  BusinessKey  RequestStatus  RequestPolicy  PurchaseRequest
              Rule + TransitionRule + OnlyApprovedCanCancelRule
              RequestNotifier (outbound port)  DuplicateRequest  RequestNotFound  TransitionForbidden
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

## Security (Lab 5)

Two roles. The locked action is **approve** (`DRAFT -> APPROVED`), the expensive move from the status table.

- **REQUESTER** (junior) creates and reads purchase requests.
- **APPROVER** (senior) does the same and may also approve.

### Rules table

The filter chain in `SecurityConfig` is this table, top to bottom. The first match wins, so the narrow approve rule sits above `/api/v1/**`.

| Action | REQUESTER | APPROVER | No token |
| --- | --- | --- | --- |
| `POST /api/v1/auth/login` | anyone | anyone | allowed |
| `GET /actuator/health`, `GET /actuator/info` | anyone | anyone | allowed |
| Auth page: `/`, `/index.html`, `/app.css`, `/app.js` | anyone | anyone | allowed |
| springdoc: `/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**` | anyone | anyone | allowed |
| Approve: `POST /api/v1/purchase-requests/{id}/approve` | **403** | yes (200) | 401 |
| `GET /api/me` | yes | yes | 401 |
| Create and read: `POST /api/v1/purchase-requests`, `GET /api/v1/purchase-requests/{id}` | yes | yes | 401 |
| Anything else (also `/actuator/env`, `heapdump`, `beans`) | 403 | 403 | 401 |

`RequestService.approve` also carries `@PreAuthorize("hasRole('APPROVER')")`: the same role as the URL rule. A REQUESTER never reaches the service, and the row stays `DRAFT`. The actor in the log is `Authentication.getName()` (the token's `sub`), never a JSON field.

### Login and the token

```bash
curl -i -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"daniyar","password":"approver-dev"}'
# 200 {"accessToken":"eyJ...","tokenType":"Bearer","expiresIn":900}

curl -i -X POST http://localhost:8080/api/v1/purchase-requests/<uuid>/approve \
  -H "Authorization: Bearer <accessToken>"
```

- HS256, `iss: purchases`, `sub`, `iat`, `exp` (15 minutes), and `roles` without the `ROLE_` prefix (`["APPROVER"]`). No password and no personal data in the token.
- The decoder checks the signature, the expiry and the issuer. A token from another issuer (`helpdesk`) is 401.
- The `roles` claim becomes `ROLE_<role>` for `hasRole(...)`.

| Failure | Status | `code` | `message` |
| --- | --- | --- | --- |
| Unknown user, or wrong password | 401 | `UNAUTHENTICATED` | `bad username or password` |
| Blank username or password | 400 | `VALIDATION` | `username must not be blank` |
| No token, bad format, bad signature, expired, wrong issuer | 401 | `UNAUTHENTICATED` | `valid token required` |
| Role not allowed | 403 | `FORBIDDEN` | `not allowed for your role` |
| Approve a request that is not `DRAFT` | 409 | `TRANSITION_FORBIDDEN` | `purchase request cannot move from APPROVED to APPROVED` |

401 and 403 come from the filters, so `SecurityErrorHandlers` writes the same `code`, `message`, `correlationId` body as the advice. CSRF is off, sessions are `STATELESS`, and there is no form login or HTTP Basic: the only credential is `Authorization: Bearer`.

### Dev users and the secret

| Username | Password | Role |
| --- | --- | --- |
| `aigerim` | `requester-dev` | REQUESTER |
| `daniyar` | `approver-dev` | APPROVER |

These users exist only on the `dev` profile (`InMemoryUserDetailsManager`), stored as BCrypt hashes via `PasswordEncoderFactories.createDelegatingPasswordEncoder()`. There is no `{noop}` and no user table. Prod ships no users.

| Profile | `app.jwt.secret` |
| --- | --- |
| `application.yaml` | none (only `app.jwt.issuer: purchases`) |
| `application-dev.yaml` | classroom value, 32+ characters |
| `application-prod.yaml` | `${CSS_JWT_SECRET}`, no default |

A secret shorter than 32 characters fails at startup (`JwtProperties`). `src/main/java` has no secret literal.

### The auth page

`http://localhost:8080/` is our own page: `src/main/resources/static/index.html`, `app.css` and `app.js`, plain HTML and JS with no library. It is served by the same Spring Boot process, so no CORS bean is needed.

1. Shows the product, the two roles and the two dev users (labelled dev-only).
2. Signs in with `POST /api/v1/auth/login`. A wrong password shows `401 UNAUTHENTICATED bad username or password`.
3. Keeps `accessToken` only in a JavaScript variable: not in `localStorage`, not in the URL, not on the page. It shows the user and role from `GET /api/me` and a 15-minute countdown.
4. Create and Read call the API with `Authorization: Bearer` and show the HTTP status and the status token.
5. Approve as `aigerim` shows **403 FORBIDDEN**, then re-reads the row: "status is still DRAFT". Approve as `daniyar` shows **200** and `APPROVED`.
6. Log out drops the variable. The next Read shows **401** `valid token required`.
7. Every call sends `X-Correlation-Id`. Errors show `code`, `message` and `correlationId`.

## REST API (Lab 4)

| Call | Success | Failures |
| --- | --- | --- |
| `POST /api/v1/purchase-requests` | **201** + `Location: /api/v1/purchase-requests/{id}` | 400 `VALIDATION`, 409 `DUPLICATE`, 415 `UNSUPPORTED` |
| `GET /api/v1/purchase-requests/{id}` | **200** | 400 `VALIDATION` (`id must be a UUID`), 404 `NOT_FOUND` |
| `POST /api/v1/purchase-requests/{id}/approve` (Lab 5) | **200** `APPROVED` | 401, 403, 404, 409 `TRANSITION_FORBIDDEN` |

Since Lab 5 every call here needs `Authorization: Bearer <token>`; without it the answer is 401.

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

Only `health` and `info` are exposed. `GET /actuator/health` is 200 `UP` without details. `env`, `beans`, `heapdump` and `shutdown` are not exposed (404 in Lab 4). Since Lab 5 the chain denies them first: 401 with no token, 403 with any token.

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
