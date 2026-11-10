package br.com.salonhub.dto.appointment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Define os dados recebidos pela API no fluxo de agendamentos e suas validações de entrada. */
public record AppointmentInvalidationRequest(
        @NotBlank(message = "O motivo da invalidação é obrigatório")
        @Size(max = 500, message = "O motivo deve ter no máximo 500 caracteres")
        String reason
) {
}
