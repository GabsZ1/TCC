package br.com.salonhub.service;

import br.com.salonhub.dto.customer.CustomerRequest;
import br.com.salonhub.dto.customer.CustomerResponse;
import br.com.salonhub.dto.appointment.AppointmentResponse;
import br.com.salonhub.entity.Customer;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.exception.ResourceNotFoundException;
import br.com.salonhub.repository.CustomerRepository;
import br.com.salonhub.repository.AppointmentRepository;
import br.com.salonhub.repository.EstablishmentServiceRepository;
import br.com.salonhub.repository.UserRepository;
import br.com.salonhub.validation.PhoneNumbers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerService {
    private final CustomerRepository customers;
    private final AppointmentRepository appointments;
    private final EstablishmentServiceRepository services;
    private final UserRepository users;
    private final TenantAuthorizationService authorization;

    @Transactional(readOnly = true)
    public List<CustomerResponse> list(Long staffId,
                                       String search) {
        Long establishmentId = establishmentId(staffId);
        String term = search == null ? null : search.trim();
        boolean phoneSearch = term != null && term.matches("[0-9+()\\s-]+")
                && !term.replaceAll("\\D", "").isEmpty();
        String normalizedPhone = phoneSearch ? PhoneNumbers.normalize(term) : null;
        List<Customer> result = term == null || term.isBlank()
                ? customers.findByEstablishmentIdOrderByNameAsc(establishmentId)
                : phoneSearch
                    ? customers.findByEstablishmentIdAndPhoneContainingOrderByNameAsc(establishmentId,
                            normalizedPhone != null ? normalizedPhone : term.replaceAll("\\D", ""))
                    : customers.findByEstablishmentIdAndNameContainingIgnoreCaseOrderByNameAsc(establishmentId, term);
        return result.stream().map(CustomerResponse::fromEntity).toList();
    }

    public record BookingOption(Long id, String name) {}
    public record BookingOptions(List<BookingOption> services, List<BookingOption> professionals) {}

    @Transactional(readOnly = true)
    public BookingOptions bookingOptions(Long staffId,
                                        Long serviceId) {
        var staff = authorization.requireEstablishmentStaff(staffId);
        Long establishmentId = staff.getEstablishment().getId();
        var activeServices = services.findByEstablishmentIdAndActiveTrue(establishmentId);
        if (serviceId != null && activeServices.stream().noneMatch(service -> service.getId().equals(serviceId))) {
            throw new ResourceNotFoundException("Serviço não encontrado.");
        }
        if (staff.getRole() == UserRole.PROFESSIONAL) {
            // O profissional agenda só na própria agenda e só os serviços que ele realiza.
            return new BookingOptions(
                    activeServices.stream().filter(staff::offers)
                            .map(service -> new BookingOption(service.getId(), service.getName())).toList(),
                    serviceId == null || activeServices.stream().anyMatch(service -> service.getId().equals(serviceId) && staff.offers(service))
                            ? List.of(new BookingOption(staff.getId(), staff.getName())) : List.of());
        }
        return new BookingOptions(
                activeServices.stream()
                        .map(service -> new BookingOption(service.getId(), service.getName())).toList(),
                users.findActiveProfessionalsForService(establishmentId, UserRole.PROFESSIONAL, serviceId).stream()
                        .map(professional -> new BookingOption(professional.getId(), professional.getName())).toList());
    }

    @Transactional
    public CustomerResponse create(Long staffId, CustomerRequest request) {
        var administrator = authorization.requireEstablishmentStaff(staffId);
        Customer customer = Customer.create(administrator.getEstablishment(), request.name().trim(),
                request.phone().trim(), request.email());
        return CustomerResponse.fromEntity(customers.save(customer));
    }

    @Transactional
    public CustomerResponse update(Long staffId, Long id,
                                   CustomerRequest request) {
        Customer customer = customers.findByIdAndEstablishmentId(id, establishmentId(staffId))
                .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado."));
        if (request.active() != null && request.active() != customer.isActive())
            authorization.requireAdministrator(staffId);
        customer.update(request.name().trim(), request.phone().trim(), request.email(),
                request.active() == null ? customer.isActive() : request.active());
        return CustomerResponse.fromEntity(customer);
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> history(Long staffId, Long id) {
        Long establishmentId = establishmentId(staffId);
        customers.findByIdAndEstablishmentId(id, establishmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado."));
        return appointments.findByCustomerIdAndEstablishmentIdOrderByStartsAtDesc(id, establishmentId)
                .stream().map(AppointmentResponse::fromEntity).toList();
    }

    private Long establishmentId(Long staffId) {
        return authorization.requireEstablishmentStaff(staffId).getEstablishment().getId();
    }
}
