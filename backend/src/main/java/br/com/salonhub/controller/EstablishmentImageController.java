package br.com.salonhub.controller;

import br.com.salonhub.security.AccessRules;
import org.springframework.security.access.prepost.PreAuthorize;

import br.com.salonhub.service.establishment.EstablishmentImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Expõe as operações HTTP de estabelecimentos e encaminha as regras de negócio aos serviços. */
@PreAuthorize(AccessRules.ADMIN)
@RestController
@RequestMapping("/api/estabelecimentos")
@RequiredArgsConstructor
public class EstablishmentImageController {
    private final EstablishmentImageService images;

    /** Substitui capa ou logotipo do perfil da unidade. */
    @PostMapping(value = "/me/perfil/imagens/{kind}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> upload(@AuthenticationPrincipal Jwt jwt, @PathVariable String kind,
            @RequestPart("file") MultipartFile file) {
        images.upload(Long.valueOf(jwt.getSubject()), kind, file);
        return ResponseEntity.noContent().build();
    }

    /** Remove capa ou logotipo do perfil da unidade. */
    @DeleteMapping("/me/perfil/imagens/{kind}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String kind) {
        images.delete(Long.valueOf(jwt.getSubject()), kind);
        return ResponseEntity.noContent().build();
    }

    /** Entrega uma imagem do próprio estabelecimento ao administrador. */
    @GetMapping("/me/perfil/imagens/{kind}")
    public ResponseEntity<byte[]> mine(@AuthenticationPrincipal Jwt jwt, @PathVariable String kind) {
        var image = images.findMine(Long.valueOf(jwt.getSubject()), kind);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType())).body(image.data());
    }

    /** Entrega uma imagem do perfil público ao cliente. */
    @PreAuthorize(AccessRules.AUTHENTICATED)
    @GetMapping("/{establishmentId}/perfil/imagens/{kind}")
    public ResponseEntity<byte[]> publicImage(@PathVariable Long establishmentId, @PathVariable String kind) {
        var image = images.findPublic(establishmentId, kind);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType())).body(image.data());
    }
}
