-- A player is a member of at most one room. Leaving or being kicked deletes the row and a room closes only
-- when its last member leaves, so every row belongs to an open room. Two concurrent joins could pass the
-- open-room check together; this index rejects the second insert.

-- Keep each player's earliest membership from before the index existed.
DELETE FROM room_members m
WHERE EXISTS (SELECT 1 FROM room_members o
              WHERE o.account_id = m.account_id
                AND (o.joined_at < m.joined_at OR (o.joined_at = m.joined_at AND o.id < m.id)));

-- A room that lost its owner goes to its longest-waiting member, as when the owner leaves.
UPDATE custom_rooms r
SET owner_id = (SELECT m.account_id FROM room_members m WHERE m.room_id = r.id ORDER BY m.joined_at, m.id LIMIT 1)
WHERE r.status <> 'CLOSED'
  AND NOT EXISTS (SELECT 1 FROM room_members m WHERE m.room_id = r.id AND m.account_id = r.owner_id)
  AND EXISTS (SELECT 1 FROM room_members m WHERE m.room_id = r.id);

UPDATE custom_rooms r SET status = 'CLOSED'
WHERE r.status <> 'CLOSED' AND NOT EXISTS (SELECT 1 FROM room_members m WHERE m.room_id = r.id);

DROP INDEX idx_room_members_account;
CREATE UNIQUE INDEX uq_room_members_account ON room_members(account_id);
