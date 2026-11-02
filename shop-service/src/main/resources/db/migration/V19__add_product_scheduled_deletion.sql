-- Migration V19: Add scheduled deletion and auto-refund fields to products table
ALTER TABLE products
ADD COLUMN IF NOT EXISTS is_deleting BOOLEAN NOT NULL DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS deletion_scheduled_at TIMESTAMP WITH TIME ZONE NULL,
ADD COLUMN IF NOT EXISTS deletion_notice TEXT NULL;

CREATE INDEX IF NOT EXISTS idx_products_is_deleting ON products(is_deleting);
CREATE INDEX IF NOT EXISTS idx_products_deletion_scheduled_at ON products(deletion_scheduled_at);

