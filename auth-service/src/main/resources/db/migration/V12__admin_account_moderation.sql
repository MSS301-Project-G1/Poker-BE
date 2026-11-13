CREATE TABLE account_moderation_events (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id),
    actor_id UUID NOT NULL REFERENCES accounts(id),
    action VARCHAR(32) NOT NULL CHECK (action IN ('RESET_DISPLAY_NAME', 'REMOVE_AVATAR', 'CLEAR_BIO',
        'REVOKE_SESSIONS', 'SEND_PASSWORD_RESET', 'RESEND_VERIFICATION')),
    reason VARCHAR(500) NOT NULL,
    previous_value VARCHAR(500),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_account_moderation_events_account_time ON account_moderation_events(account_id, occurred_at DESC, id DESC);
