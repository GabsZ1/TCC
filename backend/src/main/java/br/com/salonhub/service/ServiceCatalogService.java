package br.com.salonhub.service;

import br.com.salonhub.dto.service.EstablishmentServiceRequest;
import br.com.salonhub.dto.service.EstablishmentServiceResponse;
import br.com.salonhub.entity.EstablishmentService;
import br.com.salonhub.entity.User;
import br.com.salonhub.exception.ResourceNotFoundException;
import br.com.salonhub.repository.EstablishmentServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class ServiceCatalogService {

    private final EstablishmentServiceRepository establishmentServiceRepository;
    private final TenantAuthorizationService tenantAuthorizationService;

    @Transactional
    public EstablishmentServiceResponse create(Long administratorId, EstablishmentServiceRequest request) {
        User administrator = tenantAuthorizationService.requireAdministrator(administratorId);

        EstablishmentService service = EstablishmentService.builder()
                .establishment(administrator.getEstablishment())
                .name(request.name().trim())
                .description(normalizeOptionalText(request.description()))
                .price(request.price())
                .commissionPercentage(defaultCommission(request.commissionPercentage()))
                .durationMinutes(request.durationMinutes())
                .active(true)
                .build();

        return EstablishmentServiceResponse.fromEntity(establishmentServiceRepository.save(service));
    }

    @Transactional(readOnly = true)
    public List<EstablishmentServiceResponse> findAll(Long administratorId) {
        User administrator = tenantAuthorizationService.requireAdministrator(administratorId);

        return establishmentServiceRepository.findByEstablishmentIdAndActiveTrue(administrator.getEstablishment().getId())
                .stream()
                .map(EstablishmentServiceResponse::fromEntity)
                .toList();
    }

    @Transactional
    public EstablishmentServiceResponse update(Long administratorId, Long id, EstablishmentServiceRequest request) {
        User administrator = tenantAuthorizationService.requireAdministrator(administratorId);
        EstablishmentService service = findService(id, administrator.getEstablishment().getId());

        service.updateDetails(
                request.name().trim(),
                normalizeOptionalText(request.description()),
                request.price(),
                defaultCommission(request.commissionPercentage()),
                request.durationMinutes());

        return EstablishmentServiceResponse.fromEntity(establishmentServiceRepository.save(service));
    }

    @Transactional
    public void deactivate(Long administratorId, Long id) {
        User administrator = tenantAuthorizationService.requireAdministrator(administratorId);
        EstablishmentService service = findService(id, administrator.getEstablishment().getId());

        service.deactivate();
        establishmentServiceRepository.save(service);
    }

    private EstablishmentService findService(Long serviceId, Long establishmentId) {
        return establishmentServiceRepository.findByIdAndEstablishmentId(serviceId, establishmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Serviço não encontrado."));
    }

    private BigDecimal defaultCommission(BigDecimal commissionPercentage) {
        return commissionPercentage == null ? BigDecimal.ZERO : commissionPercentage;
    }

    private String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
