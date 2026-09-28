package br.com.salonhub.service;

import br.com.salonhub.dto.auth.AuthenticatedUserResponse;
import br.com.salonhub.dto.employee.EmployeeCreateRequest;
import br.com.salonhub.dto.employee.EmployeeCreationResponse;
import br.com.salonhub.dto.employee.EmployeeRemovalResponse;
import br.com.salonhub.dto.employee.EmployeeUpdateRequest;
import br.com.salonhub.dto.employee.ProfessionalServicesRequest;
import br.com.salonhub.dto.employee.ProfessionalServicesResponse;
import br.com.salonhub.entity.Appointment;
import br.com.salonhub.entity.AppointmentStatus;
import br.com.salonhub.entity.EstablishmentService;
import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.exception.ConflictException;
import br.com.salonhub.exception.ResourceNotFoundException;
import br.com.salonhub.notification.EmailNotifier;
import br.com.salonhub.notification.FrontendLinks;
import br.com.salonhub.repository.AppointmentRepository;
import br.com.salonhub.repository.UserRepository;
import br.com.salonhub.repository.EstablishmentServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final EstablishmentServiceRepository establishmentServiceRepository;
    private final TokenService tokenService;
    private final EmailNotifier emailNotifier;
    private final FrontendLinks frontendLinks;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ProfessionalServicesResponse findServices(Long userId, Long employeeId) {
        User administrator = tenantAuthorizationService.requireAdministrator(userId);
        return responseWithEffectiveServices(findEmployee(employeeId, administrator.getEstablishment().getId()));
    }

    @Transactional(readOnly = true)
    public ProfessionalServicesResponse findMyServices(Long professionalId) {
        return responseWithEffectiveServices(tenantAuthorizationService.requireProfessional(professionalId));
    }

    @Transactional
    public ProfessionalServicesResponse updateServices(
            Long administratorId, Long employeeId, ProfessionalServicesRequest request) {
        User administrator = tenantAuthorizationService.requireAdministrator(administratorId);
        Long establishmentId = administrator.getEstablishment().getId();
        User employee = findEmployee(employeeId, establishmentId);

        List<EstablishmentService> services = List.of();
        if (!request.allServices() && !request.serviceIds().isEmpty()) {
            services = establishmentServiceRepository
                    .findByIdInAndEstablishmentIdAndActiveTrue(request.serviceIds(), establishmentId);
            if (services.size() != request.serviceIds().size()) {
                throw new ResourceNotFoundException("Um ou mais serviços não foram encontrados.");
            }
        }
        employee.updateOfferedServices(request.allServices(), services);
        return responseWithEffectiveServices(employee);
    }

    private ProfessionalServicesResponse responseWithEffectiveServices(User professional) {
        if (!professional.isOffersAllServices()) {
            return ProfessionalServicesResponse.fromEntity(professional);
        }
        List<br.com.salonhub.dto.service.EstablishmentServiceResponse> services = establishmentServiceRepository
                .findByEstablishmentIdAndActiveTrue(professional.getEstablishment().getId()).stream()
                .map(br.com.salonhub.dto.service.EstablishmentServiceResponse::fromEntity)
                .toList();
        return new ProfessionalServicesResponse(professional.getId(), true, services);
    }

    @Transactional
    public EmployeeCreationResponse create(Long administratorId, EmployeeCreateRequest request) {
        User administrator = tenantAuthorizationService.requireAdministrator(administratorId);

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("E-mail já cadastrado no sistema.");
        }

        User employee = User.builder()
                .name(request.name().trim())
                .email(email)
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .passwordInitialized(false)
                .role(UserRole.PROFESSIONAL)
                .establishment(administrator.getEstablishment())
                .active(true)
                .build();

        User savedEmployee = userRepository.save(employee);
        TokenService.GeneratedToken token = tokenService.generateFirstAccess(savedEmployee);
        // US 2.1 / RNF.03.02: o e-mail de primeiro acesso sai automaticamente depois do cadastro
        // (e só se ele for confirmado). O link continua na resposta para o admin copiar, se preferir.
        emailNotifier.sendFirstAccessInvitation(
                savedEmployee.getEmail(),
                savedEmployee.getName(),
                administrator.getEstablishment().getName(),
                frontendLinks.firstAccess(token.value()),
                token.expiresAt());
        return EmployeeCreationResponse.fromEntity(savedEmployee, token.value(), token.expiresAt());
    }

    @Transactional(readOnly = true)
    public List<AuthenticatedUserResponse> findAll(Long administratorId) {
        User administrator = tenantAuthorizationService.requireAdministrator(administratorId);

        // Funcionários desativados continuam na listagem (com status inativo) —
        // eles só somem de fato quando removidos permanentemente (RN 2.4.2 / CT03).
        return userRepository.findByEstablishmentIdAndRole(
                        administrator.getEstablishment().getId(),
                        UserRole.PROFESSIONAL)
                .stream()
                .map(AuthenticatedUserResponse::fromEntity)
                .toList();
    }

    @Transactional
    public AuthenticatedUserResponse update(Long administratorId, Long employeeId, EmployeeUpdateRequest request) {
        User administrator = tenantAuthorizationService.requireAdministrator(administratorId);
        User employee = findEmployee(employeeId, administrator.getEstablishment().getId());

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(email, employeeId)) {
            throw new ConflictException("E-mail já cadastrado no sistema.");
        }

        employee.updateProfile(request.name().trim(), email);

        return AuthenticatedUserResponse.fromEntity(employee);
    }

    @Transactional
    public AuthenticatedUserResponse reactivate(Long administratorId, Long employeeId) {
        User administrator = tenantAuthorizationService.requireAdministrator(administratorId);
        User employee = findEmployee(employeeId, administrator.getEstablishment().getId());

        if (employee.isActive()) {
            throw new ConflictException("O funcionário já está ativo.");
        }

        employee.reactivate();
        return AuthenticatedUserResponse.fromEntity(employee);
    }

    @Transactional
    public AuthenticatedUserResponse deactivate(Long administratorId, Long employeeId) {
        User administrator = tenantAuthorizationService.requireAdministrator(administratorId);
        Long establishmentId = administrator.getEstablishment().getId();

        // Appointment creation/remapping already locks the professional before writing.
        // Taking the same row lock here serializes deactivation with those flows.
        // This transaction does not acquire client or establishment locks afterwards,
        // so it remains a valid subset of the global lock order.
        User employee = userRepository.findByIdForUpdate(employeeId)
                .filter(candidate -> candidate.getRole() == UserRole.PROFESSIONAL)
                .filter(candidate -> candidate.getEstablishment() != null)
                .filter(candidate -> candidate.getEstablishment().getId().equals(establishmentId))
                .orElseThrow(() -> new ResourceNotFoundException("Funcionário não encontrado."));
        if (!employee.isActive()) {
            throw new ConflictException("O funcionário já está desativado.");
        }
        requireNoRelevantConfirmedAppointments(employee);
        employee.deactivate();
        return AuthenticatedUserResponse.fromEntity(employee);
    }

    /**
     * Impede desativar um funcionário que ainda possui atendimentos confirmados futuros ou em andamento:
     * sem isso, o agendamento continuava CONFIRMED mesmo com o profissional impedido de
     * acessar o sistema, e não havia fluxo de reatribuição. Para o MVP, o administrador
     * precisa cancelar, concluir ou aguardar o término desses atendimentos antes de desativar.
     */
    private void requireNoRelevantConfirmedAppointments(User employee) {
        List<Appointment> relevantConfirmed = appointmentRepository
                .findByProfessionalIdAndStatusAndEndsAtGreaterThanOrderByStartsAt(
                        employee.getId(), AppointmentStatus.CONFIRMED, LocalDateTime.now(clock));
        if (!relevantConfirmed.isEmpty()) {
            throw new ConflictException(
                    "Este funcionário possui " + relevantConfirmed.size()
                            + " agendamento(s) confirmado(s) futuro(s) ou em andamento. Cancele, conclua ou aguarde o término "
                            + "desses agendamentos antes de desativar o funcionário.");
        }
    }

    @Transactional
    public EmployeeRemovalResponse remove(Long administratorId, Long employeeId) {
        User administrator = tenantAuthorizationService.requireAdministrator(administratorId);
        User employee = findEmployee(employeeId, administrator.getEstablishment().getId());
        // Use the same professional lock as appointment creation.
        userRepository.findByIdForUpdate(employee.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Funcionário não encontrado."));
        if (appointmentRepository.existsByProfessionalId(employee.getId())
                || !employee.getOfferedServices().isEmpty()) {
            throw new ConflictException("O funcionário possui agendamentos ou serviços vinculados e não pode ser excluído. Utilize a desativação.");
        }
        try {
            userRepository.delete(employee);
            userRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("O funcionário possui vínculos que impedem a exclusão definitiva. Utilize a desativação.");
        }
        return new EmployeeRemovalResponse(true, "O funcionário foi excluído definitivamente.");
    }

    private User findEmployee(Long employeeId, Long establishmentId) {
        return userRepository.findByIdAndEstablishmentIdAndRole(employeeId, establishmentId, UserRole.PROFESSIONAL)
                .orElseThrow(() -> new ResourceNotFoundException("Funcionário não encontrado."));
    }
}
