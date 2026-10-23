-- ============================================================================
-- V8: Add preview_video_url column to products table for video review feature
-- ============================================================================

ALTER TABLE products ADD COLUMN IF NOT EXISTS preview_video_url VARCHAR(500);
