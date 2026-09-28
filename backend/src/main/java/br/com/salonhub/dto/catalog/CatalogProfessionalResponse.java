package br.com.salonhub.dto.catalog;

import br.com.salonhub.entity.User;

/** Profissional exibido ao cliente; hasPhoto indica se há foto em /catalogo/funcionarios/{id}/foto. */
public record CatalogProfessionalResponse(Long id, String name, boolean hasPhoto) {
    public static CatalogProfessionalResponse fromEntity(User user) {
        return new CatalogProfessionalResponse(user.getId(), user.getName(), user.getPhoto() != null);
    }
}
