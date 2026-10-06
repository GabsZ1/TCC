package br.com.salonhub.service.appointment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Procura lembretes a enviar a cada {@code app.notifications.reminders.interval-ms} (padrão 10 min).
 * Desligado com {@code app.notifications.reminders.enabled=false} (testes e seed).
 */
@Component
@ConditionalOnProperty(name = "app.notifications.reminders.enabled", havingValue = "true", matchIfMissing = true)
public class AppointmentReminderJob {

    private static final Logger log = LoggerFactory.getLogger(AppointmentReminderJob.class);

    private final AppointmentReminderService reminderService;

    public AppointmentReminderJob(AppointmentReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @Scheduled(initialDelayString = "${app.notifications.reminders.initial-delay-ms:60000}",
            fixedDelayString = "${app.notifications.reminders.interval-ms:600000}")
    public void sendReminders() {
        try {
            int sent = reminderService.sendDueReminders();
            if (sent > 0) {
                log.info("{} lembrete(s) de agendamento processado(s).", sent);
            }
        } catch (RuntimeException exception) {
            log.error("Falha ao processar os lembretes de agendamento.", exception);
        }
    }
}
