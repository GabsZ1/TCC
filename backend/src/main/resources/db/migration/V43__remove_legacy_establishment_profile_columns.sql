-- Desde V20, a descricao e o endereco do estabelecimento pertencem ao perfil.
-- Preserva dados de instalacoes antigas sem sobrescrever valores ja cadastrados no perfil.
INSERT INTO establishment_profiles (
    establishment_id, about_us, postal_code, street, street_number,
    address_complement, neighborhood, city, state
)
SELECT
    id, description, postal_code, street, street_number,
    address_complement, neighborhood, city, state
FROM establishments
WHERE description IS NOT NULL OR postal_code IS NOT NULL OR street IS NOT NULL
   OR street_number IS NOT NULL OR address_complement IS NOT NULL
   OR neighborhood IS NOT NULL OR city IS NOT NULL OR state IS NOT NULL
ON CONFLICT (establishment_id) DO UPDATE SET
    about_us = COALESCE(establishment_profiles.about_us, EXCLUDED.about_us),
    postal_code = COALESCE(establishment_profiles.postal_code, EXCLUDED.postal_code),
    street = COALESCE(establishment_profiles.street, EXCLUDED.street),
    street_number = COALESCE(establishment_profiles.street_number, EXCLUDED.street_number),
    address_complement = COALESCE(establishment_profiles.address_complement, EXCLUDED.address_complement),
    neighborhood = COALESCE(establishment_profiles.neighborhood, EXCLUDED.neighborhood),
    city = COALESCE(establishment_profiles.city, EXCLUDED.city),
    state = COALESCE(establishment_profiles.state, EXCLUDED.state),
    updated_at = CURRENT_TIMESTAMP;

ALTER TABLE establishments
    DROP CONSTRAINT ck_establishments_postal_code,
    DROP CONSTRAINT ck_establishments_state,
    DROP COLUMN description,
    DROP COLUMN postal_code,
    DROP COLUMN street,
    DROP COLUMN street_number,
    DROP COLUMN address_complement,
    DROP COLUMN neighborhood,
    DROP COLUMN city,
    DROP COLUMN state;
