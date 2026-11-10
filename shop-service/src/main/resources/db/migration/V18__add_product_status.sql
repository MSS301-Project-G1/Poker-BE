ALTER TABLE products ADD COLUMN IF NOT EXISTS status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE';

-- Restore defective item Ánh Khang Rồng back to is_deleted = false with status = 'MAINTAIN'
UPDATE products SET is_deleted = false, status = 'MAINTAIN' WHERE id = '01a1809e-4598-77ad-bf52-498fd2cc1e29';

CREATE INDEX IF NOT EXISTS idx_products_status ON products(status);

