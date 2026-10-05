-- Lembrete por e-mail ao cliente nas 24 h antes do horário: guarda quando foi enviado para não
-- repetir. Remarcar o agendamento zera o campo.
ALTER TABLE appointments ADD COLUMN reminder_sent_at TIMESTAMP WITHOUT TIME ZONE;

CREATE INDEX idx_appointments_pending_reminders
    ON appointments (starts_at)
    WHERE status = 'CONFIRMED' AND reminder_sent_at IS NULL;
