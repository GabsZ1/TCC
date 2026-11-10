package br.com.salonhub.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Define os dados recebidos pela API no fluxo de autenticação e suas validações de entrada. */
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {
}
