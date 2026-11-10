-- V17: Add price_paid_vip_coin to inventory_items, create revoked_inventory_items and refund_tickets

ALTER TABLE inventory_items ADD COLUMN IF NOT EXISTS price_paid_vip_coin BIGINT;

CREATE TABLE IF NOT EXISTS revoked_inventory_items (
    id                   UUID PRIMARY KEY,
    account_id           UUID         NOT NULL,
    product_id           UUID         NOT NULL,
    product_name         VARCHAR(150) NOT NULL,
    product_preview_url  VARCHAR(500),
    category_code        VARCHAR(50),
    source               VARCHAR(30)  NOT NULL,
    price_paid_vip_coin  BIGINT       NOT NULL,
    price_source         VARCHAR(30)  NOT NULL,
    acquired_at          TIMESTAMPTZ  NOT NULL,
    revoke_reason        VARCHAR(500) NOT NULL,
    revoked_by           UUID         NOT NULL,
    revoked_at           TIMESTAMPTZ  NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_revoked_account ON revoked_inventory_items(account_id, revoked_at DESC);

CREATE TABLE IF NOT EXISTS refund_tickets (
    id                 UUID PRIMARY KEY,
    account_id         UUID          NOT NULL,
    revoked_item_id    UUID          NOT NULL REFERENCES revoked_inventory_items(id),
    status             VARCHAR(20)   NOT NULL,
    original_amount    BIGINT        NOT NULL,
    approved_amount    BIGINT,
    evidence_image_url VARCHAR(500)  NOT NULL,
    user_message       VARCHAR(1000) NOT NULL,
    admin_note         VARCHAR(500),
    reject_reason      VARCHAR(500),
    reviewed_by        UUID,
    reviewed_at        TIMESTAMPTZ,
    version            BIGINT        NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ   NOT NULL,
    updated_at         TIMESTAMPTZ   NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_refund_tickets_status ON refund_tickets(status, created_at);
CREATE INDEX IF NOT EXISTS idx_refund_tickets_account ON refund_tickets(account_id, created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uq_refund_ticket_active ON refund_tickets(revoked_item_id)
    WHERE status IN ('PENDING', 'APPROVED');

