CREATE TABLE customer_duplicate_ignores (
    establishment_id BIGINT NOT NULL REFERENCES establishments(id),
    customer_id_a BIGINT NOT NULL,
    customer_id_b BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_customer_duplicate_ignores PRIMARY KEY (establishment_id, customer_id_a, customer_id_b),
    CONSTRAINT ck_customer_duplicate_ignores_order CHECK (customer_id_a < customer_id_b),
    CONSTRAINT fk_duplicate_a_tenant FOREIGN KEY (customer_id_a, establishment_id)
        REFERENCES customers(id, establishment_id) ON DELETE CASCADE,
    CONSTRAINT fk_duplicate_b_tenant FOREIGN KEY (customer_id_b, establishment_id)
        REFERENCES customers(id, establishment_id) ON DELETE CASCADE
);
