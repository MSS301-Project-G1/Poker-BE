-- ============================================================================
-- V2: Add VIP Coin balance to wallets and currency_type to transactions
-- Separates Playing Chips (coin_balance) from Cosmetics Currency (vip_coin_balance)
-- ============================================================================

ALTER TABLE wallets RENAME COLUMN balance TO coin_balance;
ALTER TABLE wallets ADD COLUMN IF NOT EXISTS vip_coin_balance BIGINT NOT NULL DEFAULT 0 CHECK (vip_coin_balance >= 0);

ALTER TABLE wallet_transactions ADD COLUMN IF NOT EXISTS currency_type VARCHAR(20) NOT NULL DEFAULT 'COIN';
