package br.com.salonhub.dto.employee;

import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;

public record EmployeeCreationResponse(
        Long id,
        String name,
        String email,
        UserRole role,
        Long establishmentId,
        String establishmentName,
        boolean active
) {
    public static EmployeeCreationResponse fromEntity(User user) {
        return new EmployeeCreationResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getEstablishment().getId(),
                user.getEstablishment().getName(),
                user.isActive());
    }
}
