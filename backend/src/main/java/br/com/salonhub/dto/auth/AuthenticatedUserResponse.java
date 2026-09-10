package br.com.salonhub.dto.auth;

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
        Long establishmentId = user.getEstablishment() == null ? null : user.getEstablishment().getId();
        String establishmentName = user.getEstablishment() == null ? null : user.getEstablishment().getName();
        return new AuthenticatedUserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                establishmentId,
                establishmentName
        );
    }
}
