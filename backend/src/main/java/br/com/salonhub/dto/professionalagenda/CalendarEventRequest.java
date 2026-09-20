package br.com.salonhub.dto.professionalagenda;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record CalendarEventRequest(
        @NotBlank @Size(max = 120) String title,
        @Size(max = 500) String description,
        @NotNull LocalDateTime startsAt,
        @NotNull LocalDateTime endsAt,
        boolean allDay) {
}
