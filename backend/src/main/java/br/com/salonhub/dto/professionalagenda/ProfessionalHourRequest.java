package br.com.salonhub.dto.professionalagenda;

import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * useEstablishmentHours = true significa "herdar o horário do estabelecimento neste dia":
 * qualquer override pessoal existente é removido e closed/openingTime/closingTime são
 * ignorados. useEstablishmentHours = false significa que o profissional está personalizando
 * o dia com os valores de closed/openingTime/closingTime informados.
 */
public record ProfessionalHourRequest(
        @NotNull DayOfWeek dayOfWeek,
        boolean useEstablishmentHours,
        boolean closed,
        LocalTime openingTime,
        LocalTime closingTime) {
}
