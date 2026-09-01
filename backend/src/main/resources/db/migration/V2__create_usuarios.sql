CREATE TABLE usuarios (
    id BIGSERIAL PRIMARY KEY,
    estabelecimento_id BIGINT NOT NULL,
    nome VARCHAR(150) NOT NULL,
    email VARCHAR(150) NOT NULL,
    senha VARCHAR(255) NOT NULL,
    perfil VARCHAR(20) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_usuarios_estabelecimento
        FOREIGN KEY (estabelecimento_id)
        REFERENCES estabelecimentos (id),
    CONSTRAINT uk_usuarios_email UNIQUE (email),
    CONSTRAINT ck_usuarios_perfil CHECK (perfil IN ('ADMIN', 'PROFISSIONAL'))
);

CREATE INDEX idx_usuarios_estabelecimento_id
    ON usuarios (estabelecimento_id);
