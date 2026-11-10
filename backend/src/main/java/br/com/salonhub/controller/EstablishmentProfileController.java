package br.com.salonhub.controller;

import br.com.salonhub.security.AccessRules;
import org.springframework.security.access.prepost.PreAuthorize;

import br.com.salonhub.dto.establishment.EstablishmentProfileRequest;
import br.com.salonhub.dto.establishment.EstablishmentProfileResponse;
import br.com.salonhub.service.establishment.EstablishmentProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Expõe as operações HTTP de estabelecimentos e encaminha as regras de negócio aos serviços. */
@PreAuthorize(AccessRules.ADMIN)
@RestController
@RequestMapping("/api/estabelecimentos")
@RequiredArgsConstructor
public class EstablishmentProfileController {

    private final EstablishmentProfileService profileService;

    /** Consulta o perfil público de um estabelecimento disponível. */
    @PreAuthorize(AccessRules.AUTHENTICATED)
    @GetMapping("/{establishmentId}/perfil")
    public EstablishmentProfileResponse findPublic(@PathVariable Long establishmentId) {
        return profileService.findPublic(establishmentId);
    }

    /** Consulta o perfil da própria unidade para edição. */
    @GetMapping("/me/perfil")
    public EstablishmentProfileResponse findMine(@AuthenticationPrincipal Jwt jwt) {
        return profileService.findMine(Long.valueOf(jwt.getSubject()));
    }

    /** Salva as informações públicas da unidade administrada. */
    @PutMapping("/me/perfil")
    public EstablishmentProfileResponse updateMine(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody EstablishmentProfileRequest request) {
        return profileService.updateMine(Long.valueOf(jwt.getSubject()), request);
    }
}
