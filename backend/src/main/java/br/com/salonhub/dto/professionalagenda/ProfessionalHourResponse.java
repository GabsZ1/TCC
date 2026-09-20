package br.com.salonhub.dto.professionalagenda;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * openingTime/closingTime/closed refletem o horário efetivo do dia (próprio, quando existir,
 * ou herdado do estabelecimento). establishmentOpeningTime/establishmentClosingTime/
 * establishmentClosed trazem separadamente o horário atual do estabelecimento, para a
 * interface conseguir validar limites e oferecer "usar horário do salão" sem precisar de
 * outra chamada.
 */
public record ProfessionalHourResponse(
        DayOfWeek dayOfWeek,
        boolean closed,
        LocalTime openingTime,
        LocalTime closingTime,
        boolean inheritedFromEstablishment,
        boolean establishmentClosed,
        LocalTime establishmentOpeningTime,
        LocalTime establishmentClosingTime) {
}
