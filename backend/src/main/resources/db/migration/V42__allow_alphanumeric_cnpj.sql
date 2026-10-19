ALTER TABLE establishments DROP CONSTRAINT ck_establishments_cnpj;
ALTER TABLE establishments ADD CONSTRAINT ck_establishments_cnpj
    CHECK (cnpj ~ '^[A-Z0-9]{12}[0-9]{2}$');
