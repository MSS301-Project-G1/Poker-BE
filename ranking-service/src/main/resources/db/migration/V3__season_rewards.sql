-- Tier snapshot taken when the season closes; CARD_GOD depends on the final leaderboard.
ALTER TABLE player_elo ADD COLUMN final_tier VARCHAR(20);
CREATE TABLE season_tier_rewards (
    id UUID PRIMARY KEY, season_id UUID NOT NULL REFERENCES seasons(id),
    tier VARCHAR(20) NOT NULL, reward_type VARCHAR(20) NOT NULL,
    amount BIGINT NOT NULL DEFAULT 0, product_id UUID,
    CHECK ((reward_type = 'COIN' AND amount > 0 AND product_id IS NULL)
        OR (reward_type = 'PRODUCT' AND product_id IS NOT NULL))
);
CREATE INDEX idx_season_tier_rewards_season ON season_tier_rewards(season_id);
CREATE TABLE season_reward_claims (
    id UUID PRIMARY KEY, season_id UUID NOT NULL REFERENCES seasons(id),
    account_id UUID NOT NULL, tier VARCHAR(20) NOT NULL,
    claimed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (season_id, account_id)
);
CREATE TABLE reward_grants (
    id UUID PRIMARY KEY, account_id UUID NOT NULL, reward_type VARCHAR(20) NOT NULL,
    amount BIGINT NOT NULL DEFAULT 0, product_id UUID, reason VARCHAR(50) NOT NULL,
    ref_id VARCHAR(100), idempotency_key VARCHAR(200) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    granted_at TIMESTAMP WITH TIME ZONE,
    attempts INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_reward_grants_pending ON reward_grants(granted_at, created_at);
CREATE TABLE ranking_outbox (
    id UUID PRIMARY KEY, event_type VARCHAR(64) NOT NULL, body TEXT NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_ranking_outbox_pending ON ranking_outbox(published_at, occurred_at);
-- Proposed coin rewards for the seeded season (SPEC §2.3 cumulative); admins can change them.
INSERT INTO season_tier_rewards (id, season_id, tier, reward_type, amount) VALUES
    ('0199e0a0-0000-7000-8000-000000000101', '0199e0a0-0000-7000-8000-000000000001', 'IRON', 'COIN', 50),
    ('0199e0a0-0000-7000-8000-000000000102', '0199e0a0-0000-7000-8000-000000000001', 'BRONZE', 'COIN', 100),
    ('0199e0a0-0000-7000-8000-000000000103', '0199e0a0-0000-7000-8000-000000000001', 'SILVER', 'COIN', 150),
    ('0199e0a0-0000-7000-8000-000000000104', '0199e0a0-0000-7000-8000-000000000001', 'GOLD', 'COIN', 200),
    ('0199e0a0-0000-7000-8000-000000000105', '0199e0a0-0000-7000-8000-000000000001', 'PLATINUM', 'COIN', 300),
    ('0199e0a0-0000-7000-8000-000000000106', '0199e0a0-0000-7000-8000-000000000001', 'DIAMOND', 'COIN', 400),
    ('0199e0a0-0000-7000-8000-000000000107', '0199e0a0-0000-7000-8000-000000000001', 'CHAMPION', 'COIN', 500),
    ('0199e0a0-0000-7000-8000-000000000108', '0199e0a0-0000-7000-8000-000000000001', 'GRAND_CHAMPION', 'COIN', 700),
    ('0199e0a0-0000-7000-8000-000000000109', '0199e0a0-0000-7000-8000-000000000001', 'CARD_GOD', 'COIN', 1000);
