CREATE TABLE registration_otps (
    account_id UUID PRIMARY KEY REFERENCES accounts(id),
    code_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    last_sent_at TIMESTAMPTZ NOT NULL,
    failed_attempts INTEGER NOT NULL DEFAULT 0 CHECK (failed_attempts >= 0)
);

-- Committed with email verification; delivery can resume after a broker outage.
CREATE TABLE account_registration_events (
    event_id UUID PRIMARY KEY,
    account_id UUID NOT NULL UNIQUE REFERENCES accounts(id),
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    delivery_attempts INTEGER NOT NULL DEFAULT 0 CHECK (delivery_attempts >= 0)
);

CREATE INDEX idx_registration_events_pending
    ON account_registration_events (occurred_at) WHERE published_at IS NULL;
