CREATE TABLE professional_business_hours (
    id BIGSERIAL PRIMARY KEY,
    professional_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    day_of_week VARCHAR(10) NOT NULL,
    closed BOOLEAN NOT NULL DEFAULT FALSE,
    opening_time TIME,
    closing_time TIME,
    CONSTRAINT uk_professional_business_hours UNIQUE (professional_id, day_of_week),
    CONSTRAINT ck_professional_business_hours_times CHECK (
        (closed AND opening_time IS NULL AND closing_time IS NULL)
        OR
        (NOT closed AND opening_time IS NOT NULL AND closing_time IS NOT NULL AND opening_time < closing_time)
    )
);

CREATE TABLE professional_calendar_events (
    id BIGSERIAL PRIMARY KEY,
    establishment_id BIGINT NOT NULL REFERENCES establishments(id),
    professional_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    starts_at TIMESTAMP NOT NULL,
    ends_at TIMESTAMP NOT NULL,
    all_day BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_professional_calendar_events_period CHECK (starts_at < ends_at)
);

CREATE INDEX idx_professional_calendar_events_period
    ON professional_calendar_events (professional_id, starts_at, ends_at);
