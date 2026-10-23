CREATE TABLE daily_checkins (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id),
    checkin_date DATE NOT NULL,
    reward_coins BIGINT NOT NULL CHECK (reward_coins > 0),
    reward_status VARCHAR(16) NOT NULL CHECK (reward_status IN ('PENDING', 'REWARDED')),
    checked_in_at TIMESTAMPTZ NOT NULL,
    rewarded_at TIMESTAMPTZ,
    wallet_transaction_id UUID,
    delivery_attempts INTEGER NOT NULL DEFAULT 0 CHECK (delivery_attempts >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_daily_checkins_account_date UNIQUE (account_id, checkin_date),
    CONSTRAINT ck_daily_checkins_reward_state CHECK (
        (reward_status = 'PENDING' AND rewarded_at IS NULL AND wallet_transaction_id IS NULL)
        OR (reward_status = 'REWARDED' AND rewarded_at IS NOT NULL AND wallet_transaction_id IS NOT NULL)
    )
);
CREATE INDEX idx_daily_checkins_pending ON daily_checkins (next_attempt_at, id) WHERE reward_status = 'PENDING';
