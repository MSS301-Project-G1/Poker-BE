-- V12: Create AI Prompt Presets table
CREATE TABLE IF NOT EXISTS ai_prompt_presets (
    id UUID PRIMARY KEY,
    preset_name VARCHAR(100) NOT NULL,
    category_code VARCHAR(50) NOT NULL,
    description TEXT,
    style_guide_json JSON NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_prompt_preset_name UNIQUE (preset_name)
);

CREATE INDEX IF NOT EXISTS idx_prompt_presets_category
    ON ai_prompt_presets (category_code, is_active);

-- Seed prompt presets
INSERT INTO ai_prompt_presets (id, preset_name, category_code, description, style_guide_json, is_active)
VALUES
    (
        'b1000000-0000-0000-0000-000000000001',
        'Cyber Neon 2077',
        'CARD_SKIN',
        'Phong cách Cyberpunk tương lai với viền dạ quang cyan, nền tím than huyền bí và họa tiết mạch điện tử',
        '{
            "theme": "CYBER_NEON",
            "palette": {
                "primary": "#00f0ff",
                "secondary": "#1a0836",
                "accent": "#ff007f",
                "background": "#0b0318",
                "card_bg": "#120524"
            },
            "card_skin": {
                "pip_style": "GEOMETRIC_NEON",
                "border_radius": 24
            }
        }'::json,
        true
    ),
    (
        'b1000000-0000-0000-0000-000000000002',
        'Hoàng Gia Hắc Kim (Royal Obsidian Gold)',
        'CARD_SKIN',
        'Phong cách quý tộc hoàng gia phương Tây với viền mạ vàng 24K, nền đá hắc diện thạch obsidian và họa tiết rồng vương giả',
        '{
            "theme": "ROYAL_GOLD",
            "palette": {
                "primary": "#d4af37",
                "secondary": "#121214",
                "accent": "#ffd700",
                "background": "#08080a",
                "card_bg": "#101012"
            },
            "card_skin": {
                "pip_style": "CLASSIC_ORNATE_GOLD",
                "border_radius": 24
            }
        }'::json,
        true
    ),
    (
        'b1000000-0000-0000-0000-000000000003',
        'Thủy Tinh Dạ Quang (Frosted Crystal)',
        'CARD_SKIN',
        'Chất liệu pha lê thủy tinh mờ sương, khúc xạ ánh sáng huyền ảo với viền sapphire lấp lánh',
        '{
            "theme": "CRYSTAL_GLASS",
            "palette": {
                "primary": "#38bdf8",
                "secondary": "#0f172a",
                "accent": "#a855f7",
                "background": "#030712",
                "card_bg": "#0c1527"
            },
            "card_skin": {
                "pip_style": "CRYSTAL_FACET",
                "border_radius": 24
            }
        }'::json,
        true
    ),
    (
        'b1000000-0000-0000-0000-000000000004',
        'Croupier Quý Phái Monaco',
        'DEALER',
        'Nữ Dealer trang nhã phong cách sòng bài Monaco châu Âu, vest gile đen ánh kim, nơ cổ thêu chỉ vàng',
        '{
            "theme": "MONACO_CASINO",
            "dealer": {
                "outfit": "ELEGANT_TUXEDO_VEST",
                "poses": ["idle", "deal", "flip", "win"],
                "lighting": "STUDIO_WARM_GLOW"
            }
        }'::json,
        true
    ),
    (
        'b1000000-0000-0000-0000-000000000005',
        'Bàn VIP Monte Carlo Bọc Nhung',
        'TABLE',
        'Bàn poker tròn bọc nỉ nhung xanh thẫm ngọc lục bảo, thành bàn da thuộc nâu cà phê và đường chia cược vàng bóng',
        '{
            "theme": "EMERALD_VELVET",
            "palette": {
                "primary": "#d4af37",
                "secondary": "#2c1810",
                "accent": "#10b981",
                "background": "#064e3b"
            }
        }'::json,
        true
    )
ON CONFLICT (preset_name) DO NOTHING;
