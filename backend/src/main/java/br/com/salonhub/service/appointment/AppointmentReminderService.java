package br.com.salonhub.service.appointment;

import br.com.salonhub.entity.Appointment;
import br.com.salonhub.entity.AppointmentStatus;
import br.com.salonhub.notification.AppointmentNotifier;
import br.com.salonhub.repository.AppointmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Lembrete por e-mail para o cliente nas 24 horas antes do horário.
 * <ul>
 *   <li>Cada agendamento recebe no máximo um lembrete ({@code reminder_sent_at}); remarcar zera a marcação.</li>
 *   <li>Agendamentos marcados com menos de 24 horas de antecedência não recebem lembrete: o cliente
 *       acabou de receber a confirmação.</li>
 *   <li>Só agendamentos confirmados de estabelecimentos ativos.</li>
 * </ul>
 */
@Service
public class AppointmentReminderService {

    static final Duration REMINDER_WINDOW = Duration.ofHours(24);

    private final AppointmentRepository appointmentRepository;
    private final AppointmentNotifier notifier;
    private final Clock clock;

    public AppointmentReminderService(AppointmentRepository appointmentRepository, AppointmentNotifier notifier,
                                      Clock clock) {
        this.appointmentRepository = appointmentRepository;
        this.notifier = notifier;
        this.clock = clock;
    }

    /** Envia os lembretes que estão no prazo e devolve quantos agendamentos foram processados. */
    @Transactional
    public int sendDueReminders() {
        LocalDateTime now = LocalDateTime.now(clock);
        int processed = 0;
        for (Appointment appointment : appointmentRepository.findPendingReminders(
                AppointmentStatus.CONFIRMED, now, now.plus(REMINDER_WINDOW))) {
            LocalDateTime bookedAt = appointment.getCreatedAt()
                    .atZoneSameInstant(clock.getZone()).toLocalDateTime();
            if (bookedAt.isAfter(appointment.getStartsAt().minus(REMINDER_WINDOW))) {
                continue;
            }
            notifier.reminder(appointment);
            appointment.markReminderSent(now);
            processed++;
        }
        return processed;
    }
}
