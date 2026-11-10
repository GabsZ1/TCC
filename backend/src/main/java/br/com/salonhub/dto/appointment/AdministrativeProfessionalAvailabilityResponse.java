package br.com.salonhub.dto.appointment;

import java.time.LocalTime;
import java.util.List;

/** Define os dados devolvidos pela API no fluxo de agendamentos, sem expor as entidades persistidas. */
public record AdministrativeProfessionalAvailabilityResponse(
        Long professionalId,
        String professionalName,
        boolean working,
        LocalTime openingTime,
        LocalTime closingTime,
        List<AdministrativeCalendarBlockResponse> blocks) {
}
