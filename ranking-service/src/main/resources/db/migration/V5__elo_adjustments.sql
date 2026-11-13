-- Admin changes to a player's season Elo: who, when, from what, to what and why. Match results stay in elo_history.
CREATE TABLE elo_adjustments (
    id UUID PRIMARY KEY,
    season_id UUID NOT NULL REFERENCES seasons(id),
    account_id UUID NOT NULL,
    actor_id UUID NOT NULL,
    elo_before INTEGER NOT NULL CHECK (elo_before >= 0),
    elo_after INTEGER NOT NULL CHECK (elo_after >= 0),
    reason VARCHAR(500) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_elo_adjustments_account ON elo_adjustments(account_id, occurred_at, id);
