CREATE TABLE seasons (
    id UUID PRIMARY KEY, name VARCHAR(100) NOT NULL,
    start_at TIMESTAMP WITH TIME ZONE NOT NULL,
    end_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL, reset_percent INTEGER NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CHECK (end_at > start_at AND reset_percent BETWEEN 0 AND 100)
);
CREATE TABLE player_elo (
    id UUID PRIMARY KEY, season_id UUID NOT NULL REFERENCES seasons(id),
    account_id UUID NOT NULL, current_elo INTEGER NOT NULL, peak_elo INTEGER NOT NULL,
    tier VARCHAR(20) NOT NULL, win_streak INTEGER NOT NULL DEFAULT 0,
    loss_streak INTEGER NOT NULL DEFAULT 0, matches_played INTEGER NOT NULL DEFAULT 0,
    last_match_at TIMESTAMP WITH TIME ZONE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CHECK (current_elo >= 0 AND peak_elo >= current_elo),
    UNIQUE (season_id, account_id)
);
CREATE INDEX idx_player_elo_board ON player_elo(season_id, current_elo, account_id);
CREATE TABLE elo_history (
    id UUID PRIMARY KEY, account_id UUID NOT NULL, match_id UUID NOT NULL,
    season_id UUID NOT NULL REFERENCES seasons(id), place INTEGER NOT NULL,
    delta INTEGER NOT NULL, elo_after INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (account_id, match_id)
);
CREATE INDEX idx_elo_history_match ON elo_history(match_id);
CREATE TABLE rank_reward_credits (
    id UUID PRIMARY KEY, account_id UUID NOT NULL, match_id UUID NOT NULL,
    place INTEGER NOT NULL, amount BIGINT NOT NULL CHECK (amount > 0),
    idempotency_key VARCHAR(120) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    credited_at TIMESTAMP WITH TIME ZONE,
    attempts INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_rank_reward_pending ON rank_reward_credits(credited_at, created_at);
