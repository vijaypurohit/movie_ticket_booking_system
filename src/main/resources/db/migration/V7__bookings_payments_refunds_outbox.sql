CREATE TABLE booking (
    id UUID PRIMARY KEY,
    reference VARCHAR(24) NOT NULL UNIQUE,
    customer_id UUID NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    screening_id UUID NOT NULL REFERENCES screening(id) ON DELETE RESTRICT,
    reservation_id UUID NOT NULL REFERENCES seat_reservation(id) ON DELETE RESTRICT,
    discount_code_id UUID REFERENCES discount_code(id) ON DELETE RESTRICT,
    state VARCHAR(30) NOT NULL CHECK (state IN ('PENDING_PAYMENT', 'CONFIRMED', 'FAILED', 'CANCELLED')),
    subtotal NUMERIC(12,2) NOT NULL CHECK (subtotal >= 0),
    discount_amount NUMERIC(12,2) NOT NULL CHECK (discount_amount >= 0),
    total_amount NUMERIC(12,2) NOT NULL CHECK (total_amount >= 0),
    currency CHAR(3) NOT NULL CHECK (currency = 'INR'),
    idempotency_key VARCHAR(120) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    checkout_expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_booking_customer_key UNIQUE (customer_id, idempotency_key),
    CONSTRAINT uq_booking_reservation UNIQUE (reservation_id),
    CONSTRAINT ck_booking_total CHECK (total_amount = subtotal - discount_amount)
);

CREATE INDEX idx_booking_customer_history ON booking(customer_id, created_at DESC, id DESC);
CREATE INDEX idx_booking_pending_checkout ON booking(checkout_expires_at, id) WHERE state = 'PENDING_PAYMENT';

CREATE TABLE booking_item (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES booking(id) ON DELETE CASCADE,
    screening_seat_id UUID NOT NULL REFERENCES screening_seat(id) ON DELETE RESTRICT,
    row_label VARCHAR(10) NOT NULL,
    seat_number INTEGER NOT NULL CHECK (seat_number > 0),
    category VARCHAR(20) NOT NULL CHECK (category IN ('REGULAR', 'PREMIUM')),
    unit_price NUMERIC(12,2) NOT NULL CHECK (unit_price > 0),
    currency CHAR(3) NOT NULL CHECK (currency = 'INR'),
    CONSTRAINT uq_booking_item_seat UNIQUE (booking_id, screening_seat_id)
);

CREATE TABLE booking_refund_rule (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES booking(id) ON DELETE CASCADE,
    cutoff_minutes BIGINT NOT NULL CHECK (cutoff_minutes >= 0),
    refund_percentage NUMERIC(5,2) NOT NULL CHECK (refund_percentage BETWEEN 0 AND 100),
    CONSTRAINT uq_booking_refund_cutoff UNIQUE (booking_id, cutoff_minutes)
);

CREATE TABLE payment (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL UNIQUE REFERENCES booking(id) ON DELETE RESTRICT,
    status VARCHAR(20) NOT NULL CHECK (status IN ('INITIATED', 'SUCCEEDED', 'DECLINED')),
    amount NUMERIC(12,2) NOT NULL CHECK (amount >= 0),
    currency CHAR(3) NOT NULL CHECK (currency = 'INR'),
    gateway_idempotency_key VARCHAR(160) NOT NULL UNIQUE,
    provider_reference VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE discount_redemption (
    id UUID PRIMARY KEY,
    discount_code_id UUID NOT NULL REFERENCES discount_code(id) ON DELETE RESTRICT,
    customer_id UUID NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    booking_id UUID NOT NULL UNIQUE REFERENCES booking(id) ON DELETE RESTRICT,
    awarded_amount NUMERIC(12,2) NOT NULL CHECK (awarded_amount > 0),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_discount_redemption_global ON discount_redemption(discount_code_id);
CREATE INDEX idx_discount_redemption_customer ON discount_redemption(discount_code_id, customer_id);

CREATE TABLE refund (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES booking(id) ON DELETE RESTRICT,
    payment_id UUID NOT NULL REFERENCES payment(id) ON DELETE RESTRICT,
    reason VARCHAR(30) NOT NULL CHECK (reason IN ('CANCELLATION', 'LATE_PAYMENT')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED')),
    amount NUMERIC(12,2) NOT NULL CHECK (amount >= 0),
    currency CHAR(3) NOT NULL CHECK (currency = 'INR'),
    idempotency_key VARCHAR(160) NOT NULL UNIQUE,
    provider_reference VARCHAR(100),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    available_at TIMESTAMPTZ NOT NULL,
    processing_lease_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_refund_payment_reason UNIQUE (payment_id, reason)
);

CREATE INDEX idx_refund_pending_work ON refund(status, available_at, id);
CREATE INDEX idx_refund_booking ON refund(booking_id, created_at, id);

CREATE TABLE outbox_event (
    id UUID PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id UUID NOT NULL,
    business_key VARCHAR(180) NOT NULL UNIQUE,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'PROCESSING', 'DELIVERED', 'FAILED')),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    available_at TIMESTAMPTZ NOT NULL,
    processing_lease_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_outbox_pending_work ON outbox_event(status, available_at, id);
