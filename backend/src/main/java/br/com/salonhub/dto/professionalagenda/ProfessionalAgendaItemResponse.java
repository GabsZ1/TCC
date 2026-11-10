package br.com.salonhub.dto.professionalagenda;

import br.com.salonhub.entity.Appointment;
import br.com.salonhub.entity.ProfessionalCalendarEvent;

import java.time.LocalDateTime;
import java.math.BigDecimal;

/** Define os dados devolvidos pela API no fluxo de agenda do profissional, sem expor as entidades persistidas. */
public record ProfessionalAgendaItemResponse(
        String type,
        Long id,
        String title,
        String description,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        boolean allDay,
        String status,
        String clientName,
        String clientEmail,
        String serviceName,
        Integer durationMinutes,
        BigDecimal agreedPrice,
        String cancellationReason,
        String invalidationReason) {

    public static ProfessionalAgendaItemResponse fromAppointment(Appointment appointment) {
        return new ProfessionalAgendaItemResponse(
                "APPOINTMENT", appointment.getId(), appointment.getServiceName(), null,
                appointment.getStartsAt(), appointment.getEndsAt(), false,
                appointment.getStatus().name(), appointment.getCustomer().getName(), appointment.getCustomer().getEmail(),
                appointment.getServiceName(), appointment.getDurationMinutes(), appointment.getAgreedPrice(),
                appointment.getCancellationReason(), appointment.getInvalidationReason());
    }

    public static ProfessionalAgendaItemResponse fromEvent(ProfessionalCalendarEvent event) {
        return new ProfessionalAgendaItemResponse(
                "EVENT", event.getId(), event.getTitle(), event.getDescription(),
                event.getStartsAt(), event.getEndsAt(), event.isAllDay(),
                null, null, null, null, null, null, null, null);
    }
}
