package br.com.salonhub.dto.establishment;

import br.com.salonhub.dto.catalog.CatalogProfessionalResponse;
import br.com.salonhub.dto.catalog.CatalogServiceResponse;
import br.com.salonhub.entity.Establishment;
import br.com.salonhub.entity.EstablishmentProfile;

import java.util.List;
import java.math.BigDecimal;

/** Define os dados devolvidos pela API no fluxo de estabelecimentos, sem expor as entidades persistidas. */
public record EstablishmentProfileResponse(
        Long establishmentId,
        String establishmentName,
        String aboutUs,
        String instagram,
        String whatsapp,
        String phone,
        String postalCode,
        String street,
        String streetNumber,
        String addressComplement,
        String neighborhood,
        String city,
        String state,
        boolean locationAvailable,
        BigDecimal latitude,
        BigDecimal longitude,
        boolean hasLogo,
        boolean hasCover,
        List<BusinessHourResponse> businessHours,
        List<CatalogProfessionalResponse> professionals,
        List<CatalogServiceResponse> services
) {
    public static EstablishmentProfileResponse from(
            Establishment establishment,
            EstablishmentProfile profile,
            List<BusinessHourResponse> businessHours,
            List<CatalogProfessionalResponse> professionals,
            List<CatalogServiceResponse> services) {
        return new EstablishmentProfileResponse(
                establishment.getId(),
                establishment.getName(),
                profile == null ? null : profile.getAboutUs(),
                profile == null ? null : profile.getInstagram(),
                profile == null ? null : profile.getWhatsapp(),
                profile == null ? null : profile.getPhone(),
                profile == null ? null : profile.getPostalCode(),
                profile == null ? null : profile.getStreet(),
                profile == null ? null : profile.getStreetNumber(),
                profile == null ? null : profile.getAddressComplement(),
                profile == null ? null : profile.getNeighborhood(),
                profile == null ? null : profile.getCity(),
                profile == null ? null : profile.getState(),
                profile != null && profile.hasLocation(),
                profile == null ? null : profile.getLatitude(),
                profile == null ? null : profile.getLongitude(),
                profile != null && profile.getLogo() != null,
                profile != null && profile.getCover() != null,
                businessHours,
                professionals,
                services);
    }
}
