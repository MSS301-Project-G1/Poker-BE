-- ============================================================================
-- V4: Add Game Assets (preview_url, metadata JSON, price_vip_coin) and Coin Bundles
-- ============================================================================

-- 1. Insert new categories
INSERT INTO categories (id, code, name, created_at) VALUES
    ('a0000000-0000-0000-0000-000000000009', 'CARD_FACE', 'Mặt trước lá bài', CURRENT_TIMESTAMP),
    ('a0000000-0000-0000-0000-000000000010', 'ALL_IN_EFFECT', 'Hiệu ứng All-in', CURRENT_TIMESTAMP)
ON CONFLICT (code) DO NOTHING;

-- 2. Alter products table to support Game Assets & VIP Coin pricing
ALTER TABLE products ADD COLUMN IF NOT EXISTS preview_url VARCHAR(500);
ALTER TABLE products ADD COLUMN IF NOT EXISTS metadata JSON;
ALTER TABLE products ADD COLUMN IF NOT EXISTS price_vip_coin BIGINT NOT NULL DEFAULT 0;

-- 3. Table: coin_bundles (VIP Coin packages per Riot Games model)
CREATE TABLE IF NOT EXISTS coin_bundles (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    price_vnd BIGINT NOT NULL CHECK (price_vnd > 0),
    base_vip_coin BIGINT NOT NULL CHECK (base_vip_coin > 0),
    bonus_vip_coin BIGINT NOT NULL DEFAULT 0 CHECK (bonus_vip_coin >= 0),
    bonus_percentage INT NOT NULL DEFAULT 0,
    badge_label VARCHAR(50),
    icon_url VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_bundles_is_active_sort ON coin_bundles(is_active, sort_order);

-- 4. Alter orders table to support both Product Purchase and Bundle Top-up
ALTER TABLE orders ALTER COLUMN product_id DROP NOT NULL;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS bundle_id UUID REFERENCES coin_bundles(id);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS vip_coin_amount BIGINT;
