ALTER TABLE appointments
    ADD COLUMN service_name VARCHAR(100),
    ADD COLUMN agreed_price NUMERIC(10, 2),
    ADD COLUMN commission_percentage NUMERIC(5, 2),
    ADD COLUMN commission_amount NUMERIC(10, 2),
    ADD COLUMN duration_minutes INTEGER;

UPDATE appointments a
SET service_name = s.name,
    agreed_price = s.price,
    commission_percentage = s.commission_percentage,
    commission_amount = ROUND(s.price * s.commission_percentage / 100, 2),
    duration_minutes = s.duration_minutes
FROM salon_services s
WHERE s.id = a.service_id;

ALTER TABLE appointments
    ALTER COLUMN service_name SET NOT NULL,
    ALTER COLUMN agreed_price SET NOT NULL,
    ALTER COLUMN commission_percentage SET NOT NULL,
    ALTER COLUMN commission_amount SET NOT NULL,
    ALTER COLUMN duration_minutes SET NOT NULL,
    ADD CONSTRAINT ck_appointments_agreed_price CHECK (agreed_price > 0),
    ADD CONSTRAINT ck_appointments_commission_percentage CHECK (commission_percentage BETWEEN 0 AND 100),
    ADD CONSTRAINT ck_appointments_commission_amount CHECK (commission_amount >= 0),
    ADD CONSTRAINT ck_appointments_duration_minutes CHECK (duration_minutes > 0);
