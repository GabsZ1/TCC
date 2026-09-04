ALTER TABLE estabelecimentos RENAME TO establishments;
ALTER SEQUENCE estabelecimentos_id_seq RENAME TO establishments_id_seq;
ALTER TABLE establishments RENAME CONSTRAINT estabelecimentos_pkey TO establishments_pkey;

ALTER TABLE establishments RENAME COLUMN nome TO name;
ALTER TABLE establishments RENAME COLUMN telefone TO phone;
ALTER TABLE establishments RENAME COLUMN descricao TO description;
ALTER TABLE establishments RENAME COLUMN cep TO postal_code;
ALTER TABLE establishments RENAME COLUMN logradouro TO street;
ALTER TABLE establishments RENAME COLUMN numero TO street_number;
ALTER TABLE establishments RENAME COLUMN complemento TO address_complement;
ALTER TABLE establishments RENAME COLUMN bairro TO neighborhood;
ALTER TABLE establishments RENAME COLUMN cidade TO city;
ALTER TABLE establishments RENAME COLUMN estado TO state;
ALTER TABLE establishments RENAME COLUMN ativo TO active;
ALTER TABLE establishments RENAME COLUMN criado_em TO created_at;
ALTER TABLE establishments RENAME COLUMN atualizado_em TO updated_at;

ALTER TABLE establishments RENAME CONSTRAINT uk_estabelecimentos_cnpj TO uk_establishments_cnpj;
ALTER TABLE establishments RENAME CONSTRAINT ck_estabelecimentos_cnpj TO ck_establishments_cnpj;
ALTER TABLE establishments RENAME CONSTRAINT ck_estabelecimentos_cep TO ck_establishments_postal_code;
ALTER TABLE establishments RENAME CONSTRAINT ck_estabelecimentos_estado TO ck_establishments_state;

ALTER TABLE usuarios RENAME TO users;
ALTER SEQUENCE usuarios_id_seq RENAME TO users_id_seq;
ALTER TABLE users RENAME CONSTRAINT usuarios_pkey TO users_pkey;

ALTER TABLE users RENAME COLUMN estabelecimento_id TO establishment_id;
ALTER TABLE users RENAME COLUMN nome TO name;
ALTER TABLE users RENAME COLUMN senha TO password;
ALTER TABLE users RENAME COLUMN perfil TO role;
ALTER TABLE users RENAME COLUMN ativo TO active;
ALTER TABLE users RENAME COLUMN criado_em TO created_at;
ALTER TABLE users RENAME COLUMN atualizado_em TO updated_at;

ALTER TABLE users RENAME CONSTRAINT fk_usuarios_estabelecimento TO fk_users_establishment;
ALTER TABLE users RENAME CONSTRAINT uk_usuarios_email TO uk_users_email;
ALTER TABLE users RENAME CONSTRAINT ck_usuarios_perfil TO ck_users_role;
ALTER INDEX idx_usuarios_estabelecimento_id RENAME TO idx_users_establishment_id;

ALTER TABLE servicos RENAME TO salon_services;
ALTER SEQUENCE servicos_id_seq RENAME TO salon_services_id_seq;
ALTER TABLE salon_services RENAME CONSTRAINT servicos_pkey TO salon_services_pkey;

ALTER TABLE salon_services RENAME COLUMN estabelecimento_id TO establishment_id;
ALTER TABLE salon_services RENAME COLUMN nome TO name;
ALTER TABLE salon_services RENAME COLUMN descricao TO description;
ALTER TABLE salon_services RENAME COLUMN preco TO price;
ALTER TABLE salon_services RENAME COLUMN comissao_porcentagem TO commission_percentage;
ALTER TABLE salon_services RENAME COLUMN ativo TO active;

ALTER TABLE salon_services RENAME CONSTRAINT fk_servicos_estabelecimento TO fk_salon_services_establishment;
