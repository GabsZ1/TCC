package br.com.salonhub.dto.professionalagenda;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record ProfessionalHourResponse(
        DayOfWeek dayOfWeek,
        boolean closed,
        LocalTime openingTime,
        LocalTime closingTime,
        boolean inheritedFromEstablishment) {
}
