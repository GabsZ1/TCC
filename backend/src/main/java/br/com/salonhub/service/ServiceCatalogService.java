package br.com.salonhub.service;

import br.com.salonhub.dto.SalonServiceRequest;
import br.com.salonhub.dto.SalonServiceResponse;
import br.com.salonhub.entity.SalonService;
import br.com.salonhub.entity.User;
import br.com.salonhub.exception.ResourceNotFoundException;
import br.com.salonhub.repository.SalonServiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ServiceCatalogService {

    private final SalonServiceRepository salonServiceRepository;
    private final TenantAuthorizationService tenantAuthorizationService;

    public ServiceCatalogService(
            SalonServiceRepository salonServiceRepository,
            TenantAuthorizationService tenantAuthorizationService) {
        this.salonServiceRepository = salonServiceRepository;
        this.tenantAuthorizationService = tenantAuthorizationService;
    }

    @Transactional
    public SalonServiceResponse create(Long administratorId, SalonServiceRequest request) {
        User administrator = tenantAuthorizationService.requireAdministrator(administratorId);

        SalonService service = SalonService.builder()
                .establishment(administrator.getEstablishment())
                .name(request.name())
                .description(request.description())
                .price(request.price())
                .commissionPercentage(request.commissionPercentage())
                .active(true)
                .build();

        return SalonServiceResponse.fromEntity(salonServiceRepository.save(service));
    }

    @Transactional(readOnly = true)
    public List<SalonServiceResponse> findAll(Long administratorId) {
        User administrator = tenantAuthorizationService.requireAdministrator(administratorId);

        return salonServiceRepository.findByEstablishmentIdAndActiveTrue(administrator.getEstablishment().getId())
                .stream()
                .map(SalonServiceResponse::fromEntity)
                .toList();
    }

    @Transactional
    public SalonServiceResponse update(Long administratorId, Long id, SalonServiceRequest request) {
        User administrator = tenantAuthorizationService.requireAdministrator(administratorId);
        SalonService service = findService(id, administrator.getEstablishment().getId());

        service.setName(request.name());
        service.setDescription(request.description());
        service.setPrice(request.price());
        service.setCommissionPercentage(request.commissionPercentage());

        return SalonServiceResponse.fromEntity(salonServiceRepository.save(service));
    }

    @Transactional
    public void deactivate(Long administratorId, Long id) {
        User administrator = tenantAuthorizationService.requireAdministrator(administratorId);
        SalonService service = findService(id, administrator.getEstablishment().getId());

        service.setActive(false);
        salonServiceRepository.save(service);
    }

    private SalonService findService(Long serviceId, Long establishmentId) {
        return salonServiceRepository.findByIdAndEstablishmentId(serviceId, establishmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Serviço não encontrado."));
    }
}
