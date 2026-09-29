ALTER TABLE customers ADD CONSTRAINT uq_customers_id_establishment UNIQUE (id, establishment_id);
ALTER TABLE appointments DROP CONSTRAINT fk_appointments_customer;
ALTER TABLE appointments ADD CONSTRAINT fk_appointments_customer_tenant
    FOREIGN KEY (customer_id, establishment_id) REFERENCES customers(id, establishment_id);
