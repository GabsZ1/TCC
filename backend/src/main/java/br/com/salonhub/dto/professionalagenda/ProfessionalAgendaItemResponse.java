package br.com.salonhub.dto.professionalagenda;

import br.com.salonhub.entity.Appointment;
import br.com.salonhub.entity.ProfessionalCalendarEvent;

import java.time.LocalDateTime;

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
        String serviceName) {

    public static ProfessionalAgendaItemResponse fromAppointment(Appointment appointment) {
        return new ProfessionalAgendaItemResponse(
                "APPOINTMENT", appointment.getId(), appointment.getServiceName(), null,
                appointment.getStartsAt(), appointment.getEndsAt(), false,
                appointment.getStatus().name(), appointment.getClient().getName(), appointment.getServiceName());
    }

    public static ProfessionalAgendaItemResponse fromEvent(ProfessionalCalendarEvent event) {
        return new ProfessionalAgendaItemResponse(
                "EVENT", event.getId(), event.getTitle(), event.getDescription(),
                event.getStartsAt(), event.getEndsAt(), event.isAllDay(),
                null, null, null);
    }
}
