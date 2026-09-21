package br.com.salonhub.dto.appointment;

import java.time.LocalTime;
import java.util.List;

public record AdministrativeProfessionalAvailabilityResponse(
        Long professionalId,
        String professionalName,
        boolean working,
        LocalTime openingTime,
        LocalTime closingTime,
        List<AdministrativeCalendarBlockResponse> blocks) {
}
