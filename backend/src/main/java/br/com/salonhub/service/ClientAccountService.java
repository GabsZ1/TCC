package br.com.salonhub.service;

import br.com.salonhub.dto.client.ClientAccountClosureRequest;
import br.com.salonhub.dto.client.ClientProfileResponse;
import br.com.salonhub.dto.client.ClientProfileUpdateRequest;
import br.com.salonhub.entity.Appointment;
import br.com.salonhub.entity.AppointmentStatus;
import br.com.salonhub.entity.Customer;
import br.com.salonhub.entity.User;
import br.com.salonhub.exception.ConflictException;
import br.com.salonhub.exception.InvalidRequestException;
import br.com.salonhub.repository.AppointmentRepository;
import br.com.salonhub.repository.CustomerRepository;
import br.com.salonhub.repository.UserRepository;
import br.com.salonhub.notification.AppointmentNotifier;
import br.com.salonhub.service.auth.SecureTokens;
import br.com.salonhub.validation.PhoneNumbers;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Locale;

/**
 * Conta do cliente (US 2.24, 2.25 e 2.26). Todas as operações usam o usuário do
 * token: não existe rota que receba o ID de outro cliente (CT11 da 2.24, CT04 da 2.25,
 * CT06 da 2.26).
 */
@Service
@RequiredArgsConstructor
public class ClientAccountService {

    private final TenantAuthorizationService tenantAuthorizationService;
    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;
    private final CustomerRepository customerRepository;
    private final AppointmentNotifier appointmentNotifier;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ClientProfileResponse getProfile(Long clientId) {
        return ClientProfileResponse.fromEntity(tenantAuthorizationService.requireClient(clientId));
    }

    @Transactional
    public ClientProfileResponse updateProfile(Long clientId, ClientProfileUpdateRequest request) {
        User client = userRepository.findByIdForUpdate(tenantAuthorizationService.requireClient(clientId).getId())
                .orElseThrow();

        String phone = PhoneNumbers.normalize(request.phone());
        if (phone == null) {
            throw new InvalidRequestException("Informe um telefone válido!");
        }
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(email, client.getId())) {
            throw new ConflictException("Este e-mail já está cadastrado!");
        }

        client.updateClientProfile(request.name().trim(), email, phone);
        customerRepository.findByUserId(client.getId()).forEach(customer -> customer.synchronizeFrom(client));
        return ClientProfileResponse.fromEntity(client);
    }

    /**
     * Exclui a conta: dados pessoais anonimizados, login bloqueado, e-mail liberado e
     * agendamentos futuros confirmados cancelados (para não deixar o estabelecimento
     * esperando). O histórico passado continua íntegro para o estabelecimento.
     */
    @Transactional
    public void closeAccount(Long clientId, ClientAccountClosureRequest request) {
        User client = userRepository.findByIdForUpdate(tenantAuthorizationService.requireClient(clientId).getId())
                .orElseThrow();

        if (!passwordEncoder.matches(request.password(), client.getPassword())) {
            throw new InvalidRequestException("Senha incorreta. A conta não foi excluída.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        for (Appointment appointment : appointmentRepository.findByCustomerUserIdAndStatusAndStartsAtGreaterThanOrderByStartsAtAsc(
                client.getId(), AppointmentStatus.CONFIRMED, now)) {
            appointment.cancelBecauseClientAccountWasClosed(now);
            appointmentNotifier.cancelledByClient(appointment);
        }

        customerRepository.findByUserId(client.getId()).forEach(Customer::anonymizeClosedAccount);

        client.closeClientAccount(
                "conta-excluida-" + client.getId() + "-" + SecureTokens.generate().substring(0, 8).toLowerCase(Locale.ROOT)
                        + "@contas-excluidas.salonhub.local",
                passwordEncoder.encode(SecureTokens.generate()),
                OffsetDateTime.now(clock));
    }
}
