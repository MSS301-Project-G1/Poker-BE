-- ============================================================================
-- V5: Seed Coin Bundles (Riot Games model) & Rich Game Assets (Skins & Effects)
-- ============================================================================

-- 1. Seed Coin Bundles (VIP Coin packages)
INSERT INTO coin_bundles (id, code, name, price_vnd, base_vip_coin, bonus_vip_coin, bonus_percentage, badge_label, icon_url, is_active, sort_order, created_at, updated_at) VALUES
    ('c0000000-0000-0000-0000-000000000001', 'BUNDLE_10K', 'Gói Khởi Động 10K', 10000, 10, 0, 0, 'Phổ thông', 'https://assets.msspoker.com/bundles/bundle_10k.png', TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('c0000000-0000-0000-0000-000000000002', 'BUNDLE_20K', 'Gói Bài Thủ 20K', 20000, 20, 0, 0, 'Phổ thông', 'https://assets.msspoker.com/bundles/bundle_20k.png', TRUE, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('c0000000-0000-0000-0000-000000000003', 'BUNDLE_50K', 'Gói Tiết Kiệm 50K (+5%)', 50000, 50, 2, 5, 'Tiết kiệm', 'https://assets.msspoker.com/bundles/bundle_50k.png', TRUE, 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('c0000000-0000-0000-0000-000000000004', 'BUNDLE_100K', 'Gói Kim Cương 100K (+10%)', 100000, 100, 10, 10, 'HOT', 'https://assets.msspoker.com/bundles/bundle_100k.png', TRUE, 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('c0000000-0000-0000-0000-000000000005', 'BUNDLE_200K', 'Gói Đại Gia 200K (+15%)', 200000, 200, 30, 15, 'Ưu đãi', 'https://assets.msspoker.com/bundles/bundle_200k.png', TRUE, 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('c0000000-0000-0000-0000-000000000006', 'BUNDLE_500K', 'Gói Hoàng Gia 500K (+20%)', 500000, 500, 100, 20, 'VIP', 'https://assets.msspoker.com/bundles/bundle_500k.png', TRUE, 6, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('c0000000-0000-0000-0000-000000000007', 'BUNDLE_1M', 'Gói Chí Tôn 1M (+25%)', 1000000, 1000, 250, 25, 'Super VIP', 'https://assets.msspoker.com/bundles/bundle_1m.png', TRUE, 7, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('c0000000-0000-0000-0000-000000000008', 'BUNDLE_2M', 'Gói Huyền Thoại 2M (+30%)', 2000000, 2000, 600, 30, 'Max Value', 'https://assets.msspoker.com/bundles/bundle_2m.png', TRUE, 8, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO NOTHING;

-- 2. Seed Rich Game Assets (Skin Lá Bài, Hiệu Ứng All-In, Victory, Bàn Chơi)
INSERT INTO products (id, category_id, name, description, tier, price_vnd, price_vip_coin, asset_url, preview_url, metadata, is_exclusive, is_deleted, created_at, updated_at) VALUES
    -- Skin Lưng Bài Cyberpunk (CARD_BACK)
    ('b0000000-0000-0000-0000-000000000010', 'a0000000-0000-0000-0000-000000000001', 'Lưng Bài Neon Cyberpunk', 'Mặt sau lá bài phát sáng phong cách công nghệ tương lai.', 'VIP', 50000, 50,
     'https://assets.msspoker.com/cards/cyber/back.png',
     'https://assets.msspoker.com/cards/cyber/preview.png',
     '{"card_back_url": "https://assets.msspoker.com/cards/cyber/back.png", "glow_effect": true, "flip_sound_url": "https://assets.msspoker.com/audio/cyber_flip.mp3"}',
     FALSE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

    -- Skin Bộ Mặt Lá Bài 52 Lá (CARD_FACE)
    ('b0000000-0000-0000-0000-000000000011', 'a0000000-0000-0000-0000-000000000009', 'Bộ Bài Hoàng Gia Hắc Kim', 'Bộ bài nghệ thuật 52 lá dát vàng sang trọng trên nền đen tuyền.', 'SUPER_VIP', 100000, 100,
     'https://assets.msspoker.com/cards/gold_black/deck_texture.png',
     'https://assets.msspoker.com/cards/gold_black/preview.png',
     '{"deck_atlas_url": "https://assets.msspoker.com/cards/gold_black/deck_atlas.json", "deck_texture_url": "https://assets.msspoker.com/cards/gold_black/deck_texture.png", "card_style": "GOLD_BLACK"}',
     FALSE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

    -- Hiệu Ứng All-in Bão Lửa Rồng Vàng (ALL_IN_EFFECT)
    ('b0000000-0000-0000-0000-000000000012', 'a0000000-0000-0000-0000-000000000010', 'Hiệu Ứng All-In Rồng Lửa', 'Bốc lửa đỏ rực kèm tiếng rồng gầm rung chuyển bàn chơi khi bấm All-In.', 'SUPER_VIP', 150000, 150,
     'https://assets.msspoker.com/effects/allin_dragon/effect.json',
     'https://assets.msspoker.com/effects/allin_dragon/preview.gif',
     '{"effect_type": "SPINE", "animation_url": "https://assets.msspoker.com/effects/allin_dragon/dragon.json", "atlas_url": "https://assets.msspoker.com/effects/allin_dragon/dragon.atlas", "sound_url": "https://assets.msspoker.com/audio/dragon_roar.mp3", "duration_ms": 3200, "screen_shake": true}',
     FALSE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

    -- Hiệu Ứng Chiến Thắng Mưa Tiền (VICTORY_EFFECT)
    ('b0000000-0000-0000-0000-000000000013', 'a0000000-0000-0000-0000-000000000005', 'Hiệu Ứng Mưa Tiền Vàng', 'Tiền vàng và pháo hoa rực rỡ rơi ngập bàn chơi khi giành chiến thắng ván bài.', 'VIP', 80000, 80,
     'https://assets.msspoker.com/effects/victory_cash/cash_fall.json',
     'https://assets.msspoker.com/effects/victory_cash/preview.gif',
     '{"effect_type": "PARTICLE", "particle_json": "https://assets.msspoker.com/effects/victory_cash/particle.json", "sound_url": "https://assets.msspoker.com/audio/victory_cheer.mp3", "duration_ms": 4000}',
     FALSE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

    -- Bàn Chơi Thảm Nỉ Ma Cà Rồng (TABLE)
    ('b0000000-0000-0000-0000-000000000014', 'a0000000-0000-0000-0000-000000000002', 'Bàn Chơi Đỏ Dạ Quang', 'Bàn nỉ đỏ nhung quý tộc kết hợp viền gỗ gụ viền kim loại.', 'NORMAL', 30000, 30,
     'https://assets.msspoker.com/tables/velvet_red.png',
     'https://assets.msspoker.com/tables/velvet_red_preview.png',
     '{"felt_color": "#8B0000", "table_texture_url": "https://assets.msspoker.com/tables/velvet_red.png", "chip_sound_url": "https://assets.msspoker.com/audio/chip_wood.mp3"}',
     FALSE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
