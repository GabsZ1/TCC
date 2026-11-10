package br.com.salonhub.dto.appointment;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/** Define os dados recebidos pela API no fluxo de agendamentos e suas validações de entrada. */
public record AppointmentCreateRequest(
        @NotNull(message = "O serviço é obrigatório")
        Long serviceId,

        @NotNull(message = "O funcionário é obrigatório")
        Long professionalId,

        @NotNull(message = "A data e o horário são obrigatórios")
        @Future(message = "O agendamento deve estar no futuro")
        LocalDateTime startsAt
) {
}
