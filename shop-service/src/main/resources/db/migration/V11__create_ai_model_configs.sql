-- V11: Create AI Model Configs table for dynamic provider / model selection
CREATE TABLE IF NOT EXISTS ai_model_configs (
    id UUID PRIMARY KEY,
    provider VARCHAR(50) NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    display_name VARCHAR(150) NOT NULL,
    capabilities JSON NOT NULL DEFAULT '[]'::json,
    is_active BOOLEAN NOT NULL DEFAULT true,
    is_default BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ai_model_provider_name UNIQUE (provider, model_name)
);

CREATE INDEX IF NOT EXISTS idx_ai_model_configs_active
    ON ai_model_configs (is_active, is_default);

-- Seed initial models
INSERT INTO ai_model_configs (id, provider, model_name, display_name, capabilities, is_active, is_default)
VALUES
    (
        'a1000000-0000-0000-0000-000000000001',
        'GEMINI',
        'gemini-1.5-pro',
        'Gemini 1.5 Pro (Đa giác quan & Phân tích chuyên sâu)',
        '["STYLE_SPEC_GENERATION", "DEALER_POSE_GENERATION", "PROMPT_REFINEMENT"]'::json,
        true,
        true
    ),
    (
        'a1000000-0000-0000-0000-000000000002',
        'GEMINI',
        'gemini-1.5-flash',
        'Gemini 1.5 Flash (Tốc độ cao, tối ưu độ trễ)',
        '["STYLE_SPEC_GENERATION", "PROMPT_REFINEMENT"]'::json,
        true,
        false
    ),
    (
        'a1000000-0000-0000-0000-000000000003',
        'GEMINI',
        'imagen-3.0-generate-002',
        'Imagen 3 (Tạo ảnh Dealer 4 tư thế chia bài / lật bài)',
        '["DEALER_POSE_GENERATION"]'::json,
        true,
        false
    ),
    (
        'a1000000-0000-0000-0000-000000000004',
        'OPENAI',
        'gpt-4o',
        'OpenAI GPT-4o (Dự phòng đa phương thức)',
        '["STYLE_SPEC_GENERATION", "PROMPT_REFINEMENT"]'::json,
        false,
        false
    )
ON CONFLICT (provider, model_name) DO NOTHING;
