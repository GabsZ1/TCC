-- The application already scopes these relations by establishment. Enforce the
-- same invariant in PostgreSQL so direct writes cannot create cross-tenant links.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM appointments a
        JOIN users p ON p.id = a.professional_id
        WHERE p.establishment_id IS DISTINCT FROM a.establishment_id
    ) THEN
        RAISE EXCEPTION 'Cannot apply tenant integrity: appointments reference professionals from another establishment';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM appointments a
        JOIN salon_services s ON s.id = a.service_id
        WHERE s.establishment_id IS DISTINCT FROM a.establishment_id
    ) THEN
        RAISE EXCEPTION 'Cannot apply tenant integrity: appointments reference services from another establishment';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM professional_calendar_events e
        JOIN users p ON p.id = e.professional_id
        WHERE p.establishment_id IS DISTINCT FROM e.establishment_id
    ) THEN
        RAISE EXCEPTION 'Cannot apply tenant integrity: calendar events reference professionals from another establishment';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM professional_services ps
        JOIN users p ON p.id = ps.professional_id
        JOIN salon_services s ON s.id = ps.service_id
        WHERE p.establishment_id IS DISTINCT FROM s.establishment_id
    ) THEN
        RAISE EXCEPTION 'Cannot apply tenant integrity: professional services cross establishments';
    END IF;
END $$;

ALTER TABLE users
    ADD CONSTRAINT uk_users_id_establishment UNIQUE (id, establishment_id);

ALTER TABLE salon_services
    ADD CONSTRAINT uk_salon_services_id_establishment UNIQUE (id, establishment_id);

ALTER TABLE appointments
    ADD CONSTRAINT fk_appointments_professional_tenant
        FOREIGN KEY (professional_id, establishment_id) REFERENCES users(id, establishment_id),
    ADD CONSTRAINT fk_appointments_service_tenant
        FOREIGN KEY (service_id, establishment_id) REFERENCES salon_services(id, establishment_id);

ALTER TABLE professional_calendar_events
    ADD CONSTRAINT fk_professional_calendar_events_tenant
        FOREIGN KEY (professional_id, establishment_id) REFERENCES users(id, establishment_id);

CREATE FUNCTION enforce_professional_service_tenant()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM users p
        JOIN salon_services s ON s.establishment_id = p.establishment_id
        WHERE p.id = NEW.professional_id
          AND s.id = NEW.service_id
    ) THEN
        RAISE EXCEPTION 'Professional and service must belong to the same establishment'
            USING ERRCODE = '23503', CONSTRAINT = 'fk_professional_services_tenant';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_professional_services_tenant
    BEFORE INSERT OR UPDATE OF professional_id, service_id ON professional_services
    FOR EACH ROW EXECUTE FUNCTION enforce_professional_service_tenant();
