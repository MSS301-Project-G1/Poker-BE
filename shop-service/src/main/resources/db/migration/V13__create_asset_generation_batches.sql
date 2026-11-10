-- V13: Create Asset Generation Batches table for rollback, cleanup and product application
CREATE TABLE IF NOT EXISTS asset_generation_batches (
    id UUID PRIMARY KEY,
    category_code VARCHAR(50) NOT NULL,
    product_id UUID,
    model_config_id UUID REFERENCES ai_model_configs(id) ON DELETE SET NULL,
    raw_prompt TEXT NOT NULL,
    style_spec JSON NOT NULL,
    s3_zip_url VARCHAR(500) NOT NULL,
    s3_preview_url VARCHAR(500) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'GENERATED',
    created_by VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    applied_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_batches_status_expires
    ON asset_generation_batches (status, expires_at);

CREATE INDEX IF NOT EXISTS idx_batches_product
    ON asset_generation_batches (product_id)
    WHERE product_id IS NOT NULL;
