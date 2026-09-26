CREATE TABLE screening (
    id UUID PRIMARY KEY,
    movie_id UUID NOT NULL REFERENCES movie(id) ON DELETE RESTRICT,
    auditorium_id UUID NOT NULL REFERENCES auditorium(id) ON DELETE RESTRICT,
    pricing_plan_id UUID NOT NULL REFERENCES pricing_plan(id) ON DELETE RESTRICT,
    refund_policy_id UUID NOT NULL REFERENCES refund_policy(id) ON DELETE RESTRICT,
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_screening_interval CHECK (end_time > start_time)
);

CREATE INDEX idx_screening_auditorium_time
    ON screening(auditorium_id, start_time, end_time) WHERE status = 'ACTIVE';
CREATE INDEX idx_screening_movie_time ON screening(movie_id, start_time, id);

CREATE TABLE screening_price (
    id UUID PRIMARY KEY,
    screening_id UUID NOT NULL REFERENCES screening(id) ON DELETE CASCADE,
    seat_category VARCHAR(20) NOT NULL CHECK (seat_category IN ('REGULAR', 'PREMIUM')),
    amount NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL CHECK (currency = 'INR'),
    CONSTRAINT uq_screening_price_category UNIQUE (screening_id, seat_category)
);

CREATE TABLE screening_seat (
    id UUID PRIMARY KEY,
    screening_id UUID NOT NULL REFERENCES screening(id) ON DELETE CASCADE,
    seat_id UUID NOT NULL REFERENCES seat(id) ON DELETE RESTRICT,
    state VARCHAR(30) NOT NULL CHECK (state IN ('AVAILABLE', 'RESERVED', 'PAYMENT_IN_PROGRESS', 'BOOKED')),
    CONSTRAINT uq_screening_physical_seat UNIQUE (screening_id, seat_id)
);

CREATE INDEX idx_screening_seat_screening_state ON screening_seat(screening_id, state, id);
