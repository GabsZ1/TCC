package br.com.salonhub.dto.appointment;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Define os dados devolvidos pela API no fluxo de agendamentos, sem expor as entidades persistidas. */
public record AvailabilityResponse(
        LocalDate date,
        Integer durationMinutes,
        List<LocalTime> availableTimes
) {
}
