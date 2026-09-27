-- Admin-initiated screening cancellation settles every confirmed booking with a full refund.
ALTER TABLE refund DROP CONSTRAINT refund_reason_check;
ALTER TABLE refund ADD CONSTRAINT refund_reason_check
    CHECK (reason IN ('CANCELLATION', 'LATE_PAYMENT', 'SHOW_CANCELLED'));

-- Drains the confirmed bookings of a cancelled screening in bounded batches.
CREATE INDEX idx_booking_screening_state ON booking(screening_id, state, id);
