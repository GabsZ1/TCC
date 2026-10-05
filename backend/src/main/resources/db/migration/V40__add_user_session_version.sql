-- Versão da sessão: vai no JWT e é conferida a cada requisição. Trocar ou redefinir a senha
-- e excluir a conta incrementam o valor, e todos os tokens emitidos antes deixam de valer.
ALTER TABLE users ADD COLUMN session_version INTEGER NOT NULL DEFAULT 0;
