-- Clean up ticket and revocation tables as product deletion now auto-refunds directly to user wallet
DROP TABLE IF EXISTS refund_tickets CASCADE;
DROP TABLE IF EXISTS revoked_inventory_items CASCADE;

