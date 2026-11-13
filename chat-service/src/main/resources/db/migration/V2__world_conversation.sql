ALTER TABLE conversations DROP CONSTRAINT conversations_check;
ALTER TABLE conversations ADD CONSTRAINT conversations_check CHECK (
    (type = 'DIRECT' AND direct_key IS NOT NULL AND ref_id IS NULL)
    OR (type = 'MATCH' AND direct_key IS NULL AND ref_id IS NOT NULL)
    OR (type = 'WORLD' AND direct_key IS NULL AND ref_id IS NULL
        AND id = '00000000-0000-0000-0000-000000000001'::uuid)
);
