CREATE TABLE seat_reservation (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    screening_id UUID NOT NULL REFERENCES screening(id) ON DELETE RESTRICT,
    state VARCHAR(30) NOT NULL CHECK (state IN ('ACTIVE', 'RELEASED', 'EXPIRED', 'PAYMENT_IN_PROGRESS', 'CONVERTED')),
    expires_at TIMESTAMPTZ NOT NULL,
    checkout_expires_at TIMESTAMPTZ,
    idempotency_key VARCHAR(120) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_reservation_customer_key UNIQUE (customer_id, idempotency_key)
);

CREATE INDEX idx_reservation_customer_created ON seat_reservation(customer_id, created_at, id);
CREATE INDEX idx_reservation_active_expiry ON seat_reservation(expires_at, id) WHERE state = 'ACTIVE';

ALTER TABLE screening_seat
    ADD COLUMN reservation_id UUID REFERENCES seat_reservation(id) ON DELETE SET NULL;

CREATE INDEX idx_screening_seat_reservation ON screening_seat(reservation_id) WHERE reservation_id IS NOT NULL;
