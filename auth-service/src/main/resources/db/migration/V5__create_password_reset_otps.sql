-- Recovery OTPs never activate accounts or share registration codes.
CREATE TABLE password_reset_otps (
    account_id UUID PRIMARY KEY REFERENCES accounts(id),
    code_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    last_sent_at TIMESTAMPTZ NOT NULL,
    failed_attempts INTEGER NOT NULL DEFAULT 0 CHECK (failed_attempts >= 0)
);
