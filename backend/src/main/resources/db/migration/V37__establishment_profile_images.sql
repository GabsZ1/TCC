ALTER TABLE establishment_profiles
    ADD COLUMN logo_id BIGINT REFERENCES media(id),
    ADD COLUMN cover_id BIGINT REFERENCES media(id);
