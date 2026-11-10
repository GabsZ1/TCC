package br.com.salonhub.controller;

import br.com.salonhub.security.AccessRules;
import org.springframework.security.access.prepost.PreAuthorize;

import br.com.salonhub.dto.customer.CustomerRequest;
import br.com.salonhub.dto.PagedResponse;
import br.com.salonhub.dto.customer.CustomerResponse;
import br.com.salonhub.dto.appointment.AppointmentResponse;
import br.com.salonhub.service.customer.CustomerDuplicateService;
import br.com.salonhub.service.customer.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** Expõe as operações HTTP de clientes e encaminha as regras de negócio aos serviços. */
@PreAuthorize(AccessRules.ESTABLISHMENT_STAFF)
@RestController
@RequestMapping("/api/clientes/estabelecimento/me")
@RequiredArgsConstructor
public class CustomerController {
    private final CustomerService customers;
    private final CustomerDuplicateService duplicates;

    /** Sugere cadastros locais semelhantes antes de criar um novo cliente. */
    @GetMapping("/sugestoes")
    public List<CustomerResponse> suggestions(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String phone, @RequestParam(required = false) String email) {
        return duplicates.suggestionsForNew(userId(jwt), phone, email);
    }

    /** Lista possíveis duplicatas de um cliente para revisão administrativa. */
    @PreAuthorize(AccessRules.ADMIN)
    @GetMapping("/{id}/possiveis-duplicados")
    public List<CustomerDuplicateService.Candidate> duplicates(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return duplicates.candidates(userId(jwt), id);
    }

    /** Mescla dois cadastros locais após as validações do serviço. */
    @PreAuthorize(AccessRules.ADMIN)
    @PatchMapping("/{id}/mesclar/{otherId}")
    public CustomerResponse merge(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @PathVariable Long otherId) {
        return CustomerResponse.fromEntity(duplicates.merge(userId(jwt), id, otherId));
    }

    /** Registra que dois cadastros parecidos representam clientes distintos. */
    @PreAuthorize(AccessRules.ADMIN)
    @PatchMapping("/{id}/manter-separados/{otherId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void keepSeparate(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @PathVariable Long otherId) {
        duplicates.keepSeparate(userId(jwt), id, otherId);
    }

    /** Pesquisa clientes cadastrados na unidade da equipe autenticada. */
    @GetMapping
    public PagedResponse<CustomerResponse> list(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PagedResponse.from(customers.list(userId(jwt), search, active, Math.max(0, page), Math.max(1, Math.min(size, 100))));
    }

    /** Lista serviços e profissionais que a equipe pode usar no agendamento interno. */
    @GetMapping("/opcoes-agendamento")
    public CustomerService.BookingOptions bookingOptions(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) Long serviceId) {
        return customers.bookingOptions(userId(jwt), serviceId);
    }

    /** Cadastra um cliente local no estabelecimento da equipe. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CustomerRequest request) {
        return customers.create(userId(jwt), request);
    }

    /** Altera dados de um cliente do estabelecimento autorizado. */
    @PutMapping("/{id}")
    public CustomerResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
        return customers.update(userId(jwt), id, request);
    }

    /** Consulta o histórico de reservas do cliente local selecionado. */
    @GetMapping("/{id}/agendamentos")
    public PagedResponse<AppointmentResponse> history(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return PagedResponse.from(customers.history(userId(jwt), id, Math.max(0, page), Math.max(1, Math.min(size, 100))));
    }

    private Long userId(Jwt jwt) { return Long.valueOf(jwt.getSubject()); }
}
