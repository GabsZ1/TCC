ALTER TABLE salon_services
    ADD CONSTRAINT ck_salon_services_price CHECK (price > 0),
    ADD CONSTRAINT ck_salon_services_commission_percentage CHECK (commission_percentage BETWEEN 0 AND 100);
