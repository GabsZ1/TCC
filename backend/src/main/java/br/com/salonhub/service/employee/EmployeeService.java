package br.com.salonhub.service.employee;

import br.com.salonhub.service.auth.TokenService;
import br.com.salonhub.service.media.MediaService;
import br.com.salonhub.service.tenant.TenantAuthorizationService;
import br.com.salonhub.dto.employee.EmployeeCreateRequest;
import br.com.salonhub.dto.employee.EmployeeCreationResponse;
import br.com.salonhub.dto.employee.EmployeeRemovalResponse;
import br.com.salonhub.dto.employee.EmployeeResponse;
import br.com.salonhub.dto.employee.EmployeeSelfProfileUpdateRequest;
import br.com.salonhub.dto.employee.EmployeeUpdateRequest;
import br.com.salonhub.dto.employee.ProfessionalServicesRequest;
import br.com.salonhub.dto.employee.ProfessionalServicesResponse;
import br.com.salonhub.entity.Appointment;
import br.com.salonhub.entity.AppointmentStatus;
import br.com.salonhub.entity.EstablishmentService;
import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.exception.ConflictException;
import br.com.salonhub.exception.InvalidRequestException;
import br.com.salonhub.exception.ResourceNotFoundException;
import br.com.salonhub.notification.EmailNotifier;
import br.com.salonhub.notification.FrontendLinks;
import br.com.salonhub.repository.AppointmentRepository;
import br.com.salonhub.repository.UserRepository;
import br.com.salonhub.validation.CpfValidator;
import br.com.salonhub.validation.PhoneNumbers;
import br.com.salonhub.repository.EstablishmentServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Gerencia funcionários, papéis e os serviços atribuídos aos profissionais. */
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
    private final MediaService mediaService;
    private final Clock clock;

    /** Consulta os serviços atribuídos ao funcionário no estabelecimento do administrador. */
    @Transactional(readOnly = true)
    public ProfessionalServicesResponse findServices(Long userId, Long employeeId) {
        User administrator = tenantAuthorizationService.requireAdministrator(userId);
        return responseWithEffectiveServices(findEmployee(employeeId, administrator.getEstablishment().getId()));
    }

    /** Devolve ao profissional autenticado apenas os serviços que pode oferecer. */
    @Transactional(readOnly = true)
    public ProfessionalServicesResponse findMyServices(Long professionalId) {
        return responseWithEffectiveServices(tenantAuthorizationService.requireProfessional(professionalId));
    }

    /** Substitui os serviços oferecidos pelo profissional dentro do catálogo do estabelecimento. */
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

    /** Cria o funcionário vinculado ao estabelecimento e prepara seu primeiro acesso. */
    @Transactional
    public EmployeeCreationResponse create(Long administratorId, EmployeeCreateRequest request) {
        User administrator = tenantAuthorizationService.requireAdministrator(administratorId);

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("E-mail já cadastrado no sistema.");
        }
        String cpf = CpfValidator.normalize(request.cpf());
        requireHireDateNotInFuture(request.hireDate());
        if (userRepository.existsByCpf(cpf)) {
            throw new ConflictException("Já existe um funcionário cadastrado com este CPF.");
        }

        User employee = User.builder()
                .name(request.name().trim())
                .email(email)
                .occupation(request.occupation().trim())
                .cpf(cpf)
                .hireDate(request.hireDate())
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .passwordInitialized(false)
                .role(UserRole.PROFESSIONAL)
                .establishment(administrator.getEstablishment())
                .active(true)
                .build();

        User savedEmployee = userRepository.save(employee);
        return invite(administrator, savedEmployee);
    }

    /** Gera novas instruções de primeiro acesso para um funcionário elegível. */
    @Transactional
    public EmployeeCreationResponse resendInvitation(Long administratorId, Long employeeId) {
        User administrator = tenantAuthorizationService.requireAdministrator(administratorId);
        User employee = userRepository.findByIdForUpdate(employeeId)
                .filter(candidate -> candidate.getRole() == UserRole.PROFESSIONAL)
                .filter(candidate -> candidate.getEstablishment() != null)
                .filter(candidate -> candidate.getEstablishment().getId().equals(administrator.getEstablishment().getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Funcionário não encontrado."));
        if (!employee.isActive()) throw new ConflictException("O funcionário está desativado.");
        if (employee.isPasswordInitialized()) throw new ConflictException("O primeiro acesso já foi concluído.");
        employee.renewFirstAccess();
        return invite(administrator, employee);
    }

    private EmployeeCreationResponse invite(User administrator, User employee) {
        TokenService.GeneratedToken token = tokenService.generateFirstAccess(employee);
        emailNotifier.sendFirstAccessInvitation(employee.getEmail(), employee.getName(),
                administrator.getEstablishment().getName(), frontendLinks.firstAccess(token.value()), token.expiresAt());
        return EmployeeCreationResponse.fromEntity(employee);
    }

    /** Lista a equipe pertencente ao estabelecimento da conta administradora. */
    @Transactional(readOnly = true)
    public List<EmployeeResponse> findAll(Long administratorId) {
        User administrator = tenantAuthorizationService.requireAdministrator(administratorId);

        // Funcionários desativados continuam na listagem (com status inativo) —
        // eles só somem de fato quando removidos permanentemente (RN 2.4.2 / CT03).
        List<User> employees = userRepository.findByEstablishmentIdAndRole(
                administrator.getEstablishment().getId(),
                UserRole.PROFESSIONAL);
        return employees.stream()
                .sorted(java.util.Comparator.comparing(User::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toResponse)
                .toList();
    }

    /**
     * Edição pelo administrador: nome e ocupação; CPF/data de admissão só se ainda estiverem
     * vazios (depois de gravados ficam travados para todos).
     */
    @Transactional
    public EmployeeResponse update(Long administratorId, Long employeeId, EmployeeUpdateRequest request) {
        User administrator = tenantAuthorizationService.requireAdministrator(administratorId);
        User employee = findEmployee(employeeId, administrator.getEstablishment().getId());

        String cpf = null;
        if (request.cpf() != null && !request.cpf().isBlank()) {
            String informed = CpfValidator.normalize(request.cpf());
            if (employee.getCpf() != null && !employee.getCpf().equals(informed)) {
                throw new InvalidRequestException("O CPF não pode ser alterado depois de cadastrado.");
            }
            if (employee.getCpf() == null) {
                if (userRepository.existsByCpfAndIdNot(informed, employeeId)) {
                    throw new ConflictException("Já existe um funcionário cadastrado com este CPF.");
                }
                cpf = informed;
            }
        }
        java.time.LocalDate hireDate = null;
        if (request.hireDate() != null) {
            if (employee.getHireDate() != null && !employee.getHireDate().equals(request.hireDate())) {
                throw new InvalidRequestException("A data de admissão não pode ser alterada depois de cadastrada.");
            }
            if (employee.getHireDate() == null) {
                requireHireDateNotInFuture(request.hireDate());
                hireDate = request.hireDate();
            }
        }

        employee.renameEmployee(request.name().trim());
        if (request.occupation() != null) {
            if (request.occupation().isBlank()) {
                throw new InvalidRequestException("A ocupação é obrigatória.");
            }
            employee.updateEmployeeOccupation(request.occupation().trim());
        }
        employee.fillEmployeeDocuments(cpf, hireDate);
        return toResponse(employee);
    }

    /** Perfil do próprio funcionário (somente leitura dos dados administrativos). */
    @Transactional(readOnly = true)
    public EmployeeResponse findMyProfile(Long professionalId) {
        return toResponse(tenantAuthorizationService.requireProfessional(professionalId));
    }

    /** O funcionário atualiza e-mail (com a senha atual) e contatos. */
    @Transactional
    public EmployeeResponse updateMyProfile(Long professionalId, EmployeeSelfProfileUpdateRequest request) {
        User professional = userRepository.findByIdForUpdate(
                tenantAuthorizationService.requireProfessional(professionalId).getId()).orElseThrow();

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (!email.equals(professional.getEmail())) {
            if (request.currentPassword() == null
                    || !passwordEncoder.matches(request.currentPassword(), professional.getPassword())) {
                throw new InvalidRequestException("Para alterar o e-mail de acesso, informe corretamente sua senha atual.");
            }
            if (userRepository.existsByEmailIgnoreCaseAndIdNot(email, professional.getId())) {
                throw new ConflictException("E-mail já cadastrado no sistema.");
            }
        }

        professional.updateOwnEmployeeProfile(
                email,
                normalizePhone(request.phone()),
                normalizeInstagram(request.instagram()),
                request.address() == null || request.address().isBlank() ? null : request.address().trim());
        return toResponse(professional);
    }

    /** Reativa o funcionário após conferir as condições de retorno à equipe. */
    @Transactional
    public EmployeeResponse reactivate(Long administratorId, Long employeeId) {
        User administrator = tenantAuthorizationService.requireAdministrator(administratorId);
        User employee = findEmployee(employeeId, administrator.getEstablishment().getId());

        if (employee.isActive()) {
            throw new ConflictException("O funcionário já está ativo.");
        }

        employee.reactivate();
        return toResponse(employee);
    }

    /** Desativa o funcionário somente quando não há agendamentos confirmados relevantes. */
    @Transactional
    public EmployeeResponse deactivate(Long administratorId, Long employeeId) {
        User administrator = tenantAuthorizationService.requireAdministrator(administratorId);
        Long establishmentId = administrator.getEstablishment().getId();

        // Criação e remarcação bloqueiam o profissional antes de gravar. Usar o mesmo
        // bloqueio serializa esses fluxos com a desativação sem inverter a ordem global.
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
        return toResponse(employee);
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

    /** Remove um cadastro elegível segundo as regras de histórico e vínculo do estabelecimento. */
    @Transactional
    public EmployeeRemovalResponse remove(Long administratorId, Long employeeId) {
        User administrator = tenantAuthorizationService.requireAdministrator(administratorId);
        User employee = findEmployee(employeeId, administrator.getEstablishment().getId());
        // Usa o mesmo bloqueio do profissional aplicado na criação de agendamentos.
        userRepository.findByIdForUpdate(employee.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Funcionário não encontrado."));
        if (appointmentRepository.existsByProfessionalId(employee.getId())) {
            throw new ConflictException("O funcionário possui agendamentos vinculados e não pode ser excluído. Utilize a desativação.");
        }
        br.com.salonhub.entity.Media photo = employee.getPhoto();
        try {
            if (photo != null) {
                employee.replacePhoto(null);
                userRepository.flush();
                mediaService.delete(photo);
            }
            userRepository.delete(employee);
            userRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("O funcionário possui vínculos que impedem a exclusão definitiva. Utilize a desativação.");
        }
        return new EmployeeRemovalResponse(true, "O funcionário foi excluído definitivamente.");
    }

    private EmployeeResponse toResponse(User employee) {
        return EmployeeResponse.fromEntity(employee, employee.getPhoto() != null);
    }

    private void requireHireDateNotInFuture(LocalDate hireDate) {
        if (hireDate != null && hireDate.isAfter(LocalDate.now(clock))) {
            throw new InvalidRequestException("A data de admissão não pode ser futura.");
        }
    }

    private static String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String normalized = PhoneNumbers.normalize(phone);
        if (normalized == null) {
            throw new InvalidRequestException("Informe um telefone válido!");
        }
        return normalized;
    }

    private static String normalizeInstagram(String instagram) {
        if (instagram == null || instagram.isBlank()) {
            return null;
        }
        String normalized = instagram.trim().replaceFirst("^@", "");
        if (!normalized.matches("[A-Za-z0-9._]{1,30}")) {
            throw new InvalidRequestException("Instagram inválido. Use até 30 letras, números, ponto ou sublinhado.");
        }
        return normalized;
    }

    private User findEmployee(Long employeeId, Long establishmentId) {
        return userRepository.findByIdAndEstablishmentIdAndRole(employeeId, establishmentId, UserRole.PROFESSIONAL)
                .orElseThrow(() -> new ResourceNotFoundException("Funcionário não encontrado."));
    }
}
