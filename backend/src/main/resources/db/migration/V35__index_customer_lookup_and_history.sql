CREATE INDEX idx_customers_establishment_name_id ON customers (establishment_id, name, id);
CREATE INDEX idx_customers_establishment_email ON customers (establishment_id, email);
CREATE INDEX idx_appointments_customer_starts_id ON appointments (customer_id, starts_at DESC, id DESC);
