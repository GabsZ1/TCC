package br.com.salonhub.dto.employee;

import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;

import java.time.Instant;

public record EmployeeCreationResponse(
        Long id,
        String name,
        String email,
        UserRole role,
        Long establishmentId,
        String establishmentName,
        boolean active,
        String firstAccessToken,
        Instant firstAccessExpiresAt
) {
    public static EmployeeCreationResponse fromEntity(
            User user, String firstAccessToken, Instant firstAccessExpiresAt) {
        return new EmployeeCreationResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getEstablishment().getId(),
                user.getEstablishment().getName(),
                user.isActive(),
                firstAccessToken,
                firstAccessExpiresAt);
    }
}
