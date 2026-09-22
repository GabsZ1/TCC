package br.com.salonhub.service;

import br.com.salonhub.dto.establishment.SystemEstablishmentResponse;
import br.com.salonhub.repository.EstablishmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SystemEstablishmentService {

    private final EstablishmentRepository establishmentRepository;
    private final EstablishmentManagementService establishmentManagementService;
    private final TenantAuthorizationService tenantAuthorizationService;

    @Transactional(readOnly = true)
    public List<SystemEstablishmentResponse> findAll(Long systemAdministratorId) {
        tenantAuthorizationService.requireSystemAdministrator(systemAdministratorId);
        return establishmentRepository.findAllByOrderByNameAsc().stream()
                .map(SystemEstablishmentResponse::fromEntity)
                .toList();
    }

    @Transactional
    public void deactivate(Long systemAdministratorId, Long establishmentId) {
        establishmentManagementService.deactivateAsSystemAdministrator(systemAdministratorId, establishmentId);
    }

    @Transactional
    public void reactivate(Long systemAdministratorId, Long establishmentId) {
        establishmentManagementService.reactivateAsSystemAdministrator(systemAdministratorId, establishmentId);
    }

    @Transactional
    public void blockBookings(Long systemAdministratorId, Long establishmentId) {
        establishmentManagementService.blockBookingsAsSystemAdministrator(systemAdministratorId, establishmentId);
    }

    @Transactional
    public void allowBookings(Long systemAdministratorId, Long establishmentId) {
        establishmentManagementService.allowBookingsAsSystemAdministrator(systemAdministratorId, establishmentId);
    }

    @Transactional
    public void pause(Long systemAdministratorId, Long establishmentId) {
        establishmentManagementService.pauseAsSystemAdministrator(systemAdministratorId, establishmentId);
    }

    @Transactional
    public void resume(Long systemAdministratorId, Long establishmentId) {
        establishmentManagementService.resumeAsSystemAdministrator(systemAdministratorId, establishmentId);
    }
}
