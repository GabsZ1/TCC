ALTER TABLE salon_services
    ADD COLUMN duration_minutes INTEGER NOT NULL DEFAULT 30,
    ADD CONSTRAINT ck_salon_services_duration CHECK (duration_minutes > 0);

ALTER TABLE establishments
    ADD COLUMN opening_time TIME NOT NULL DEFAULT TIME '09:00',
    ADD COLUMN closing_time TIME NOT NULL DEFAULT TIME '18:00',
    ADD CONSTRAINT ck_establishments_business_hours CHECK (opening_time < closing_time);

ALTER TABLE users
    ALTER COLUMN establishment_id DROP NOT NULL;

ALTER TABLE users
    DROP CONSTRAINT ck_users_role;

ALTER TABLE users
    ADD CONSTRAINT ck_users_role CHECK (role IN ('ADMIN', 'PROFISSIONAL', 'CLIENTE'));

CREATE TABLE appointments (
    id BIGSERIAL PRIMARY KEY,
    establishment_id BIGINT NOT NULL,
    client_id BIGINT NOT NULL,
    professional_id BIGINT NOT NULL,
    service_id BIGINT NOT NULL,
    starts_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    ends_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_appointments_establishment
        FOREIGN KEY (establishment_id) REFERENCES establishments (id),
    CONSTRAINT fk_appointments_client
        FOREIGN KEY (client_id) REFERENCES users (id),
    CONSTRAINT fk_appointments_professional
        FOREIGN KEY (professional_id) REFERENCES users (id),
    CONSTRAINT fk_appointments_service
        FOREIGN KEY (service_id) REFERENCES salon_services (id),
    CONSTRAINT ck_appointments_period CHECK (starts_at < ends_at),
    CONSTRAINT ck_appointments_status CHECK (status IN ('CONFIRMED', 'CANCELLED'))
);

CREATE INDEX idx_appointments_professional_period
    ON appointments (professional_id, starts_at, ends_at);

CREATE INDEX idx_appointments_client
    ON appointments (client_id, starts_at);

