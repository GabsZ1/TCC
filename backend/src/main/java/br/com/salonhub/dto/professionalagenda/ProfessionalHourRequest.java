package br.com.salonhub.dto.professionalagenda;

import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record ProfessionalHourRequest(
        @NotNull DayOfWeek dayOfWeek,
        boolean closed,
        LocalTime openingTime,
        LocalTime closingTime) {
}
