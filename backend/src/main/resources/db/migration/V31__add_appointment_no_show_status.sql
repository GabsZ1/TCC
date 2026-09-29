ALTER TABLE appointments DROP CONSTRAINT ck_appointments_status;
ALTER TABLE appointments ADD CONSTRAINT ck_appointments_status
    CHECK (status IN ('CONFIRMED', 'CANCELLED', 'COMPLETED', 'INVALIDATED', 'NO_SHOW'));
