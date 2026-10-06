package br.com.salonhub.controller;

import br.com.salonhub.security.AccessRules;
import org.springframework.security.access.prepost.PreAuthorize;

import br.com.salonhub.dto.customer.CustomerRequest;
import br.com.salonhub.dto.PagedResponse;
import br.com.salonhub.dto.customer.CustomerResponse;
import br.com.salonhub.dto.appointment.AppointmentResponse;
import br.com.salonhub.service.CustomerDuplicateService;
import br.com.salonhub.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@PreAuthorize(AccessRules.ESTABLISHMENT_STAFF)
@RestController
@RequestMapping("/api/clientes/estabelecimento/me")
@RequiredArgsConstructor
public class CustomerController {
    private final CustomerService customers;
    private final CustomerDuplicateService duplicates;

    @GetMapping("/sugestoes")
    public List<CustomerResponse> suggestions(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String phone, @RequestParam(required = false) String email) {
        return duplicates.suggestionsForNew(userId(jwt), phone, email);
    }

    @PreAuthorize(AccessRules.ADMIN)
    @GetMapping("/{id}/possiveis-duplicados")
    public List<CustomerDuplicateService.Candidate> duplicates(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return duplicates.candidates(userId(jwt), id);
    }

    @PreAuthorize(AccessRules.ADMIN)
    @PatchMapping("/{id}/mesclar/{otherId}")
    public CustomerResponse merge(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @PathVariable Long otherId) {
        return CustomerResponse.fromEntity(duplicates.merge(userId(jwt), id, otherId));
    }

    @PreAuthorize(AccessRules.ADMIN)
    @PatchMapping("/{id}/manter-separados/{otherId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void keepSeparate(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @PathVariable Long otherId) {
        duplicates.keepSeparate(userId(jwt), id, otherId);
    }

    @GetMapping
    public PagedResponse<CustomerResponse> list(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PagedResponse.from(customers.list(userId(jwt), search, active, Math.max(0, page), Math.max(1, Math.min(size, 100))));
    }

    @GetMapping("/opcoes-agendamento")
    public CustomerService.BookingOptions bookingOptions(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) Long serviceId) {
        return customers.bookingOptions(userId(jwt), serviceId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CustomerRequest request) {
        return customers.create(userId(jwt), request);
    }

    @PutMapping("/{id}")
    public CustomerResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
        return customers.update(userId(jwt), id, request);
    }

    @GetMapping("/{id}/agendamentos")
    public PagedResponse<AppointmentResponse> history(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return PagedResponse.from(customers.history(userId(jwt), id, Math.max(0, page), Math.max(1, Math.min(size, 100))));
    }

    private Long userId(Jwt jwt) { return Long.valueOf(jwt.getSubject()); }
}
