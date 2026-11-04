-- Migration V21: Give the seeded red table (V5) a felt that exists.
-- V5 pointed it at placeholder links on assets.msspoker.com, a domain that was never set up, so the equipped table
-- kept the default felt. The felt is now in the team bucket with the other seeded cosmetics (signed on read by
-- AssetUrlSigner). A row an admin has already given another image is left alone.
UPDATE products
SET asset_url   = 'https://the-one-bucket-vippro.s3.ap-southeast-1.amazonaws.com/poker/shop/seed/tables/velvet-red/felt.webp',
    preview_url = 'https://the-one-bucket-vippro.s3.ap-southeast-1.amazonaws.com/poker/shop/seed/tables/velvet-red/preview.webp',
    metadata    = jsonb_set(COALESCE(metadata::jsonb, '{}'::jsonb), '{table_texture_url}',
                            to_jsonb('https://the-one-bucket-vippro.s3.ap-southeast-1.amazonaws.com/poker/shop/seed/tables/velvet-red/felt.webp'::text))::json,
    updated_at  = CURRENT_TIMESTAMP
WHERE id = 'b0000000-0000-0000-0000-000000000014'
  AND asset_url = 'https://assets.msspoker.com/tables/velvet_red.png';
