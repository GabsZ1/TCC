package br.com.salonhub.service;

import br.com.salonhub.entity.Customer;
import br.com.salonhub.entity.Appointment;
import br.com.salonhub.entity.AppointmentStatus;
import br.com.salonhub.dto.customer.CustomerResponse;
import br.com.salonhub.exception.ConflictException;
import br.com.salonhub.exception.ResourceNotFoundException;
import br.com.salonhub.repository.AppointmentRepository;
import br.com.salonhub.repository.CustomerRepository;
import br.com.salonhub.repository.EstablishmentRepository;
import br.com.salonhub.validation.PhoneNumbers;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerDuplicateService {
    private final CustomerRepository customers;
    private final AppointmentRepository appointments;
    private final EstablishmentRepository establishments;
    private final TenantAuthorizationService authorization;
    private final JdbcTemplate jdbc;

    public record Candidate(Long id, String name, String phone, String email, boolean linked,
                            long appointmentCount) {}

    @Transactional(readOnly = true)
    public List<CustomerResponse> suggestionsForNew(Long staffId, String phone, String email) {
        Long establishmentId = authorization.requireEstablishmentStaff(staffId).getEstablishment().getId();
        String normalizedPhone = PhoneNumbers.normalize(phone);
        String normalizedEmail = email(email);
        if (normalizedPhone == null && normalizedEmail == null) return List.of();
        return customers.findByEstablishmentIdOrderByNameAsc(establishmentId).stream()
                .filter(Customer::isActive)
                .filter(candidate -> matches(candidate, normalizedPhone, normalizedEmail))
                .map(CustomerResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Candidate> candidates(Long administratorId, Long customerId) {
        Long establishmentId = establishmentId(administratorId);
        Customer selected = customer(customerId, establishmentId);
        if (!selected.isActive()) return List.of();
        Set<Long> ignored = jdbc.queryForList("""
                select case when customer_id_a = ? then customer_id_b else customer_id_a end
                from customer_duplicate_ignores
                where establishment_id = ? and (customer_id_a = ? or customer_id_b = ?)
                """, Long.class, customerId, establishmentId, customerId, customerId)
                .stream().collect(Collectors.toSet());
        return customers.findByEstablishmentIdOrderByNameAsc(establishmentId).stream()
                .filter(candidate -> candidate.isActive() && !candidate.getId().equals(customerId))
                .filter(candidate -> !ignored.contains(candidate.getId()))
                .filter(candidate -> similar(selected, candidate))
                .map(candidate -> new Candidate(candidate.getId(), candidate.getName(), candidate.getPhone(),
                        candidate.getEmail(), candidate.getUser() != null,
                        appointments.countByCustomerId(candidate.getId())))
                .toList();
    }

    @Transactional
    public Customer merge(Long administratorId, Long selectedId, Long otherId) {
        Long establishmentId = establishmentId(administratorId);
        lockEstablishment(establishmentId);
        if (selectedId.equals(otherId)) throw new ConflictException("Selecione dois clientes diferentes.");
        Customer selected = customer(selectedId, establishmentId);
        Customer other = customer(otherId, establishmentId);
        if (!selected.isActive() || !other.isActive()) throw new ConflictException("Os dois cadastros devem estar ativos.");
        if (selected.getUser() != null && other.getUser() != null) {
            throw new ConflictException("Não é possível mesclar cadastros vinculados a contas diferentes.");
        }
        Customer survivor = selected.getUser() == null && other.getUser() != null ? other : selected;
        Customer source = survivor == selected ? other : selected;
        List<Appointment> sourceAppointments = appointments.findByCustomerIdAndEstablishmentIdOrderByStartsAtDesc(source.getId(), establishmentId);
        List<Appointment> survivorAppointments = appointments.findByCustomerIdAndEstablishmentIdOrderByStartsAtDesc(survivor.getId(), establishmentId);
        boolean conflicting = sourceAppointments.stream().filter(item -> item.getStatus() == AppointmentStatus.CONFIRMED)
                .anyMatch(left -> survivorAppointments.stream().filter(item -> item.getStatus() == AppointmentStatus.CONFIRMED)
                        .anyMatch(right -> left.getStartsAt().isBefore(right.getEndsAt())
                                && left.getEndsAt().isAfter(right.getStartsAt())));
        if (conflicting) throw new ConflictException("Existem agendamentos conflitantes que precisam ser resolvidos antes da mesclagem.");
        sourceAppointments.forEach(appointment -> appointment.reassignCustomer(survivor));
        customers.flush();
        customers.delete(source);
        customers.flush();
        return survivor;
    }

    @Transactional
    public void keepSeparate(Long administratorId, Long firstId, Long secondId) {
        Long establishmentId = establishmentId(administratorId);
        lockEstablishment(establishmentId);
        if (firstId.equals(secondId)) throw new ConflictException("Selecione dois clientes diferentes.");
        Customer first = customer(firstId, establishmentId);
        Customer second = customer(secondId, establishmentId);
        if (!first.isActive() || !second.isActive()) throw new ConflictException("Os dois cadastros devem estar ativos.");
        long a = Math.min(firstId, secondId);
        long b = Math.max(firstId, secondId);
        jdbc.update("""
                insert into customer_duplicate_ignores (establishment_id, customer_id_a, customer_id_b)
                values (?, ?, ?) on conflict do nothing
                """, establishmentId, a, b);
    }

    private boolean similar(Customer left, Customer right) {
        return matches(right, PhoneNumbers.normalize(left.getPhone()), email(left.getEmail()));
    }

    private boolean matches(Customer candidate, String phone, String email) {
        return email != null && email.equals(email(candidate.getEmail()))
                || phone != null && phone.equals(PhoneNumbers.normalize(candidate.getPhone()));
    }

    private String email(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private Long establishmentId(Long administratorId) {
        return authorization.requireAdministrator(administratorId).getEstablishment().getId();
    }

    private void lockEstablishment(Long establishmentId) {
        establishments.findByIdForUpdate(establishmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Estabelecimento não encontrado."));
    }

    private Customer customer(Long id, Long establishmentId) {
        return customers.findByIdAndEstablishmentId(id, establishmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado."));
    }
}
