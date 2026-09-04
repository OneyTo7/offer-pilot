-- Add multi-provider support fields to users table
-- api_base_url: custom API base URL (null = use platform default)
-- api_model: custom model name (null = use platform default)
ALTER TABLE users
    ADD COLUMN api_base_url VARCHAR(255) DEFAULT NULL,
    ADD COLUMN api_model VARCHAR(100) DEFAULT NULL;