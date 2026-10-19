CREATE TABLE avatar_presets (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    object_key VARCHAR(2048) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID NOT NULL REFERENCES accounts(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_avatar_presets_name CHECK (LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_avatar_presets_key CHECK (object_key !~ '^https?://' AND LENGTH(TRIM(object_key)) > 0)
);

ALTER TABLE profiles ADD COLUMN avatar_preset_id UUID REFERENCES avatar_presets(id);
CREATE INDEX ix_profiles_avatar_preset ON profiles(avatar_preset_id);

-- Bundled SVGs have no S3 object to migrate. Keep uploaded S3 keys unchanged.
UPDATE profiles SET avatar_url = NULL
WHERE avatar_url ~ '^https?://[^/]+/api/profiles/avatars/presets/(spade|heart|diamond)$';

ALTER TABLE profiles ADD CONSTRAINT ck_profiles_avatar_source
    CHECK (avatar_preset_id IS NULL OR avatar_url IS NULL);

COMMENT ON COLUMN profiles.avatar_url IS 'S3 object key for a custom upload; API URLs are generated when reading profiles';
