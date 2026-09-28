-- Imagens passam a ser guardadas no MinIO (armazenamento compatível com S3).
-- O PostgreSQL guarda apenas os metadados e a chave do arquivo (storage_key),
-- nunca a URL completa: assim o armazenamento pode mudar (ex.: S3) sem alterar registros.
--
-- As fotos antigas (tabela user_photos, bytes dentro do banco) eram só dados de
-- desenvolvimento e são descartadas.
DROP TABLE user_photos;

CREATE TABLE media (
    id BIGSERIAL PRIMARY KEY,
    -- estabelecimento dono da imagem (isolamento entre tenants e organização no storage)
    establishment_id BIGINT REFERENCES establishments (id),
    -- caminho do arquivo no bucket, ex.: establishments/3/media/2f1c...e9.png
    storage_key VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255),
    mime_type VARCHAR(50) NOT NULL,
    size_bytes BIGINT NOT NULL,
    uploaded_by BIGINT REFERENCES users (id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_media_storage_key UNIQUE (storage_key),
    CONSTRAINT ck_media_size CHECK (size_bytes > 0)
);

CREATE INDEX idx_media_establishment ON media (establishment_id);

-- Foto do funcionário: referência para media (uma foto por usuário).
ALTER TABLE users
    ADD COLUMN photo_id BIGINT REFERENCES media (id) ON DELETE SET NULL,
    ADD CONSTRAINT uk_users_photo UNIQUE (photo_id);
