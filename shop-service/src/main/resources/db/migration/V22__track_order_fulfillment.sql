ALTER TABLE orders ADD COLUMN fulfillment_status VARCHAR(30);
ALTER TABLE orders ADD COLUMN fulfilled_at TIMESTAMPTZ;

-- Existing PAID rows do not prove that the wallet or inventory received the reward.
-- Do not automatically credit historical payments without a ledger review.
UPDATE orders
SET fulfillment_status = CASE WHEN status = 'PAID' THEN 'REVIEW_REQUIRED' ELSE 'PENDING' END;

ALTER TABLE orders ALTER COLUMN fulfillment_status SET DEFAULT 'PENDING';
ALTER TABLE orders ALTER COLUMN fulfillment_status SET NOT NULL;
ALTER TABLE orders ADD CONSTRAINT ck_order_fulfillment_status
    CHECK (fulfillment_status IN ('PENDING', 'FULFILLED', 'REVIEW_REQUIRED'));

CREATE INDEX idx_orders_pending_fulfillment ON orders (payos_order_code)
    WHERE status = 'PAID' AND fulfillment_status = 'PENDING';
