package br.com.salonhub.service;

import br.com.salonhub.dto.auth.AuthenticatedUserResponse;
import br.com.salonhub.dto.employee.EmployeeCreateRequest;
import br.com.salonhub.dto.employee.EmployeeUpdateRequest;
import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.exception.ConflictException;
import br.com.salonhub.exception.ResourceNotFoundException;
import br.com.salonhub.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class EmployeeService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantAuthorizationService tenantAuthorizationService;

    public EmployeeService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            TenantAuthorizationService tenantAuthorizationService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tenantAuthorizationService = tenantAuthorizationService;
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

        return userRepository.findByEstablishmentIdAndRoleAndActiveTrue(
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

        employee.setName(request.name().trim());
        employee.setEmail(email);

        return AuthenticatedUserResponse.fromEntity(userRepository.save(employee));
    }

    @Transactional
    public void deactivate(Long administratorId, Long employeeId) {
        User administrator = tenantAuthorizationService.requireAdministrator(administratorId);
        User employee = findEmployee(employeeId, administrator.getEstablishment().getId());

        employee.setActive(false);
        userRepository.save(employee);
    }

    private User findEmployee(Long employeeId, Long establishmentId) {
        return userRepository.findByIdAndEstablishmentIdAndRole(employeeId, establishmentId, UserRole.PROFESSIONAL)
                .orElseThrow(() -> new ResourceNotFoundException("Funcionário não encontrado."));
    }
}
