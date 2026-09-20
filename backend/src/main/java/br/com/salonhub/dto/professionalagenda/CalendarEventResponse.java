package br.com.salonhub.dto.professionalagenda;

import br.com.salonhub.entity.ProfessionalCalendarEvent;

import java.time.LocalDateTime;

public record CalendarEventResponse(
        Long id,
        String title,
        String description,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        boolean allDay) {

    public static CalendarEventResponse fromEntity(ProfessionalCalendarEvent event) {
        return new CalendarEventResponse(event.getId(), event.getTitle(), event.getDescription(),
                event.getStartsAt(), event.getEndsAt(), event.isAllDay());
    }
}
