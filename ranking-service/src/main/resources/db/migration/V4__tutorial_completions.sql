-- One row per account: the primary key makes the tutorial reward a one-time grant.
CREATE TABLE tutorial_completions (
    account_id UUID PRIMARY KEY,
    reward_coins BIGINT NOT NULL CHECK (reward_coins >= 0),
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
