-- US 2.20 — Recuperar senha.
-- Guarda apenas o hash SHA-256 do token enviado por e-mail: quem tiver acesso ao
-- banco não consegue reconstruir o link. Cada token é de uso único (used_at) e
-- expira (expires_at). Tokens substituídos por um pedido mais novo também recebem
-- used_at, para que só o último link enviado funcione.
CREATE TABLE password_reset_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_password_reset_tokens_hash UNIQUE (token_hash),
    CONSTRAINT ck_password_reset_tokens_expiration CHECK (expires_at > created_at)
);

CREATE INDEX idx_password_reset_tokens_user_created
    ON password_reset_tokens (user_id, created_at DESC);
