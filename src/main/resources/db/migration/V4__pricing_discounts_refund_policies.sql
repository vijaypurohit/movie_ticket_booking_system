CREATE TABLE pricing_plan (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL UNIQUE,
    regular_price NUMERIC(12,2) NOT NULL CHECK (regular_price > 0),
    premium_price NUMERIC(12,2) NOT NULL CHECK (premium_price > 0),
    weekend_adjustment NUMERIC(12,2) NOT NULL CHECK (weekend_adjustment >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE discount_code (
    id UUID PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    discount_type VARCHAR(20) NOT NULL CHECK (discount_type IN ('FIXED', 'PERCENTAGE')),
    discount_value NUMERIC(12,2) NOT NULL CHECK (discount_value > 0),
    valid_from TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ NOT NULL,
    minimum_spend NUMERIC(12,2) NOT NULL CHECK (minimum_spend >= 0),
    maximum_discount NUMERIC(12,2),
    global_usage_limit INTEGER,
    per_customer_usage_limit INTEGER,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_discount_validity CHECK (valid_until > valid_from),
    CONSTRAINT ck_discount_percentage CHECK (discount_type <> 'PERCENTAGE' OR discount_value <= 100),
    CONSTRAINT ck_discount_maximum CHECK (maximum_discount IS NULL OR maximum_discount > 0),
    CONSTRAINT ck_discount_global_limit CHECK (global_usage_limit IS NULL OR global_usage_limit > 0),
    CONSTRAINT ck_discount_customer_limit CHECK (per_customer_usage_limit IS NULL OR per_customer_usage_limit > 0)
);

CREATE TABLE refund_policy (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE refund_policy_rule (
    id UUID PRIMARY KEY,
    refund_policy_id UUID NOT NULL REFERENCES refund_policy(id) ON DELETE CASCADE,
    cutoff_minutes BIGINT NOT NULL CHECK (cutoff_minutes >= 0),
    refund_percentage NUMERIC(5,2) NOT NULL CHECK (refund_percentage >= 0 AND refund_percentage <= 100),
    CONSTRAINT uq_refund_policy_cutoff UNIQUE (refund_policy_id, cutoff_minutes)
);

CREATE INDEX idx_refund_policy_rule_order
    ON refund_policy_rule(refund_policy_id, cutoff_minutes DESC);
