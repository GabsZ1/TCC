package br.com.salonhub.service;

import br.com.salonhub.dto.auth.AuthenticatedUserResponse;
import br.com.salonhub.dto.employee.EmployeeCreateRequest;
import br.com.salonhub.dto.employee.EmployeeRemovalResponse;
import br.com.salonhub.dto.employee.EmployeeUpdateRequest;
import br.com.salonhub.dto.employee.ProfessionalServicesRequest;
import br.com.salonhub.dto.employee.ProfessionalServicesResponse;
import br.com.salonhub.entity.EstablishmentService;
import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.exception.ConflictException;
import br.com.salonhub.exception.ResourceNotFoundException;
import br.com.salonhub.repository.AppointmentRepository;
import br.com.salonhub.repository.UserRepository;
import br.com.salonhub.repository.EstablishmentServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final EstablishmentServiceRepository establishmentServiceRepository;

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
        userRepository.save(employee);
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
    public AuthenticatedUserResponse create(Long administratorId, EmployeeCreateRequest request) {
        User administrator = tenantAuthorizationService.requireAdministrator(administratorId);

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("E-mail já cadastrado no sistema.");
        }

        User employee = User.builder()
                .name(request.name().trim())
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .role(UserRole.PROFESSIONAL)
                .establishment(administrator.getEstablishment())
                .active(true)
                .build();

        return AuthenticatedUserResponse.fromEntity(userRepository.save(employee));
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

        return AuthenticatedUserResponse.fromEntity(userRepository.save(employee));
    }

    @Transactional
    public AuthenticatedUserResponse reactivate(Long administratorId, Long employeeId) {
        User administrator = tenantAuthorizationService.requireAdministrator(administratorId);
        User employee = findEmployee(employeeId, administrator.getEstablishment().getId());

        if (employee.isActive()) {
            throw new ConflictException("O funcionário já está ativo.");
        }

        employee.reactivate();
        return AuthenticatedUserResponse.fromEntity(userRepository.save(employee));
    }

    @Transactional
    public EmployeeRemovalResponse remove(Long administratorId, Long employeeId) {
        User administrator = tenantAuthorizationService.requireAdministrator(administratorId);
        User employee = findEmployee(employeeId, administrator.getEstablishment().getId());

        boolean hasAppointments = appointmentRepository.existsByProfessionalId(employee.getId());

        // RN 2.3.2: só é possível remover de verdade um funcionário sem atendimentos
        // vinculados; caso contrário, a remoção é convertida em inativação (CT04).
        if (!hasAppointments) {
            userRepository.delete(employee);
            return new EmployeeRemovalResponse(true, "O funcionário foi removido com sucesso.");
        }

        if (!employee.isActive()) {
            // CT05: funcionário já está desativado, nenhuma alteração é realizada.
            throw new ConflictException("O funcionário já está desativado.");
        }

        employee.deactivate();
        userRepository.save(employee);
        return new EmployeeRemovalResponse(
                false,
                "O funcionário não pode ser removido pois possui atendimentos vinculados. Ele foi desativado."
        );
    }

    private User findEmployee(Long employeeId, Long establishmentId) {
        return userRepository.findByIdAndEstablishmentIdAndRole(employeeId, establishmentId, UserRole.PROFESSIONAL)
                .orElseThrow(() -> new ResourceNotFoundException("Funcionário não encontrado."));
    }
}
