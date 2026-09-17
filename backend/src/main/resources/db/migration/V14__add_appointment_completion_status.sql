ALTER TABLE appointments
    DROP CONSTRAINT ck_appointments_status;

ALTER TABLE appointments
    ADD CONSTRAINT ck_appointments_status
        CHECK (status IN ('CONFIRMED', 'CANCELLED', 'COMPLETED', 'INVALIDATED'));

ALTER TABLE appointments
    ADD COLUMN invalidation_reason VARCHAR(500);

ALTER TABLE appointments
    ADD CONSTRAINT ck_appointments_invalidation_reason
        CHECK (
            status <> 'INVALIDATED'
            OR (invalidation_reason IS NOT NULL AND btrim(invalidation_reason) <> '')
        );
