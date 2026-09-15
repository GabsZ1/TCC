CREATE TABLE establishment_business_hours (
    id BIGSERIAL PRIMARY KEY,
    establishment_id BIGINT NOT NULL,
    day_of_week VARCHAR(10) NOT NULL,
    closed BOOLEAN NOT NULL DEFAULT FALSE,
    opening_time TIME,
    closing_time TIME,
    CONSTRAINT fk_business_hours_establishment FOREIGN KEY (establishment_id) REFERENCES establishments (id),
    CONSTRAINT uk_business_hours_establishment_day UNIQUE (establishment_id, day_of_week),
    CONSTRAINT ck_business_hours_day CHECK (day_of_week IN ('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY')),
    CONSTRAINT ck_business_hours_period CHECK (
        (closed = TRUE AND opening_time IS NULL AND closing_time IS NULL)
        OR (closed = FALSE AND opening_time IS NOT NULL AND closing_time IS NOT NULL AND opening_time < closing_time)
    )
);

INSERT INTO establishment_business_hours (establishment_id, day_of_week, closed, opening_time, closing_time)
SELECT e.id, day_of_week, FALSE, e.opening_time, e.closing_time
FROM establishments e
CROSS JOIN (VALUES ('MONDAY'), ('TUESDAY'), ('WEDNESDAY'), ('THURSDAY'), ('FRIDAY'), ('SATURDAY'), ('SUNDAY')) AS days(day_of_week);
