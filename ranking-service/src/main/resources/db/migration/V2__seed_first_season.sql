-- SPEC §9.4: one ACTIVE season is seeded; season management arrives in GĐ2.
INSERT INTO seasons (id, name, start_at, end_at, status, reset_percent) VALUES (
    '0199e0a0-0000-7000-8000-000000000001', 'Mùa 1',
    TIMESTAMP WITH TIME ZONE '2026-10-01 00:00:00+07',
    TIMESTAMP WITH TIME ZONE '2027-01-01 00:00:00+07',
    'ACTIVE', 50
);
