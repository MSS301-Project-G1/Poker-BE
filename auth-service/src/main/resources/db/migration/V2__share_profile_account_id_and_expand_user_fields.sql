ALTER TABLE accounts
    ADD COLUMN phone_number VARCHAR(20),
    ADD COLUMN first_login_rewarded BOOLEAN NOT NULL DEFAULT FALSE;

-- Do not silently revive a previously deleted profile belonging to an active account.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM profiles p JOIN accounts a ON a.id = p.account_id
               WHERE p.is_deleted = TRUE AND a.is_deleted = FALSE) THEN
        RAISE EXCEPTION 'Review deleted profiles of active accounts before applying V2';
    END IF;
END $$;

ALTER TABLE profiles
    DROP CONSTRAINT profiles_pkey,
    DROP CONSTRAINT uk_profiles_account,
    DROP COLUMN id,
    DROP COLUMN is_deleted,
    ADD CONSTRAINT profiles_pkey PRIMARY KEY (account_id),
    ADD COLUMN full_name VARCHAR(150),
    ADD COLUMN gender VARCHAR(16),
    ADD COLUMN birth_date DATE,
    ADD COLUMN bio VARCHAR(500),
    ADD COLUMN country_code VARCHAR(2),
    ADD COLUMN city VARCHAR(100),
    ADD CONSTRAINT ck_profiles_gender CHECK (gender IN ('MALE', 'FEMALE', 'OTHER'));
