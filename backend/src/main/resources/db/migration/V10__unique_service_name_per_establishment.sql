-- Includes inactive services. Existing duplicates must be resolved before migration.
CREATE UNIQUE INDEX uk_salon_services_establishment_name
    ON salon_services (establishment_id, lower(btrim(name)));
