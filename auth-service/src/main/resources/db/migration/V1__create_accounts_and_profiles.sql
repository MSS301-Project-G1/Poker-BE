CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    account_status VARCHAR(32) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    is_email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    role VARCHAR(16) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_accounts_email UNIQUE (email),
    CONSTRAINT ck_accounts_email_canonical CHECK (email = LOWER(TRIM(email)) AND LENGTH(email) > 0),
    CONSTRAINT ck_accounts_status CHECK (account_status IN ('PENDING_VERIFICATION', 'ACTIVE')),
    CONSTRAINT ck_accounts_role CHECK (role IN ('USER', 'ADMIN'))
);

CREATE TABLE profiles (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    display_name VARCHAR(100),
    avatar_url VARCHAR(2048),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_profiles_account UNIQUE (account_id),
    CONSTRAINT fk_profiles_account FOREIGN KEY (account_id) REFERENCES accounts (id)
);
