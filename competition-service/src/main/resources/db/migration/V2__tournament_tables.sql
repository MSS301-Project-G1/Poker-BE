CREATE TABLE tournaments (
    id UUID PRIMARY KEY, name VARCHAR(100) NOT NULL, type VARCHAR(20) NOT NULL,
    start_at TIMESTAMP WITH TIME ZONE NOT NULL, entry_fee BIGINT NOT NULL,
    slots INTEGER NOT NULL, status VARCHAR(20) NOT NULL,
    reward_coins BIGINT NOT NULL DEFAULT 0, reward_product_id UUID,
    created_by UUID NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CHECK (entry_fee >= 0 AND reward_coins >= 0 AND slots >= 2)
);
CREATE INDEX idx_tournaments_start ON tournaments(status, start_at);
CREATE TABLE tournament_entries (
    id UUID PRIMARY KEY, tournament_id UUID NOT NULL REFERENCES tournaments(id),
    account_id UUID NOT NULL, status VARCHAR(20) NOT NULL, final_place INTEGER,
    registered_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (tournament_id, account_id)
);
CREATE TABLE tournament_tables (
    id UUID PRIMARY KEY, tournament_id UUID NOT NULL REFERENCES tournaments(id),
    round VARCHAR(20) NOT NULL, table_index INTEGER NOT NULL, status VARCHAR(20) NOT NULL,
    table_id UUID, match_id UUID, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (tournament_id, round, table_index)
);
CREATE INDEX idx_tournament_tables_pending ON tournament_tables(status, created_at);
CREATE TABLE tournament_seats (
    id UUID PRIMARY KEY, tournament_table_id UUID NOT NULL REFERENCES tournament_tables(id),
    account_id UUID NOT NULL, seat_index INTEGER NOT NULL, place INTEGER,
    UNIQUE (tournament_table_id, account_id), UNIQUE (tournament_table_id, seat_index)
);
CREATE TABLE wallet_credits (
    id UUID PRIMARY KEY, account_id UUID NOT NULL, amount BIGINT NOT NULL CHECK (amount > 0),
    reason VARCHAR(50) NOT NULL, ref_id VARCHAR(100),
    idempotency_key VARCHAR(160) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    credited_at TIMESTAMP WITH TIME ZONE,
    attempts INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_wallet_credits_pending ON wallet_credits(credited_at, created_at);
