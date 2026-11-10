-- V14: Drop unused AI tables and batch generations
DROP TABLE IF EXISTS asset_generation_batches CASCADE;
DROP TABLE IF EXISTS ai_prompt_presets CASCADE;
DROP TABLE IF EXISTS ai_model_configs CASCADE;
