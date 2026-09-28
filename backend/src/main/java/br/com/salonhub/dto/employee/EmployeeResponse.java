package br.com.salonhub.dto.employee;

import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;

import java.time.LocalDate;

/** Funcionário na visão do administrador (listagem, detalhes e retorno das ações). */
public record EmployeeResponse(
        Long id,
        String name,
        String email,
        UserRole role,
        Long establishmentId,
        String establishmentName,
        boolean active,
        boolean passwordInitialized,
        String occupation,
        String cpf,
        String phone,
        String instagram,
        String address,
        LocalDate hireDate,
        boolean hasPhoto
) {
    public static EmployeeResponse fromEntity(User user, boolean hasPhoto) {
        return new EmployeeResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getEstablishment() == null ? null : user.getEstablishment().getId(),
                user.getEstablishment() == null ? null : user.getEstablishment().getName(),
                user.isActive(),
                user.isPasswordInitialized(),
                user.getOccupation(),
                user.getCpf(),
                user.getPhone(),
                user.getInstagram(),
                user.getAddress(),
                user.getHireDate(),
                hasPhoto);
    }
}
