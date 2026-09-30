ALTER TABLE users ADD COLUMN first_access_version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE appointments
    ADD COLUMN source VARCHAR(20),
    ADD COLUMN created_by_user_id BIGINT REFERENCES users(id),
    ADD COLUMN no_show_at TIMESTAMP WITHOUT TIME ZONE,
    ADD COLUMN no_show_by_user_id BIGINT REFERENCES users(id),
    ADD COLUMN invalidated_at TIMESTAMP WITHOUT TIME ZONE,
    ADD COLUMN invalidated_by_user_id BIGINT REFERENCES users(id),
    ADD COLUMN completed_by_user_id BIGINT REFERENCES users(id);

UPDATE appointments SET source = CASE WHEN c.user_id IS NULL THEN 'ADMIN' ELSE 'CLIENT_ONLINE' END
FROM customers c WHERE c.id = appointments.customer_id;
UPDATE appointments SET created_by_user_id = c.user_id FROM customers c
WHERE c.id = appointments.customer_id AND appointments.source = 'CLIENT_ONLINE';
UPDATE appointments SET cancelled_at = COALESCE(cancelled_at, updated_at::timestamp, created_at::timestamp)
WHERE status = 'CANCELLED' AND cancelled_at IS NULL;
UPDATE appointments SET completed_at = COALESCE(completed_at, ends_at)
WHERE status IN ('COMPLETED', 'INVALIDATED') AND completed_at IS NULL;
UPDATE appointments SET no_show_at = COALESCE(updated_at::timestamp, ends_at)
WHERE status = 'NO_SHOW' AND no_show_at IS NULL;
UPDATE appointments SET invalidated_at = COALESCE(updated_at::timestamp, completed_at, ends_at),
    invalidation_reason = COALESCE(NULLIF(TRIM(invalidation_reason), ''), 'Motivo não registrado no histórico')
WHERE status = 'INVALIDATED';

ALTER TABLE appointments ADD CONSTRAINT ck_appointments_lifecycle
    CHECK ((status <> 'CANCELLED' OR cancelled_at IS NOT NULL)
       AND (status <> 'COMPLETED' OR completed_at IS NOT NULL)
       AND (status <> 'NO_SHOW' OR no_show_at IS NOT NULL)
       AND (status <> 'INVALIDATED' OR (invalidated_at IS NOT NULL AND invalidation_reason IS NOT NULL)));
ALTER TABLE appointments ADD CONSTRAINT ck_appointments_source
    CHECK (source IS NULL OR source IN ('CLIENT_ONLINE','ADMIN','PROFESSIONAL'));
