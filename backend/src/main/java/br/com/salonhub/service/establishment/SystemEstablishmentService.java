package br.com.salonhub.service.establishment;

import br.com.salonhub.service.tenant.TenantAuthorizationService;
import br.com.salonhub.dto.establishment.BusinessHourResponse;
import br.com.salonhub.dto.establishment.SystemEstablishmentDetailsResponse;
import br.com.salonhub.dto.establishment.SystemEstablishmentResponse;
import br.com.salonhub.entity.AppointmentStatus;
import br.com.salonhub.entity.Establishment;
import br.com.salonhub.entity.EstablishmentService;
import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.exception.ResourceNotFoundException;
import br.com.salonhub.repository.AppointmentRepository;
import br.com.salonhub.repository.EstablishmentBusinessHourRepository;
import br.com.salonhub.repository.EstablishmentProfileRepository;
import br.com.salonhub.repository.EstablishmentRepository;
import br.com.salonhub.repository.EstablishmentServiceRepository;
import br.com.salonhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/** Oferece à administração do sistema uma visão dos estabelecimentos cadastrados. */
@Service
@RequiredArgsConstructor
public class SystemEstablishmentService {

    private final EstablishmentRepository establishmentRepository;
    private final EstablishmentOperationService establishmentOperationService;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final EstablishmentProfileRepository profileRepository;
    private final EstablishmentBusinessHourRepository businessHourRepository;
    private final EstablishmentServiceRepository serviceRepository;
    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;
    private final Clock clock;

    /** Lista unidades para a administração global sem restringir a um estabelecimento. */
    @Transactional(readOnly = true)
    public List<SystemEstablishmentResponse> findAll(Long systemAdministratorId) {
        tenantAuthorizationService.requireSystemAdministrator(systemAdministratorId);
        return establishmentRepository.findAllByOrderByNameAsc().stream()
                .map(SystemEstablishmentResponse::fromEntity)
                .toList();
    }

    /** Detalhes de um estabelecimento: cadastro, responsáveis, perfil público, horários, equipe, serviços e números. */
    @Transactional(readOnly = true)
    public SystemEstablishmentDetailsResponse findDetails(Long systemAdministratorId, Long establishmentId) {
        tenantAuthorizationService.requireSystemAdministrator(systemAdministratorId);
        Establishment establishment = establishmentRepository.findById(establishmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Estabelecimento não encontrado."));

        Comparator<User> byName = Comparator.comparing(User::getName, String.CASE_INSENSITIVE_ORDER);
        List<User> administrators = userRepository.findByEstablishmentIdAndRole(establishmentId, UserRole.ADMIN)
                .stream().sorted(byName).toList();
        List<User> professionals = userRepository.findByEstablishmentIdAndRole(establishmentId, UserRole.PROFESSIONAL)
                .stream().sorted(Comparator.comparing(User::isActive).reversed().thenComparing(byName)).toList();
        List<EstablishmentService> services = serviceRepository.findByEstablishmentId(establishmentId).stream()
                .sorted(Comparator.comparing(EstablishmentService::isActive).reversed()
                        .thenComparing(EstablishmentService::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime monthStart = LocalDate.now(clock).withDayOfMonth(1).atStartOfDay();
        var stats = new SystemEstablishmentDetailsResponse.Stats(
                professionals.stream().filter(User::isActive).count(),
                professionals.size(),
                services.stream().filter(EstablishmentService::isActive).count(),
                services.size(),
                appointmentRepository.countByEstablishmentIdAndStatusAndStartsAtGreaterThanEqual(
                        establishmentId, AppointmentStatus.CONFIRMED, now),
                appointmentRepository.countByEstablishmentIdAndStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
                        establishmentId, AppointmentStatus.COMPLETED, monthStart, monthStart.plusMonths(1)),
                appointmentRepository.countByEstablishmentId(establishmentId));

        return new SystemEstablishmentDetailsResponse(
                SystemEstablishmentResponse.fromEntity(establishment),
                establishment.getMinimumAdvanceMinutes(),
                establishment.getMaximumAdvanceDays(),
                administrators.stream().map(SystemEstablishmentDetailsResponse.Member::fromEntity).toList(),
                profileRepository.findByEstablishmentId(establishmentId)
                        .map(SystemEstablishmentDetailsResponse.Profile::fromEntity).orElse(null),
                businessHourRepository.findByEstablishmentIdOrderByDayOfWeek(establishmentId)
                        .stream().map(BusinessHourResponse::fromEntity).toList(),
                stats,
                professionals.stream().map(SystemEstablishmentDetailsResponse.Professional::fromEntity).toList(),
                services.stream().map(SystemEstablishmentDetailsResponse.Service::fromEntity).toList());
    }

    /** Encaminha a desativação global e seus efeitos sobre reservas confirmadas. */
    @Transactional
    public void deactivate(Long systemAdministratorId, Long establishmentId) {
        establishmentOperationService.deactivateAsSystemAdministrator(systemAdministratorId, establishmentId);
    }

    /** Encaminha a reativação de uma unidade pela administração global. */
    @Transactional
    public void reactivate(Long systemAdministratorId, Long establishmentId) {
        establishmentOperationService.reactivateAsSystemAdministrator(systemAdministratorId, establishmentId);
    }

    /** Bloqueia globalmente novas reservas sem desativar a unidade. */
    @Transactional
    public void blockBookings(Long systemAdministratorId, Long establishmentId) {
        establishmentOperationService.blockBookingsAsSystemAdministrator(systemAdministratorId, establishmentId);
    }

    /** Libera novamente reservas antes bloqueadas pela administração global. */
    @Transactional
    public void allowBookings(Long systemAdministratorId, Long establishmentId) {
        establishmentOperationService.allowBookingsAsSystemAdministrator(systemAdministratorId, establishmentId);
    }

    /** Pausa a operação da unidade pelo papel de administrador do sistema. */
    @Transactional
    public void pause(Long systemAdministratorId, Long establishmentId) {
        establishmentOperationService.pauseAsSystemAdministrator(systemAdministratorId, establishmentId);
    }

    /** Retoma a operação da unidade pelo papel de administrador do sistema. */
    @Transactional
    public void resume(Long systemAdministratorId, Long establishmentId) {
        establishmentOperationService.resumeAsSystemAdministrator(systemAdministratorId, establishmentId);
    }
}
