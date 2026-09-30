package br.com.salonhub.controller;

import br.com.salonhub.dto.customer.CustomerRequest;
import br.com.salonhub.dto.customer.CustomerResponse;
import br.com.salonhub.dto.appointment.AppointmentResponse;
import br.com.salonhub.entity.Customer;
import br.com.salonhub.exception.ResourceNotFoundException;
import br.com.salonhub.repository.CustomerRepository;
import br.com.salonhub.repository.AppointmentRepository;
import br.com.salonhub.repository.EstablishmentServiceRepository;
import br.com.salonhub.repository.UserRepository;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.service.TenantAuthorizationService;
import br.com.salonhub.service.CustomerDuplicateService;
import br.com.salonhub.validation.PhoneNumbers;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clientes/estabelecimento/me")
@RequiredArgsConstructor
public class CustomerController {
    private final CustomerRepository customers;
    private final AppointmentRepository appointments;
    private final EstablishmentServiceRepository services;
    private final UserRepository users;
    private final TenantAuthorizationService authorization;
    private final CustomerDuplicateService duplicates;

    @GetMapping("/sugestoes")
    public List<CustomerResponse> suggestions(@AuthenticationPrincipal Jwt jwt,
                                              @RequestParam(required = false) String phone,
                                              @RequestParam(required = false) String email) {
        return duplicates.suggestionsForNew(Long.valueOf(jwt.getSubject()), phone, email);
    }

    @GetMapping("/{id}/possiveis-duplicados")
    public List<CustomerDuplicateService.Candidate> duplicates(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return duplicates.candidates(Long.valueOf(jwt.getSubject()), id);
    }

    @PatchMapping("/{id}/mesclar/{otherId}")
    public CustomerResponse merge(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @PathVariable Long otherId) {
        return CustomerResponse.fromEntity(duplicates.merge(Long.valueOf(jwt.getSubject()), id, otherId));
    }

    @PatchMapping("/{id}/manter-separados/{otherId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void keepSeparate(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @PathVariable Long otherId) {
        duplicates.keepSeparate(Long.valueOf(jwt.getSubject()), id, otherId);
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<CustomerResponse> list(@AuthenticationPrincipal Jwt jwt,
                                       @RequestParam(required = false) String search) {
        Long establishmentId = establishmentId(jwt);
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

    @GetMapping("/opcoes-agendamento")
    @Transactional(readOnly = true)
    public BookingOptions bookingOptions(@AuthenticationPrincipal Jwt jwt,
                                        @RequestParam(required = false) Long serviceId) {
        var staff = authorization.requireEstablishmentStaff(Long.valueOf(jwt.getSubject()));
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public CustomerResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CustomerRequest request) {
        var administrator = authorization.requireEstablishmentStaff(Long.valueOf(jwt.getSubject()));
        Customer customer = Customer.create(administrator.getEstablishment(), request.name().trim(),
                request.phone().trim(), request.email());
        return CustomerResponse.fromEntity(customers.save(customer));
    }

    @PutMapping("/{id}")
    @Transactional
    public CustomerResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                   @Valid @RequestBody CustomerRequest request) {
        Customer customer = customers.findByIdAndEstablishmentId(id, establishmentId(jwt))
                .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado."));
        customer.update(request.name().trim(), request.phone().trim(), request.email(),
                request.active() == null || request.active());
        return CustomerResponse.fromEntity(customer);
    }

    @GetMapping("/{id}/agendamentos")
    @Transactional(readOnly = true)
    public List<AppointmentResponse> history(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        Long establishmentId = establishmentId(jwt);
        customers.findByIdAndEstablishmentId(id, establishmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado."));
        return appointments.findByCustomerIdAndEstablishmentIdOrderByStartsAtDesc(id, establishmentId)
                .stream().map(AppointmentResponse::fromEntity).toList();
    }

    private Long establishmentId(Jwt jwt) {
        return authorization.requireEstablishmentStaff(Long.valueOf(jwt.getSubject())).getEstablishment().getId();
    }
}
