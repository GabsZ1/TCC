package br.com.salonhub.dto;

import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;

public record AuthenticatedUserResponse(
        Long id,
        String name,
        String email,
        UserRole role,
        Long establishmentId,
        String establishmentName
) {
    public static AuthenticatedUserResponse fromEntity(User user) {
        return new AuthenticatedUserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getEstablishment().getId(),
                user.getEstablishment().getName()
        );
    }
}
