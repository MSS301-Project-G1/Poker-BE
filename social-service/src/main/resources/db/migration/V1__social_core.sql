CREATE TABLE relation_locks (pair_key VARCHAR(73) PRIMARY KEY);
CREATE TABLE friend_requests (
 id UUID PRIMARY KEY, requester_id UUID NOT NULL, addressee_id UUID NOT NULL,
 pair_key VARCHAR(73) NOT NULL,status VARCHAR(20) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL,updated_at TIMESTAMPTZ NOT NULL,is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
 CHECK(requester_id<>addressee_id),CHECK(status IN ('PENDING','ACCEPTED','DECLINED','CANCELLED')));
CREATE UNIQUE INDEX uq_pending_friend_pair ON friend_requests(pair_key) WHERE status='PENDING' AND NOT is_deleted;
CREATE INDEX idx_requests_incoming ON friend_requests(addressee_id,created_at,id);
CREATE INDEX idx_requests_outgoing ON friend_requests(requester_id,created_at,id);
CREATE TABLE friendships (
 id UUID PRIMARY KEY,account_id UUID NOT NULL,friend_id UUID NOT NULL,
 created_at TIMESTAMPTZ NOT NULL,updated_at TIMESTAMPTZ NOT NULL,is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
 UNIQUE(account_id,friend_id),CHECK(account_id<>friend_id));
CREATE TABLE blocks (
 id UUID PRIMARY KEY,blocker_id UUID NOT NULL,blocked_id UUID NOT NULL,
 created_at TIMESTAMPTZ NOT NULL,updated_at TIMESTAMPTZ NOT NULL,is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
 UNIQUE(blocker_id,blocked_id),CHECK(blocker_id<>blocked_id));
CREATE TABLE processed_events(event_id UUID PRIMARY KEY,event_type VARCHAR(100) NOT NULL,processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE social_outbox (
 id UUID PRIMARY KEY,body TEXT NOT NULL,occurred_at TIMESTAMPTZ NOT NULL,
 published_at TIMESTAMPTZ,attempts INTEGER NOT NULL DEFAULT 0,next_attempt_at TIMESTAMPTZ NOT NULL,last_error VARCHAR(200));
CREATE INDEX idx_social_outbox_pending ON social_outbox(next_attempt_at,occurred_at) WHERE published_at IS NULL;
