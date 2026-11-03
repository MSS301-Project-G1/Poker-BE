ALTER TABLE accounts ADD COLUMN banned_until TIMESTAMP WITH TIME ZONE;
ALTER TABLE accounts ADD COLUMN ban_reason VARCHAR(500);
ALTER TABLE accounts ADD COLUMN banned_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE accounts ADD COLUMN banned_by UUID REFERENCES accounts(id);

CREATE TABLE account_ban_events (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id),
    actor_id UUID NOT NULL REFERENCES accounts(id),
    action VARCHAR(8) NOT NULL CHECK (action IN ('BAN', 'UNBAN')),
    reason VARCHAR(500) NOT NULL,
    until_at TIMESTAMP WITH TIME ZONE,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_account_ban_events_account_time ON account_ban_events(account_id, occurred_at DESC, id DESC);
