package br.com.salonhub.dto.client;

import br.com.salonhub.entity.User;

/** US 2.25 — dados do perfil do cliente (a senha nunca é devolvida). */
public record ClientProfileResponse(Long id, String name, String email, String phone) {

    public static ClientProfileResponse fromEntity(User user) {
        return new ClientProfileResponse(user.getId(), user.getName(), user.getEmail(), user.getPhone());
    }
}
