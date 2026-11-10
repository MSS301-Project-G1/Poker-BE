-- ============================================================================
-- V16: Clean up obsolete shop categories and associated test data
-- Keeps only 5 core categories: CARD_SKIN, COIN_SKIN, TABLE, DEALER, SOUND
-- ============================================================================

-- 1. Remove inventory items referencing products of unwanted categories
DELETE FROM inventory_items WHERE product_id IN (
    SELECT id FROM products WHERE category_id IN (
        SELECT id FROM categories WHERE code IN ('FRAME', 'VICTORY_EFFECT', 'ALL_IN_EFFECT', 'EMOJI')
    )
);

-- 2. Remove orders referencing products of unwanted categories
DELETE FROM orders WHERE product_id IN (
    SELECT id FROM products WHERE category_id IN (
        SELECT id FROM categories WHERE code IN ('FRAME', 'VICTORY_EFFECT', 'ALL_IN_EFFECT', 'EMOJI')
    )
);

-- 3. Remove products belonging to unwanted categories
DELETE FROM products WHERE category_id IN (
    SELECT id FROM categories WHERE code IN ('FRAME', 'VICTORY_EFFECT', 'ALL_IN_EFFECT', 'EMOJI')
);

-- 4. Delete the unwanted categories
DELETE FROM categories WHERE code IN ('FRAME', 'VICTORY_EFFECT', 'ALL_IN_EFFECT', 'EMOJI');
