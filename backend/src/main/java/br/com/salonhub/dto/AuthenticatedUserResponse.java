package br.com.salonhub.dto;

import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;
import com.fasterxml.jackson.annotation.JsonProperty;

public record AuthenticatedUserResponse(
        Long id,
        @JsonProperty("nome") String name,
        String email,
        @JsonProperty("perfil") UserRole role,
        @JsonProperty("estabelecimentoId") Long establishmentId,
        @JsonProperty("nomeEstabelecimento") String establishmentName
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
