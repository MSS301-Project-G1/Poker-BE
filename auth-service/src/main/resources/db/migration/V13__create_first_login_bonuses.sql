-- FIRST_LOGIN_BONUS (SPEC 7.1): queued in the transaction that activates a new player account, then
-- credited to the wallet by a job. Accounts activated before this table existed never receive one.
CREATE TABLE first_login_bonuses (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id),
    reward_coins BIGINT NOT NULL CHECK (reward_coins > 0),
    reward_status VARCHAR(16) NOT NULL CHECK (reward_status IN ('PENDING', 'REWARDED')),
    created_at TIMESTAMPTZ NOT NULL,
    rewarded_at TIMESTAMPTZ,
    wallet_transaction_id UUID,
    delivery_attempts INTEGER NOT NULL DEFAULT 0 CHECK (delivery_attempts >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_first_login_bonuses_account UNIQUE (account_id),
    CONSTRAINT ck_first_login_bonuses_reward_state CHECK (
        (reward_status = 'PENDING' AND rewarded_at IS NULL AND wallet_transaction_id IS NULL)
        OR (reward_status = 'REWARDED' AND rewarded_at IS NOT NULL AND wallet_transaction_id IS NOT NULL)
    )
);
CREATE INDEX idx_first_login_bonuses_pending ON first_login_bonuses (next_attempt_at, id) WHERE reward_status = 'PENDING';
