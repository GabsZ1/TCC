ALTER TABLE appointments
    ADD COLUMN completed_at TIMESTAMP WITHOUT TIME ZONE;

-- Preenche o histórico existente com uma aproximação razoável (o próprio
-- horário de início) para que agendamentos já concluídos antes desta
-- migration não fiquem de fora de relatórios futuros que filtrem por
-- completed_at.
UPDATE appointments
SET completed_at = starts_at
WHERE status = 'COMPLETED'
  AND completed_at IS NULL;

CREATE INDEX idx_appointments_establishment_completed_at
    ON appointments (establishment_id, status, completed_at);
