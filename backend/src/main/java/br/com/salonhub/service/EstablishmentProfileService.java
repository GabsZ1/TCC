package br.com.salonhub.service;

import br.com.salonhub.dto.catalog.CatalogProfessionalResponse;
import br.com.salonhub.dto.catalog.CatalogServiceResponse;
import br.com.salonhub.dto.establishment.EstablishmentProfileRequest;
import br.com.salonhub.dto.establishment.EstablishmentProfileResponse;
import br.com.salonhub.dto.establishment.BusinessHourResponse;
import br.com.salonhub.entity.Establishment;
import br.com.salonhub.entity.EstablishmentProfile;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.service.geocoding.GeocodingService;
import br.com.salonhub.service.geocoding.BrasilApiCepGeocodingService;
import br.com.salonhub.exception.ConflictException;
import br.com.salonhub.exception.ResourceNotFoundException;
import br.com.salonhub.repository.EstablishmentProfileRepository;
import br.com.salonhub.repository.EstablishmentBusinessHourRepository;
import br.com.salonhub.repository.EstablishmentRepository;
import br.com.salonhub.repository.EstablishmentServiceRepository;
import br.com.salonhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class EstablishmentProfileService {

    private final EstablishmentProfileRepository profileRepository;
    private final EstablishmentBusinessHourRepository businessHourRepository;
    private final EstablishmentRepository establishmentRepository;
    private final EstablishmentServiceRepository serviceRepository;
    private final UserRepository userRepository;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final GeocodingService geocodingService;
    private final BrasilApiCepGeocodingService brasilApiCepGeocodingService;

    @Transactional(readOnly = true)
    public EstablishmentProfileResponse findPublic(Long establishmentId) {
        Establishment establishment = establishmentRepository.findById(establishmentId)
                .filter(Establishment::acceptsNewAppointments)
                .orElseThrow(() -> new ResourceNotFoundException("Estabelecimento não encontrado."));
        return response(establishment, profileRepository.findByEstablishmentId(establishmentId).orElse(null));
    }

    @Transactional(readOnly = true)
    public EstablishmentProfileResponse findMine(Long administratorId) {
        Establishment establishment = tenantAuthorizationService
                .requireAdministrator(administratorId).getEstablishment();
        return response(establishment, profileRepository.findByEstablishmentId(establishment.getId()).orElse(null));
    }

    @Transactional
    public EstablishmentProfileResponse updateMine(Long administratorId, EstablishmentProfileRequest request) {
        Establishment establishment = tenantAuthorizationService
                .requireAdministrator(administratorId).getEstablishment();
        if (!establishment.isActive()) {
            throw new ConflictException("Não é possível editar o perfil de um estabelecimento inativo.");
        }
        EstablishmentProfile profile = profileRepository.findByEstablishmentId(establishment.getId())
                .orElseGet(() -> EstablishmentProfile.builder().establishment(establishment).build());
        String previousAddress = addressOf(profile);
        profile.update(
                normalize(request.aboutUs()),
                normalizeInstagram(request.instagram()),
                normalizePhone(request.whatsapp()),
                normalizePhone(request.phone()),
                digits(request.postalCode()),
                normalize(request.street()),
                normalize(request.streetNumber()),
                normalize(request.addressComplement()),
                normalize(request.neighborhood()),
                normalize(request.city()),
                normalizeState(request.state()));
        String currentAddress = addressOf(profile);
        if (!currentAddress.isBlank() && (!previousAddress.equals(currentAddress) || !profile.hasLocation())) {
            var coordinates = geocodingService.geocode(currentAddress);
            if (coordinates.isEmpty()) {
                coordinates = brasilApiCepGeocodingService.geocodePostalCode(profile.getPostalCode());
            }
            coordinates.ifPresent(c -> profile.updateLocation(c.latitude(), c.longitude()));
        }
        profileRepository.save(profile);
        return response(establishment, profile);
    }

    private EstablishmentProfileResponse response(Establishment establishment, EstablishmentProfile profile) {
        var businessHours = businessHourRepository
                .findByEstablishmentIdOrderByDayOfWeek(establishment.getId())
                .stream().map(BusinessHourResponse::fromEntity).toList();
        var professionals = userRepository
                .findByEstablishmentIdAndRoleAndActiveTrue(establishment.getId(), UserRole.PROFESSIONAL)
                .stream().map(CatalogProfessionalResponse::fromEntity).toList();
        var services = serviceRepository.findByEstablishmentIdAndActiveTrue(establishment.getId())
                .stream().map(CatalogServiceResponse::fromEntity).toList();
        return EstablishmentProfileResponse.from(establishment, profile, businessHours, professionals, services);
    }

    private String addressOf(EstablishmentProfile profile) {
        if (profile.getStreet() == null || profile.getStreetNumber() == null
                || profile.getCity() == null || profile.getState() == null) return "";
        return java.util.stream.Stream.of(profile.getStreet(), profile.getStreetNumber(), profile.getNeighborhood(),
                profile.getCity(), profile.getState(), profile.getPostalCode(), "Brasil")
                .filter(v -> v != null && !v.isBlank()).collect(java.util.stream.Collectors.joining(", "));
    }
    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeInstagram(String value) {
        String normalized = normalize(value);
        if (normalized == null) return null;
        normalized = normalized.replaceFirst("(?i)^https?://(www\\.)?instagram\\.com/", "");
        normalized = normalized.replaceFirst("^@", "").replaceAll("/+$", "");
        return normalized.isBlank() ? null : normalized;
    }

    private String normalizePhone(String value) {
        String normalized = normalize(value);
        if (normalized == null) return null;
        String phone = normalized.replaceAll("[^0-9+]", "");
        return phone.isBlank() ? null : phone;
    }

    private String digits(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.replaceAll("\\D", "");
    }

    private String normalizeState(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }
}
