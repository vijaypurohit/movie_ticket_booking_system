ALTER TABLE booking ADD COLUMN cancellation_idempotency_key VARCHAR(120);
ALTER TABLE booking ADD CONSTRAINT uq_booking_customer_cancellation_key
    UNIQUE (customer_id, cancellation_idempotency_key);
