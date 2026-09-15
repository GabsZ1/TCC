package br.com.salonhub.dto.establishment;

import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalTime;

public record BusinessHourRequest(
        @NotNull DayOfWeek dayOfWeek,
        boolean closed,
        LocalTime openingTime,
        LocalTime closingTime
) {
}
