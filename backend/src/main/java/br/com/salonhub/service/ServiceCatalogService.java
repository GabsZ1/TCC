package br.com.salonhub.service;

import br.com.salonhub.dto.service.EstablishmentServiceRequest;
import br.com.salonhub.dto.service.EstablishmentServiceResponse;
import br.com.salonhub.dto.service.ServiceCountsResponse;
import br.com.salonhub.entity.EstablishmentService;
import br.com.salonhub.entity.User;
import br.com.salonhub.exception.ResourceNotFoundException;
import br.com.salonhub.exception.ConflictException;
import org.springframework.dao.DataIntegrityViolationException;
import br.com.salonhub.repository.EstablishmentServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Locale;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class ServiceCatalogService {

    private final EstablishmentServiceRepository establishmentServiceRepository;
    private final TenantAuthorizationService tenantAuthorizationService;

    @Transactional
    public EstablishmentServiceResponse create(Long administratorId, EstablishmentServiceRequest request) {
        User administrator = tenantAuthorizationService.requireAdministrator(administratorId);

        requireUniqueName(administrator.getEstablishment().getId(), request.name(), null);

        EstablishmentService service = EstablishmentService.builder()
                .establishment(administrator.getEstablishment())
                .name(request.name().trim())
                .description(normalizeOptionalText(request.description()))
                .price(request.price())
                .commissionPercentage(defaultCommission(request.commissionPercentage()))
                .durationMinutes(request.durationMinutes())
                .active(true)
                .build();

        return save(service);
    }

    @Transactional(readOnly = true)
    public Page<EstablishmentServiceResponse> findAll(Long administratorId, int page, int size, Boolean active, String search) {
        User administrator = tenantAuthorizationService.requireAdministrator(administratorId);

        String searchPattern = "%" + (search == null ? "" : search.trim().toLowerCase(Locale.ROOT)) + "%";
        return establishmentServiceRepository.findAdministrativeServices(administrator.getEstablishment().getId(),
                        active, searchPattern, PageRequest.of(page, size, Sort.by("name").ascending().and(Sort.by("id"))))
                .map(EstablishmentServiceResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public ServiceCountsResponse counts(Long administratorId) {
        User administrator = tenantAuthorizationService.requireAdministrator(administratorId);
        Long establishmentId = administrator.getEstablishment().getId();
        long total = establishmentServiceRepository.countByEstablishmentId(establishmentId);
        long active = establishmentServiceRepository.countByEstablishmentIdAndActiveTrue(establishmentId);
        return new ServiceCountsResponse(total, active, total - active);
    }

    @Transactional
    public EstablishmentServiceResponse update(Long administratorId, Long id, EstablishmentServiceRequest request) {
        User administrator = tenantAuthorizationService.requireAdministrator(administratorId);
        EstablishmentService service = findService(id, administrator.getEstablishment().getId());

        requireUniqueName(administrator.getEstablishment().getId(), request.name(), id);

        service.updateDetails(
                request.name().trim(),
                normalizeOptionalText(request.description()),
                request.price(),
                defaultCommission(request.commissionPercentage()),
                request.durationMinutes());

        return save(service);
    }

    @Transactional
    public void deactivate(Long administratorId, Long id) {
        User administrator = tenantAuthorizationService.requireAdministrator(administratorId);
        EstablishmentService service = findService(id, administrator.getEstablishment().getId());

        service.deactivate();
    }

    @Transactional
    public EstablishmentServiceResponse reactivate(Long administratorId, Long id) {
        User administrator = tenantAuthorizationService.requireAdministrator(administratorId);
        EstablishmentService service = findService(id, administrator.getEstablishment().getId());
        if (service.isActive()) {
            throw new ConflictException("O serviço já está ativo.");
        }
        service.reactivate();
        return EstablishmentServiceResponse.fromEntity(service);
    }

    private void requireUniqueName(Long establishmentId, String name, Long excludedId) {
        if (establishmentServiceRepository.existsWithName(establishmentId, name.trim(), excludedId)) {
            throw new ConflictException("Já existe um serviço com esse nome neste estabelecimento.");
        }
    }

    private EstablishmentServiceResponse save(EstablishmentService service) {
        try {
            return EstablishmentServiceResponse.fromEntity(establishmentServiceRepository.saveAndFlush(service));
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                        && "uk_salon_services_establishment_name".equals(violation.getConstraintName())) {
                    throw new ConflictException("Já existe um serviço com esse nome neste estabelecimento.");
                }
            }
            throw exception;
        }
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
