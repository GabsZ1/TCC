package br.com.salonhub.controller;

import br.com.salonhub.security.AccessRules;
import org.springframework.security.access.prepost.PreAuthorize;
import br.com.salonhub.dto.establishment.*;
import br.com.salonhub.service.establishment.ActiveEstablishmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
/** Expõe as operações HTTP de estabelecimentos e encaminha as regras de negócio aos serviços. */
@PreAuthorize(AccessRules.AUTHENTICATED)
@RestController @RequestMapping("/api/estabelecimentos") @RequiredArgsConstructor
public class ActiveEstablishmentController {
    private final ActiveEstablishmentService activeEstablishmentService;
    @GetMapping public List<ActiveEstablishmentResponse> findAllActive() { return activeEstablishmentService.findAll(); }
    @PostMapping("/proximos") public List<NearbyEstablishmentResponse> findNearby(@Valid @RequestBody NearbyEstablishmentsRequest r) {
        return activeEstablishmentService.findNearby(r.latitude(), r.longitude());
    }
}