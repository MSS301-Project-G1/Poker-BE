CREATE TABLE match_proposals (
    id UUID PRIMARY KEY,
    mode VARCHAR(20) NOT NULL,
    status VARCHAR(40) NOT NULL,
    entry_fee BIGINT NOT NULL DEFAULT 0,
    elo_min INTEGER,
    elo_max INTEGER,
    accept_deadline TIMESTAMP WITH TIME ZONE,
    table_id UUID,
    match_id UUID,
    failure_code VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (mode IN ('RANK', 'NORMAL')),
    CHECK (entry_fee >= 0),
    CHECK (elo_min IS NULL OR elo_max IS NULL OR elo_min <= elo_max)
);

CREATE INDEX idx_match_proposals_status_deadline
    ON match_proposals(status, accept_deadline);

CREATE TABLE match_proposal_players (
    id UUID PRIMARY KEY,
    proposal_id UUID NOT NULL REFERENCES match_proposals(id),
    account_id UUID NOT NULL,
    elo_snapshot INTEGER,
    queue_joined_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(40) NOT NULL,
    accepted_at TIMESTAMP WITH TIME ZONE,
    fee_charged BOOLEAN NOT NULL DEFAULT FALSE,
    fee_refunded BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (proposal_id, account_id),
    CHECK (elo_snapshot IS NULL OR elo_snapshot >= 0)
);

CREATE INDEX idx_match_proposal_players_account
    ON match_proposal_players(account_id, status);

CREATE TABLE wallet_operations (
    id UUID PRIMARY KEY,
    proposal_id UUID NOT NULL REFERENCES match_proposals(id),
    account_id UUID NOT NULL,
    operation_type VARCHAR(20) NOT NULL,
    amount BIGINT NOT NULL,
    reason VARCHAR(40) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL UNIQUE,
    status VARCHAR(30) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(500),
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (operation_type IN ('DEBIT', 'REFUND')),
    CHECK (amount > 0),
    CHECK (attempts >= 0)
);

CREATE INDEX idx_wallet_operations_pending
    ON wallet_operations(status, created_at);

CREATE INDEX idx_wallet_operations_proposal
    ON wallet_operations(proposal_id, account_id);

CREATE TABLE matchmaking_outbox (
    id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    body TEXT NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(500),
    CHECK (attempts >= 0)
);

CREATE INDEX idx_matchmaking_outbox_pending
    ON matchmaking_outbox(published_at, occurred_at);
