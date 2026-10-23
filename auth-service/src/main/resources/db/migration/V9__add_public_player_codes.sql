CREATE SEQUENCE player_code_seq START WITH 10000001;
ALTER TABLE profiles ADD COLUMN player_code varchar(24)
    DEFAULT ('PK' || nextval('player_code_seq')::text) NOT NULL;
ALTER TABLE profiles ADD CONSTRAINT profiles_player_code_unique UNIQUE (player_code);
