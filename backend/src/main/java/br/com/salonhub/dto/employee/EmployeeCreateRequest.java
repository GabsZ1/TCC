package br.com.salonhub.dto.employee;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmployeeCreateRequest(
        @NotBlank(message = "O nome é obrigatório")
        @Size(max = 150, message = "O nome deve ter no máximo 150 caracteres")
        String name,

        @NotBlank(message = "O e-mail é obrigatório")
        @Email(message = "E-mail em formato inválido")
        @Size(max = 150, message = "O e-mail deve ter no máximo 150 caracteres")
        String email,

        @NotBlank(message = "A senha provisória é obrigatória")
        @Size(min = 8, message = "A senha deve ter no mínimo 8 caracteres")
        String password
) {}
