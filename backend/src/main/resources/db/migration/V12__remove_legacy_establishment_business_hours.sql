ALTER TABLE establishments
    DROP CONSTRAINT ck_establishments_business_hours,
    DROP COLUMN opening_time,
    DROP COLUMN closing_time;
