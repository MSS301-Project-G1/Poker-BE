-- Old Docker init.sql created the V2 schema before Flyway baselined it at V1.
-- Restore the V1 column name only when V2 has not run; V2 then upgrades normally.
-- This preserves balances and avoids changing checksums of published migrations.
DO $$
BEGIN
    IF to_regclass('wallets') IS NULL THEN
        RETURN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM flyway_schema_history WHERE version = '2' AND success)
       AND EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = current_schema() AND table_name = 'wallets' AND column_name = 'coin_balance')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                       WHERE table_schema = current_schema() AND table_name = 'wallets' AND column_name = 'balance') THEN
        ALTER TABLE wallets RENAME COLUMN coin_balance TO balance;
    END IF;
END $$;
