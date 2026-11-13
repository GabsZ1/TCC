package br.com.salonhub.dto.employee;

import br.com.salonhub.validation.ValidCpf;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * O administrador edita nome e ocupação. CPF e data de admissão só podem ser
 * preenchidos enquanto estiverem vazios (funcionários antigos).
 */
public record EmployeeUpdateRequest(
        @NotBlank(message = "O nome é obrigatório")
        @Size(max = 150, message = "O nome deve ter no máximo 150 caracteres")
        String name,

        @Size(max = 80, message = "A ocupação deve ter no máximo 80 caracteres")
        String occupation,

        @ValidCpf
        String cpf,

        LocalDate hireDate
) {}
