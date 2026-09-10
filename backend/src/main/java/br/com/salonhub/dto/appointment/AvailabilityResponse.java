package br.com.salonhub.dto.appointment;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record AvailabilityResponse(
        LocalDate date,
        Integer durationMinutes,
        List<LocalTime> availableTimes
) {
}
