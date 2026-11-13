CREATE TABLE party_invitation_locks (party_id UUID PRIMARY KEY);
CREATE TABLE party_invitations (
    id UUID PRIMARY KEY, party_id UUID NOT NULL, inviter_id UUID NOT NULL,
    recipient_id UUID NOT NULL, password_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','ACCEPTED','DECLINED','CANCELLED','EXPIRED')),
    created_at TIMESTAMPTZ NOT NULL, expires_at TIMESTAMPTZ NOT NULL,
    CHECK (inviter_id <> recipient_id), CHECK (expires_at > created_at)
);
CREATE UNIQUE INDEX uq_pending_party_invitation ON party_invitations(party_id, recipient_id)
    WHERE status = 'PENDING';
CREATE INDEX idx_party_invitations_recipient ON party_invitations(recipient_id, created_at DESC);
CREATE INDEX idx_party_invitations_inviter ON party_invitations(inviter_id, created_at DESC);
CREATE TABLE party_notification_outbox (
    id UUID PRIMARY KEY, body TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);
CREATE INDEX idx_party_outbox_pending ON party_notification_outbox(created_at) WHERE published_at IS NULL;
