-- V15: Add ON DELETE CASCADE to products_category_id_fkey so category deletion cleanly cascades products
ALTER TABLE products DROP CONSTRAINT IF EXISTS products_category_id_fkey;
ALTER TABLE products ADD CONSTRAINT products_category_id_fkey
    FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE CASCADE;
