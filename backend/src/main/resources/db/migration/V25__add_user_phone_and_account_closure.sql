-- US 2.23 a 2.26 — Conta do cliente.
-- phone: telefone do usuário, somente dígitos (DDD + número). Obrigatório no cadastro
--        de novos clientes (validado na aplicação); contas antigas podem ficar sem.
-- deleted_at: momento em que o cliente excluiu a própria conta. A linha é mantida
--        (anonimizada) para não quebrar o histórico de agendamentos do estabelecimento.
ALTER TABLE users
    ADD COLUMN phone VARCHAR(20),
    ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE users
    ADD CONSTRAINT ck_users_phone_digits CHECK (phone IS NULL OR phone ~ '^[0-9]{10,11}$');
