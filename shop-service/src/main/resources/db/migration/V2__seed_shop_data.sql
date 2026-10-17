-- ============================================================================
-- V2: Seed Categories and Products Data (MVP)
-- Categories: 8 categories per SPEC 9.5
-- Products: Frame items (NORMAL, VIP, SUPER_VIP) and 1 exclusive tournament reward
-- ============================================================================

-- Seed 8 Categories
INSERT INTO categories (id, code, name, created_at) VALUES
    ('a0000000-0000-0000-0000-000000000001', 'CARD_BACK', 'Mặt sau lá bài', CURRENT_TIMESTAMP),
    ('a0000000-0000-0000-0000-000000000002', 'TABLE', 'Bàn chơi', CURRENT_TIMESTAMP),
    ('a0000000-0000-0000-0000-000000000003', 'FRAME', 'Khung Avatar', CURRENT_TIMESTAMP),
    ('a0000000-0000-0000-0000-000000000004', 'DEALER', 'Người chia bài', CURRENT_TIMESTAMP),
    ('a0000000-0000-0000-0000-000000000005', 'VICTORY_EFFECT', 'Hiệu ứng chiến thắng', CURRENT_TIMESTAMP),
    ('a0000000-0000-0000-0000-000000000006', 'COIN_SKIN', 'Skin đồng xu', CURRENT_TIMESTAMP),
    ('a0000000-0000-0000-0000-000000000007', 'EMOJI', 'Biểu cảm', CURRENT_TIMESTAMP),
    ('a0000000-0000-0000-0000-000000000008', 'SOUND', 'Âm thanh', CURRENT_TIMESTAMP)
ON CONFLICT (code) DO NOTHING;

-- Seed Products for FRAME
INSERT INTO products (id, category_id, name, description, tier, price_vnd, asset_url, is_exclusive, is_deleted, created_at, updated_at) VALUES
    ('b0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000003', 'Khung Tân Thủ', 'Khung avatar thanh lịch dành cho bài thủ khởi đầu.', 'NORMAL', 20000, 'https://assets.msspoker.com/skins/frames/frame_rookie.png', FALSE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('b0000000-0000-0000-0000-000000000002', 'a0000000-0000-0000-0000-000000000003', 'Khung Rồng Vàng', 'Khung avatar Rồng Vàng hoàng gia uy nghiêm.', 'VIP', 50000, 'https://assets.msspoker.com/skins/frames/frame_golden_dragon.png', FALSE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('b0000000-0000-0000-0000-000000000003', 'a0000000-0000-0000-0000-000000000003', 'Khung Kim Cương Chí Tôn', 'Khung avatar Kim Cương lấp lánh khẳng định đẳng cấp đại gia.', 'SUPER_VIP', 100000, 'https://assets.msspoker.com/skins/frames/frame_supreme_diamond.png', FALSE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('b0000000-0000-0000-0000-000000000004', 'a0000000-0000-0000-0000-000000000003', 'Khung Quán Quân Giải Đấu', 'Khung avatar độc quyền chỉ trao cho nhà vô địch giải đấu.', 'SUPER_VIP', 0, 'https://assets.msspoker.com/skins/frames/frame_champion.png', TRUE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
