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

    @GetMapping
    @Transactional(readOnly = true)
    public List<CustomerResponse> list(@AuthenticationPrincipal Jwt jwt,
                                       @RequestParam(required = false) String search) {
        Long establishmentId = establishmentId(jwt);
        List<Customer> result = search == null || search.isBlank()
                ? customers.findByEstablishmentIdOrderByNameAsc(establishmentId)
                : search.matches("[0-9]+")
                    ? customers.findByEstablishmentIdAndPhoneContainingOrderByNameAsc(establishmentId, search)
                    : customers.findByEstablishmentIdAndNameContainingIgnoreCaseOrderByNameAsc(establishmentId, search);
        return result.stream().map(CustomerResponse::fromEntity).toList();
    }

    public record BookingOption(Long id, String name) {}
    public record BookingOptions(List<BookingOption> services, List<BookingOption> professionals) {}

    @GetMapping("/opcoes-agendamento")
    @Transactional(readOnly = true)
    public BookingOptions bookingOptions(@AuthenticationPrincipal Jwt jwt) {
        var staff = authorization.requireEstablishmentStaff(Long.valueOf(jwt.getSubject()));
        Long establishmentId = staff.getEstablishment().getId();
        var activeServices = services.findByEstablishmentIdAndActiveTrue(establishmentId);
        if (staff.getRole() == UserRole.PROFESSIONAL) {
            // O profissional agenda só na própria agenda e só os serviços que ele realiza.
            return new BookingOptions(
                    activeServices.stream().filter(staff::offers)
                            .map(service -> new BookingOption(service.getId(), service.getName())).toList(),
                    List.of(new BookingOption(staff.getId(), staff.getName())));
        }
        return new BookingOptions(
                activeServices.stream()
                        .map(service -> new BookingOption(service.getId(), service.getName())).toList(),
                users.findByEstablishmentIdAndRoleAndActiveTrue(establishmentId, UserRole.PROFESSIONAL).stream()
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
