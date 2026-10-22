CREATE TABLE chat_locks(lock_key VARCHAR(160) PRIMARY KEY);
CREATE TABLE match_lifecycle(match_id UUID PRIMARY KEY,finished_at TIMESTAMPTZ);
CREATE TABLE conversations (
 id UUID PRIMARY KEY,type VARCHAR(20) NOT NULL,ref_id UUID,direct_key VARCHAR(73),closed_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL,updated_at TIMESTAMPTZ NOT NULL,is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
 UNIQUE(direct_key),UNIQUE(type,ref_id),
 CHECK((type='DIRECT' AND direct_key IS NOT NULL AND ref_id IS NULL) OR (type='MATCH' AND direct_key IS NULL AND ref_id IS NOT NULL)));
CREATE TABLE conversation_members (
 id UUID PRIMARY KEY,conversation_id UUID NOT NULL REFERENCES conversations(id),account_id UUID NOT NULL,joined_at TIMESTAMPTZ NOT NULL,
 UNIQUE(conversation_id,account_id));
CREATE INDEX idx_chat_members_account ON conversation_members(account_id,conversation_id);
CREATE TABLE messages (
 id UUID PRIMARY KEY,conversation_id UUID NOT NULL REFERENCES conversations(id),sender_id UUID NOT NULL,message_type VARCHAR(20) NOT NULL,
 content TEXT NOT NULL,client_message_id UUID,created_at TIMESTAMPTZ NOT NULL,
 UNIQUE(sender_id,client_message_id),CHECK(message_type='TEXT'),CHECK(length(content)>0));
CREATE INDEX idx_chat_history ON messages(conversation_id,created_at DESC,id DESC);
CREATE TABLE processed_events(event_id UUID PRIMARY KEY,event_type VARCHAR(100) NOT NULL,processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP);
