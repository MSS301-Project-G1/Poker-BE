CREATE TABLE match_settings (
    id UUID PRIMARY KEY, mode VARCHAR(20) NOT NULL UNIQUE,
    small_blind BIGINT NOT NULL, big_blind BIGINT NOT NULL,
    starting_chips BIGINT NOT NULL, turn_time_seconds INTEGER NOT NULL,
    min_players INTEGER NOT NULL, max_players INTEGER NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CHECK (small_blind > 0 AND big_blind > small_blind AND starting_chips >= big_blind),
    CHECK (min_players >= 2 AND max_players >= min_players AND max_players <= 10)
);
CREATE TABLE matches (
    id UUID PRIMARY KEY, table_id UUID NOT NULL UNIQUE,
    mode VARCHAR(20) NOT NULL, source_id UUID NOT NULL,
    entry_fee BIGINT NOT NULL CHECK (entry_fee >= 0), request_fingerprint TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (mode, source_id)
);
CREATE TABLE match_players (
    id UUID PRIMARY KEY, match_id UUID NOT NULL REFERENCES matches(id),
    account_id UUID NOT NULL, seat_index INTEGER NOT NULL,
    place INTEGER NOT NULL DEFAULT 0, final_chips BIGINT NOT NULL DEFAULT 0,
    left_early BOOLEAN NOT NULL DEFAULT FALSE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (match_id, account_id), UNIQUE (match_id, seat_index)
);
CREATE INDEX idx_match_players_account ON match_players(account_id, id);
CREATE TABLE game_outbox (
    id UUID PRIMARY KEY, event_type VARCHAR(64) NOT NULL, body TEXT NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_game_outbox_pending ON game_outbox(published_at, occurred_at);
