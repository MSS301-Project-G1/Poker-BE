ALTER TABLE tournaments ADD COLUMN invites_sent_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE tournaments ADD COLUMN replacements_sent_at TIMESTAMP WITH TIME ZONE;
CREATE TABLE item_grants (
    id UUID PRIMARY KEY, account_id UUID NOT NULL, product_id UUID NOT NULL,
    ref_id VARCHAR(100), idempotency_key VARCHAR(160) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    granted_at TIMESTAMP WITH TIME ZONE,
    attempts INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_item_grants_pending ON item_grants(granted_at, created_at);
