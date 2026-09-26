# Product: Purchase Management System

## Product
We build a purchase request tracking system. People track purchase requests.

## Core item
Purchase Request (`RequestId`, `RequestStatus`, `RequestPolicy`).

## Status table
| From | To | Allowed |
| --- | --- | --- |
| DRAFT | APPROVED | Allowed |
| APPROVED | ORDERED | Allowed |
| DRAFT | ORDERED | Forbidden |
| ORDERED | DRAFT | Forbidden |

## Forbidden — why
1. **DRAFT -> ORDERED:** Forbidden because a purchase request cannot be ordered without managerial approval (skipped approval).
2. **ORDERED -> DRAFT:** Forbidden because a completed purchase cannot be reopened or moved back to draft.