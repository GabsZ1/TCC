package br.com.salonhub.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Define os dados recebidos pela API no fluxo de autenticação e suas validações de entrada. */
public record PasswordRecoveryRequest(
        @NotBlank(message = "Informe o e-mail")
        @Email(message = "Informe um e-mail válido")
        @Size(max = 150)
        String email
) {
}
