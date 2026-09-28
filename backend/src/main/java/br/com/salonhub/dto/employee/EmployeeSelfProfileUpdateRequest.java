package br.com.salonhub.dto.employee;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Perfil do próprio funcionário: e-mail de acesso, ocupação e contatos.
 * Trocar o e-mail exige a senha atual ({@code currentPassword}).
 */
public record EmployeeSelfProfileUpdateRequest(
        @NotBlank(message = "O e-mail é obrigatório")
        @Email(message = "Email inválido")
        @Size(max = 150, message = "O e-mail deve ter no máximo 150 caracteres")
        String email,

        @NotBlank(message = "A ocupação é obrigatória")
        @Size(max = 80, message = "A ocupação deve ter no máximo 80 caracteres")
        String occupation,

        @Size(max = 20, message = "Informe um telefone válido!")
        String phone,

        @Size(max = 31, message = "O Instagram deve ter no máximo 30 caracteres")
        String instagram,

        @Size(max = 255, message = "O endereço deve ter no máximo 255 caracteres")
        String address,

        String currentPassword
) {}
