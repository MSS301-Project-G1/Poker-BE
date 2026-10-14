-- ============================================================================
-- V6: Create Payment Configs Schema & Seed Default Providers
-- ============================================================================

CREATE TABLE payment_configs (
    id UUID PRIMARY KEY,
    provider VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    bank_code VARCHAR(20),
    account_number VARCHAR(50),
    account_holder VARCHAR(100),
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    is_main BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Seed default Main provider: PayOS
INSERT INTO payment_configs (id, provider, name, bank_code, account_number, account_holder, is_active, is_main, created_at, updated_at)
VALUES (
    'b0000000-0000-0000-0000-000000000001',
    'PAYOS',
    'Cổng thanh toán PayOS (Mặc định)',
    NULL,
    NULL,
    NULL,
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);
