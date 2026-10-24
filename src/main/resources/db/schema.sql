-- Lab 3: one table for a purchase request, written by hand.
-- id           = surrogate key, the UUID from RequestId.newId()
-- business_key = the human number people see (PR-19)
-- status       = text + CHECK; the list must match RequestStatus and the README

CREATE TABLE purchase_request (
  id            uuid PRIMARY KEY,
  business_key  text NOT NULL UNIQUE,
  status        text NOT NULL,
  title         text NOT NULL,
  created_at    timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT purchase_request_status_known
    CHECK (status IN ('DRAFT', 'APPROVED', 'ORDERED', 'REJECTED', 'CANCELLED'))
);
