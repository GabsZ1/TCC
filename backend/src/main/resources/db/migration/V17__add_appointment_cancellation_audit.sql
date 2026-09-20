ALTER TABLE appointments
    ADD COLUMN cancellation_reason VARCHAR(500),
    ADD COLUMN cancelled_at TIMESTAMP WITHOUT TIME ZONE,
    ADD COLUMN cancelled_by_user_id BIGINT REFERENCES users(id);

CREATE INDEX idx_appointments_cancelled_by_user
    ON appointments (cancelled_by_user_id);