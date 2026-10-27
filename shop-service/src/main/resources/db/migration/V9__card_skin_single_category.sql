-- ============================================================================
-- V9: Merge CARD_FACE and CARD_BACK into single CARD_SKIN category
-- ============================================================================

-- 1. Insert or update CARD_SKIN category
INSERT INTO categories (id, code, name, created_at)
VALUES ('a0000000-0000-0000-0000-000000000011', 'CARD_SKIN', 'Skin bộ bài', CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE SET name = 'Skin bộ bài';

-- 2. Re-assign all products belonging to CARD_FACE or CARD_BACK to CARD_SKIN
UPDATE products 
SET category_id = (SELECT id FROM categories WHERE code = 'CARD_SKIN')
WHERE category_id IN (SELECT id FROM categories WHERE code IN ('CARD_FACE', 'CARD_BACK'));

-- 3. Deduplicate equipped items: each account keeps at most 1 equipped CARD_SKIN (most recent)
UPDATE inventory_items 
SET is_equipped = FALSE
WHERE id IN (
  SELECT id FROM (
    SELECT i.id, ROW_NUMBER() OVER (PARTITION BY i.account_id ORDER BY i.acquired_at DESC) AS rn
    FROM inventory_items i 
    JOIN products p ON p.id = i.product_id
    WHERE i.is_equipped = TRUE
      AND p.category_id = (SELECT id FROM categories WHERE code = 'CARD_SKIN')
  ) t WHERE t.rn > 1
);

-- 4. Safely delete obsolete categories CARD_FACE and CARD_BACK
DELETE FROM categories WHERE code IN ('CARD_FACE', 'CARD_BACK');
