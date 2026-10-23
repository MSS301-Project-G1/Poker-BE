-- ============================================================================
-- V1: Create Shop Database Schema
-- Tables: categories, products, orders, inventory_items
-- Sequence: orders_payos_order_code_seq
-- ============================================================================

-- 1. Sequence for PayOS order code (Numeric, unique)
CREATE SEQUENCE IF NOT EXISTS orders_payos_order_code_seq START WITH 100001 INCREMENT BY 1;

-- 2. Table: categories
CREATE TABLE categories (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 3. Table: products
CREATE TABLE products (
    id UUID PRIMARY KEY,
    category_id UUID NOT NULL REFERENCES categories(id),
    name VARCHAR(150) NOT NULL,
    description TEXT,
    tier VARCHAR(30) NOT NULL,
    price_vnd BIGINT NOT NULL,
    asset_url VARCHAR(500) NOT NULL,
    is_exclusive BOOLEAN NOT NULL DEFAULT FALSE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_products_category_id ON products(category_id);
CREATE INDEX idx_products_tier ON products(tier);
CREATE INDEX idx_products_is_deleted ON products(is_deleted);

-- 4. Table: orders (Used in Phase B, created now to avoid schema drift)
CREATE TABLE orders (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    product_id UUID NOT NULL REFERENCES products(id),
    amount_vnd BIGINT NOT NULL,
    payos_order_code BIGINT NOT NULL UNIQUE,
    checkout_url VARCHAR(1000),
    qr_code TEXT,
    status VARCHAR(30) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    paid_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_orders_account_id ON orders(account_id);
CREATE INDEX idx_orders_status ON orders(status);

-- 5. Table: inventory_items
CREATE TABLE inventory_items (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    product_id UUID NOT NULL REFERENCES products(id),
    source VARCHAR(30) NOT NULL,
    is_equipped BOOLEAN NOT NULL DEFAULT FALSE,
    acquired_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    grant_key VARCHAR(255) UNIQUE,
    CONSTRAINT uq_inventory_account_product UNIQUE (account_id, product_id)
);

CREATE INDEX idx_inventory_account_id ON inventory_items(account_id);
CREATE INDEX idx_inventory_product_id ON inventory_items(product_id);
