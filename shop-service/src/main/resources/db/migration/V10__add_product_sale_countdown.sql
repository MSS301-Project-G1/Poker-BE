-- V10: Add sale_expires_at to products for flash sales / exclusive countdown
ALTER TABLE products ADD COLUMN IF NOT EXISTS sale_expires_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_products_sale_expires_at
    ON products (sale_expires_at)
    WHERE sale_expires_at IS NOT NULL;
