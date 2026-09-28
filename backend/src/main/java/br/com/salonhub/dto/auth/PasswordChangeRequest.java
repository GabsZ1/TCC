package br.com.salonhub.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Alteração de senha do próprio usuário autenticado (US 2.19).
 * O tamanho mínimo segue a mesma política do cadastro e do primeiro acesso.
 */
public record PasswordChangeRequest(
        @NotBlank(message = "Informe a senha atual")
        String currentPassword,

        @NotBlank(message = "Informe a nova senha")
        @Size(min = 8, max = 72, message = "A senha não atende aos critérios mínimos de segurança (8 a 72 caracteres)")
        String newPassword,

        @NotBlank(message = "Confirme a nova senha")
        String newPasswordConfirmation
) {
}
