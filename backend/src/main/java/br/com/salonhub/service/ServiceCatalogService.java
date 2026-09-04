package br.com.salonhub.service;

import br.com.salonhub.dto.EstablishmentServiceRequest;
import br.com.salonhub.dto.EstablishmentServiceResponse;
import br.com.salonhub.entity.EstablishmentService;
import br.com.salonhub.entity.User;
import br.com.salonhub.exception.ResourceNotFoundException;
import br.com.salonhub.repository.EstablishmentServiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ServiceCatalogService {

    private final EstablishmentServiceRepository establishmentServiceRepository;
    private final TenantAuthorizationService tenantAuthorizationService;

    public ServiceCatalogService(
            EstablishmentServiceRepository establishmentServiceRepository,
            TenantAuthorizationService tenantAuthorizationService) {
        this.establishmentServiceRepository = establishmentServiceRepository;
        this.tenantAuthorizationService = tenantAuthorizationService;
    }

    @Transactional
    public EstablishmentServiceResponse create(Long administratorId, EstablishmentServiceRequest request) {
        User administrator = tenantAuthorizationService.requireAdministrator(administratorId);

        EstablishmentService service = EstablishmentService.builder()
                .establishment(administrator.getEstablishment())
                .name(request.name())
                .description(request.description())
                .price(request.price())
                .commissionPercentage(request.commissionPercentage())
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

        service.setName(request.name());
        service.setDescription(request.description());
        service.setPrice(request.price());
        service.setCommissionPercentage(request.commissionPercentage());

        return EstablishmentServiceResponse.fromEntity(establishmentServiceRepository.save(service));
    }

    @Transactional
    public void deactivate(Long administratorId, Long id) {
        User administrator = tenantAuthorizationService.requireAdministrator(administratorId);
        EstablishmentService service = findService(id, administrator.getEstablishment().getId());

        service.setActive(false);
        establishmentServiceRepository.save(service);
    }

    private EstablishmentService findService(Long serviceId, Long establishmentId) {
        return establishmentServiceRepository.findByIdAndEstablishmentId(serviceId, establishmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Serviço não encontrado."));
    }
}
