package br.com.salonhub.dto.appointment;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * Remarcação pelo administrador. serviceId é opcional: vazio ou igual ao atual mantém o serviço
 * e o preço combinado com o cliente; um serviço diferente atualiza o preço, a comissão e a duração.
 */
public record AppointmentRescheduleRequest(
        Long serviceId,

        @NotNull(message = "O profissional é obrigatório")
        Long professionalId,

        @NotNull(message = "A data e o horário são obrigatórios")
        @Future(message = "O novo horário deve estar no futuro")
        LocalDateTime startsAt
) {
}
