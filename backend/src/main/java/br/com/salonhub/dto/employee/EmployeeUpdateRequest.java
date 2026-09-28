package br.com.salonhub.dto.employee;

import br.com.salonhub.validation.ValidCpf;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * US 2.2 — o que o administrador ainda pode editar: o nome e, somente enquanto estiverem
 * vazios (funcionários cadastrados antes desta regra), o CPF e a data de admissão.
 * Os demais dados passaram a ser do próprio funcionário.
 */
public record EmployeeUpdateRequest(
        @NotBlank(message = "O nome é obrigatório")
        @Size(max = 150, message = "O nome deve ter no máximo 150 caracteres")
        String name,

        @ValidCpf
        String cpf,

        LocalDate hireDate
) {}
