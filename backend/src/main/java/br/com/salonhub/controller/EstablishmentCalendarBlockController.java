package br.com.salonhub.controller;

import br.com.salonhub.security.AccessRules;
import org.springframework.security.access.prepost.PreAuthorize;

import br.com.salonhub.service.EstablishmentCalendarBlockService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@PreAuthorize(AccessRules.ADMIN)
@RestController
@RequestMapping("/api/estabelecimentos/me/bloqueios")
@RequiredArgsConstructor
public class EstablishmentCalendarBlockController {
    private final EstablishmentCalendarBlockService blocks;

    @GetMapping
    public List<EstablishmentCalendarBlockService.Response> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startsAt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endsAt) {
        return blocks.list(Long.valueOf(jwt.getSubject()), startsAt, endsAt);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EstablishmentCalendarBlockService.Response create(@AuthenticationPrincipal Jwt jwt,
            @RequestBody EstablishmentCalendarBlockService.Request request) {
        return blocks.create(Long.valueOf(jwt.getSubject()), request);
    }

    @PutMapping("/{id}")
    public EstablishmentCalendarBlockService.Response update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @RequestBody EstablishmentCalendarBlockService.Request request) {
        return blocks.update(Long.valueOf(jwt.getSubject()), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        blocks.delete(Long.valueOf(jwt.getSubject()), id);
    }
}
