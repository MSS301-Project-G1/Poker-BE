-- Script khởi tạo database và schema cho wallet-service
-- Chạy tự động trong container PostgreSQL của wallet-service

CREATE TABLE IF NOT EXISTS wallets (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL UNIQUE,
    coin_balance BIGINT NOT NULL DEFAULT 0 CHECK (coin_balance >= 0),
    vip_coin_balance BIGINT NOT NULL DEFAULT 0 CHECK (vip_coin_balance >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS wallet_transactions (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    amount BIGINT NOT NULL,
    currency_type VARCHAR(20) NOT NULL DEFAULT 'COIN',
    reason VARCHAR(50) NOT NULL,
    ref_id VARCHAR(255),
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    balance_after BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_wallet_transactions_account FOREIGN KEY (account_id) REFERENCES wallets (account_id)
);

CREATE INDEX IF NOT EXISTS idx_wallets_account_id ON wallets (account_id);
CREATE INDEX IF NOT EXISTS idx_wallet_transactions_account_created ON wallet_transactions (account_id, created_at DESC);
