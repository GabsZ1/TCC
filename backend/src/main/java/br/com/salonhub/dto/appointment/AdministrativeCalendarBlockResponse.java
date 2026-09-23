package br.com.salonhub.dto.appointment;

import br.com.salonhub.entity.ProfessionalCalendarEvent;

import java.time.LocalDateTime;

public record AdministrativeCalendarBlockResponse(
        Long id,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        boolean allDay) {

    public static AdministrativeCalendarBlockResponse fromEntity(ProfessionalCalendarEvent event) {
        return new AdministrativeCalendarBlockResponse(
                event.getId(), event.getStartsAt(), event.getEndsAt(), event.isAllDay());
    }
}
