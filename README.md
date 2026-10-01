# Purchase Request System - Lab 2

## Product
We build a purchase request tracking system. People track a purchase request (`RequestId`) as it moves through statuses on its way to approval or cancellation.

## Core item
`RequestId` - a unique identifier for a purchase request.

## Package diagram

dto client handler config
(JSON later) (HTTP later) (HTTP week 9) Application
RequestService (@Service)
\ \ / |
\ \ / injects Rule
\ \ /
domain
RequestId RequestStatus
Rule + TransitionRule
+ OnlyApprovedCanCancelRule
(no Spring)


Arrows point inward. `domain` never imports `org.springframework`.

## Statuses
- `DRAFT`
- `APPROVED`
- `REJECTED`
- `CANCELLED`

## Rules (behind the `Rule` interface)
- **TransitionRule** - forbids moving directly from `DRAFT` to `CANCELLED`.
- **OnlyApprovedCanCancelRule** - only a request currently `APPROVED` can move to `CANCELLED`.

## Run

mvn spring-boot:run


## Test

mvn -q verify