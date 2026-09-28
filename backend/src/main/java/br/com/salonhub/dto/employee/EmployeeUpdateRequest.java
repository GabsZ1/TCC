package br.com.salonhub.dto.employee;

import br.com.salonhub.validation.ValidCpf;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** US 2.2 — mesmos campos do cadastro; o estabelecimento não é editável. */
public record EmployeeUpdateRequest(
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

        @Size(max = 20, message = "Informe um telefone válido!")
        String phone,

        @Size(max = 31, message = "O Instagram deve ter no máximo 30 caracteres")
        String instagram,

        @Size(max = 255, message = "O endereço deve ter no máximo 255 caracteres")
        String address,

        LocalDate hireDate
) {}
