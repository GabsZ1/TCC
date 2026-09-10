package br.com.salonhub.dto.catalog;

import br.com.salonhub.entity.User;

public record CatalogProfessionalResponse(Long id, String name) {
    public static CatalogProfessionalResponse fromEntity(User user) {
        return new CatalogProfessionalResponse(user.getId(), user.getName());
    }
}
