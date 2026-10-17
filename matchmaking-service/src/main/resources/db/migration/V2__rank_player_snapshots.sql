ALTER TABLE match_proposal_players ADD COLUMN win_streak_snapshot INTEGER;
ALTER TABLE match_proposal_players ADD COLUMN loss_streak_snapshot INTEGER;

ALTER TABLE match_proposal_players
    ADD CONSTRAINT ck_match_proposal_players_win_streak_nonnegative
        CHECK (win_streak_snapshot IS NULL OR win_streak_snapshot >= 0);

ALTER TABLE match_proposal_players
    ADD CONSTRAINT ck_match_proposal_players_loss_streak_nonnegative
        CHECK (loss_streak_snapshot IS NULL OR loss_streak_snapshot >= 0);

ALTER TABLE match_proposals ADD COLUMN redis_reconciled_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_match_proposals_redis_reconciliation
    ON match_proposals(status, redis_reconciled_at, updated_at);
