package br.com.salonhub.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Define os dados recebidos pela API no fluxo de autenticação e suas validações de entrada. */
public record FirstAccessRequest(
        @NotBlank(message = "O token de primeiro acesso é obrigatório")
        String token,

        @NotBlank(message = "A senha é obrigatória")
        @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres")
        String password
) {
}
