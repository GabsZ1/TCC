package br.com.salonhub.dto.employee;

import br.com.salonhub.validation.ValidCpf;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * US 2.1 — cadastro feito pelo administrador. CPF e data de admissão ficam travados
 * depois de gravados; os dados de contato (telefone, Instagram, endereço, foto) são
 * administrados pelo próprio funcionário no perfil dele.
 */
public record EmployeeCreateRequest(
        @NotBlank(message = "O nome é obrigatório")
        @Size(max = 150, message = "O nome deve ter no máximo 150 caracteres")
        String name,

        @NotBlank(message = "O e-mail é obrigatório")
        @Email(message = "Email inválido")
        @Size(max = 150, message = "O e-mail deve ter no máximo 150 caracteres")
        String email,

        @NotBlank(message = "A ocupação é obrigatória")
        @Size(max = 80, message = "A ocupação deve ter no máximo 80 caracteres")
        String occupation,

        @NotBlank(message = "O CPF é obrigatório")
        @ValidCpf
        String cpf,

        @NotNull(message = "A data de admissão é obrigatória")
        LocalDate hireDate
) {}
