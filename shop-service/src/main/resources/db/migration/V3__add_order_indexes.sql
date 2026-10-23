-- ============================================================================
-- V3: Add indexes to orders table for Phase B PayOS queries
-- ============================================================================

CREATE INDEX IF NOT EXISTS idx_orders_account_product_status 
    ON orders(account_id, product_id, status);

CREATE INDEX IF NOT EXISTS idx_orders_status_expires_at 
    ON orders(status, expires_at);
