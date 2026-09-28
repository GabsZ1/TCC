-- US 2.15 — "Meus atendimentos realizados": o profissional consulta seus
-- atendimentos concluídos por período de conclusão. Índice dedicado para que a
-- consulta continue rápida com o histórico crescendo.
CREATE INDEX idx_appointments_professional_status_completed
    ON appointments (professional_id, status, completed_at DESC);
