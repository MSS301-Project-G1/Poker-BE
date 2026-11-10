ALTER TABLE refresh_tokens ADD COLUMN active_tab_id UUID;
ALTER TABLE refresh_tokens ADD COLUMN replaced BOOLEAN NOT NULL DEFAULT FALSE;

-- Pre-upgrade logins have no tab owner/sid claim. Require a fresh login once after deployment.
UPDATE refresh_tokens SET revoked_at = CURRENT_TIMESTAMP, replaced = TRUE WHERE revoked_at IS NULL;
