CREATE TABLE establishment_profiles (
    id BIGSERIAL PRIMARY KEY,
    establishment_id BIGINT NOT NULL,
    about_us TEXT,
    instagram VARCHAR(100),
    whatsapp VARCHAR(20),
    phone VARCHAR(20),
    postal_code VARCHAR(8),
    street VARCHAR(150),
    street_number VARCHAR(20),
    address_complement VARCHAR(100),
    neighborhood VARCHAR(100),
    city VARCHAR(100),
    state CHAR(2),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_establishment_profiles_establishment UNIQUE (establishment_id),
    CONSTRAINT fk_establishment_profiles_establishment FOREIGN KEY (establishment_id)
        REFERENCES establishments (id) ON DELETE CASCADE,
    CONSTRAINT ck_establishment_profiles_postal_code CHECK (
        postal_code IS NULL OR postal_code ~ '^[0-9]{8}$'
    ),
    CONSTRAINT ck_establishment_profiles_state CHECK (
        state IS NULL OR state IN (
            'AC', 'AL', 'AP', 'AM', 'BA', 'CE', 'DF', 'ES', 'GO',
            'MA', 'MT', 'MS', 'MG', 'PA', 'PB', 'PR', 'PE', 'PI',
            'RJ', 'RN', 'RS', 'RO', 'RR', 'SC', 'SP', 'SE', 'TO'
        )
    )
);

CREATE INDEX idx_establishment_profiles_establishment_id
    ON establishment_profiles (establishment_id);

