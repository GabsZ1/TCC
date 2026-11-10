package br.com.salonhub.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Define os dados recebidos pela API no fluxo de autenticação e suas validações de entrada. */
public record PasswordResetRequest(
        @NotBlank(message = "O link de recuperação é inválido")
        String token,

        @NotBlank(message = "Informe a nova senha")
        @Size(min = 8, max = 72, message = "A senha não atende aos critérios mínimos de segurança (8 a 72 caracteres)")
        String newPassword,

        @NotBlank(message = "Confirme a nova senha")
        String newPasswordConfirmation
) {
}
