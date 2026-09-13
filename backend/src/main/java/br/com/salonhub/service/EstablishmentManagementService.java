package br.com.salonhub.service;

import br.com.salonhub.dto.establishment.EstablishmentResponse;
import br.com.salonhub.dto.establishment.EstablishmentUpdateRequest;
import br.com.salonhub.entity.Establishment;
import br.com.salonhub.exception.ConflictException;
import br.com.salonhub.repository.EstablishmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EstablishmentManagementService {

    private final EstablishmentRepository establishmentRepository;
    private final TenantAuthorizationService tenantAuthorizationService;

    public EstablishmentManagementService(
            EstablishmentRepository establishmentRepository,
            TenantAuthorizationService tenantAuthorizationService) {
        this.establishmentRepository = establishmentRepository;
        this.tenantAuthorizationService = tenantAuthorizationService;
    }

    @Transactional(readOnly = true)
    public EstablishmentResponse findMine(Long administratorId) {
        return EstablishmentResponse.fromEntity(findEstablishment(administratorId));
    }

    @Transactional
    public EstablishmentResponse update(Long administratorId, EstablishmentUpdateRequest request) {
        Establishment establishment = findEstablishment(administratorId);
        if (!establishment.isActive()) {
            throw new ConflictException("Não é possível editar um estabelecimento inativo.");
        }

        establishment.updateRegistration(
                request.name().trim(),
                normalizeOptionalText(request.email()),
                normalizeOptionalText(request.phone())
        );
        return EstablishmentResponse.fromEntity(establishmentRepository.save(establishment));
    }

    @Transactional
    public void deactivate(Long administratorId) {
        Establishment establishment = findEstablishment(administratorId);
        if (establishment.isActive()) {
            establishment.deactivate();
            establishmentRepository.save(establishment);
        }
    }

    private Establishment findEstablishment(Long administratorId) {
        return tenantAuthorizationService.requireAdministratorIdentity(administratorId).getEstablishment();
    }

    private String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}