CREATE TABLE city (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    country VARCHAR(120) NOT NULL,
    time_zone VARCHAR(80) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_city_name_country UNIQUE (name, country)
);

CREATE TABLE theater (
    id UUID PRIMARY KEY,
    city_id UUID NOT NULL REFERENCES city(id) ON DELETE RESTRICT,
    name VARCHAR(160) NOT NULL,
    address VARCHAR(500) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_theater_city_name UNIQUE (city_id, name)
);

CREATE INDEX idx_theater_city ON theater(city_id);

CREATE TABLE auditorium (
    id UUID PRIMARY KEY,
    theater_id UUID NOT NULL REFERENCES theater(id) ON DELETE RESTRICT,
    name VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_auditorium_theater_name UNIQUE (theater_id, name)
);

CREATE INDEX idx_auditorium_theater ON auditorium(theater_id);

CREATE TABLE seat (
    id UUID PRIMARY KEY,
    auditorium_id UUID NOT NULL REFERENCES auditorium(id) ON DELETE RESTRICT,
    row_label VARCHAR(10) NOT NULL,
    seat_number INTEGER NOT NULL,
    category VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_seat_position UNIQUE (auditorium_id, row_label, seat_number),
    CONSTRAINT ck_seat_number_positive CHECK (seat_number > 0),
    CONSTRAINT ck_seat_category CHECK (category IN ('REGULAR', 'PREMIUM'))
);

CREATE INDEX idx_seat_auditorium ON seat(auditorium_id);

CREATE TABLE movie (
    id UUID PRIMARY KEY,
    title VARCHAR(240) NOT NULL,
    duration_minutes INTEGER NOT NULL,
    language VARCHAR(80) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_movie_duration_positive CHECK (duration_minutes > 0)
);

CREATE INDEX idx_movie_title_id ON movie(title, id);
