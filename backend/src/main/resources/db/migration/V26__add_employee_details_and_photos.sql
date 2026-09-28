-- US 2.1 / 2.2 — dados cadastrais do funcionário.
-- CPF e ocupação são obrigatórios para novos cadastros (validados na aplicação);
-- funcionários antigos ficam com NULL até a próxima edição.
ALTER TABLE users
    ADD COLUMN cpf VARCHAR(11),
    ADD COLUMN occupation VARCHAR(80),
    ADD COLUMN instagram VARCHAR(30),
    ADD COLUMN address VARCHAR(255),
    ADD COLUMN hire_date DATE;

ALTER TABLE users
    ADD CONSTRAINT uk_users_cpf UNIQUE (cpf),
    ADD CONSTRAINT ck_users_cpf_digits CHECK (cpf IS NULL OR cpf ~ '^[0-9]{11}$');

-- Foto do funcionário (opcional). Guardada no banco para não depender de
-- armazenamento externo; limitada a 2 MB pela aplicação.
CREATE TABLE user_photos (
    user_id BIGINT PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    content_type VARCHAR(30) NOT NULL,
    data BYTEA NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
