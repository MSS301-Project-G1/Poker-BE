-- ============================================================================
-- V7: Add index for bundle orders to accelerate idempotency queries
-- ============================================================================

CREATE INDEX IF NOT EXISTS idx_orders_account_bundle_status 
    ON orders(account_id, bundle_id, status);
