ALTER TABLE establishments
    ADD COLUMN minimum_advance_minutes INTEGER NOT NULL DEFAULT 120,
    ADD COLUMN maximum_advance_days INTEGER NOT NULL DEFAULT 60,
    ADD CONSTRAINT ck_establishments_minimum_advance CHECK (minimum_advance_minutes BETWEEN 0 AND 10080),
    ADD CONSTRAINT ck_establishments_maximum_advance CHECK (maximum_advance_days BETWEEN 1 AND 365),
    ADD CONSTRAINT ck_establishments_advance_window CHECK (minimum_advance_minutes <= maximum_advance_days * 1440);
