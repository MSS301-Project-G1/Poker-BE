-- Replace the predictable sequence-based public code with a stable random-looking code.
-- The code remains searchable and unique, but no longer reveals signup order.
UPDATE profiles
SET player_code = 'PK' || upper(substr(md5(account_id::text), 1, 8));

CREATE OR REPLACE FUNCTION generate_player_code()
RETURNS varchar(24)
LANGUAGE plpgsql
AS $$
DECLARE candidate varchar(24);
BEGIN
    LOOP
        candidate := 'PK' || upper(substr(md5(random()::text || clock_timestamp()::text), 1, 8));
        IF NOT EXISTS (SELECT 1 FROM profiles WHERE player_code = candidate) THEN
            RETURN candidate;
        END IF;
    END LOOP;
END;
$$;

ALTER TABLE profiles ALTER COLUMN player_code SET DEFAULT generate_player_code();
