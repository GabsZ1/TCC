ALTER TABLE establishment_profiles
    ADD COLUMN latitude NUMERIC(9, 6),
    ADD COLUMN longitude NUMERIC(9, 6),
    ADD CONSTRAINT ck_establishment_profiles_latitude CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    ADD CONSTRAINT ck_establishment_profiles_longitude CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
    ADD CONSTRAINT ck_establishment_profiles_coordinates_pair CHECK ((latitude IS NULL) = (longitude IS NULL));
