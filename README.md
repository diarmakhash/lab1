# Purchase Request System - Lab 2

## Product
We build a purchase request tracking system. People track a purchase request (`RequestId`) as it moves through statuses on its way to ordering or cancellation.

`RequestId` - a unique identifier for a purchase request.

## Package diagram

```
  dto          client        handler         config
  (JSON later) (HTTP later)  (HTTP week 9)   Application
                                             RequestService (@Service)
       \            \            /                |
        \            \          /           injects Rule
         \            \        /
                     domain
              RequestId  RequestStatus  RequestPolicy
              Rule + TransitionRule
              + OnlyApprovedCanCancelRule
                     (no Spring)
```

Arrows point inward. `domain` never imports `org.springframework`.

## Statuses
- `DRAFT`
- `APPROVED`
- `ORDERED`
- `REJECTED`
- `CANCELLED`

## Status table
| From | To | Allowed |
| --- | --- | --- |
| DRAFT | APPROVED | Allowed |
| APPROVED | ORDERED | Allowed |
| APPROVED | CANCELLED | Allowed |
| DRAFT | ORDERED | Forbidden |
| DRAFT | CANCELLED | Forbidden |

## Rules (behind the `Rule` interface)
- **TransitionRule** - implements the status table: only the listed transitions are allowed.
- **OnlyApprovedCanCancelRule** - stop-factor: only a request currently `APPROVED` can move to `CANCELLED`.

## Run

```
mvn spring-boot:run
```

## Test

```
mvn -q verify
```