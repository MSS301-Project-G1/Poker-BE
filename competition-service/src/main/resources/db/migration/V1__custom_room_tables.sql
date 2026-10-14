CREATE TABLE custom_rooms (
    id UUID PRIMARY KEY, room_code VARCHAR(16) NOT NULL UNIQUE, owner_id UUID NOT NULL,
    max_players INTEGER NOT NULL, small_blind BIGINT NOT NULL, big_blind BIGINT NOT NULL,
    visibility VARCHAR(20) NOT NULL, password_hash VARCHAR(100),
    status VARCHAR(20) NOT NULL, current_source_id UUID UNIQUE, table_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CHECK (max_players BETWEEN 2 AND 10),
    CHECK (small_blind > 0 AND big_blind > small_blind)
);
CREATE INDEX idx_custom_rooms_list ON custom_rooms(visibility, status, created_at);
CREATE TABLE room_members (
    id UUID PRIMARY KEY, room_id UUID NOT NULL REFERENCES custom_rooms(id),
    account_id UUID NOT NULL, is_ready BOOLEAN NOT NULL DEFAULT FALSE,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (room_id, account_id)
);
CREATE INDEX idx_room_members_account ON room_members(account_id);
CREATE TABLE competition_outbox (
    id UUID PRIMARY KEY, event_type VARCHAR(64) NOT NULL, body TEXT NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_competition_outbox_pending ON competition_outbox(published_at, occurred_at);
