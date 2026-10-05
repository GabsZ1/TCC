package br.com.salonhub.controller;

import br.com.salonhub.security.AccessRules;
import org.springframework.security.access.prepost.PreAuthorize;

import br.com.salonhub.dto.establishment.GalleryPhotoResponse;
import br.com.salonhub.dto.establishment.GalleryReorderRequest;
import br.com.salonhub.service.EstablishmentGalleryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@PreAuthorize(AccessRules.ADMIN)
@RestController
@RequestMapping("/api/estabelecimentos")
@RequiredArgsConstructor
public class EstablishmentGalleryController {
    private final EstablishmentGalleryService gallery;

    @PreAuthorize(AccessRules.AUTHENTICATED)
    @GetMapping("/{establishmentId}/perfil/galeria")
    public List<GalleryPhotoResponse> publicPhotos(@PathVariable Long establishmentId) {
        return gallery.findPublic(establishmentId);
    }

    @PreAuthorize(AccessRules.AUTHENTICATED)
    @GetMapping("/{establishmentId}/perfil/galeria/{photoId}")
    public ResponseEntity<byte[]> publicImage(@PathVariable Long establishmentId, @PathVariable Long photoId) {
        var image = gallery.findPublicImage(establishmentId, photoId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType())).body(image.data());
    }

    @GetMapping("/me/perfil/galeria")
    public List<GalleryPhotoResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        return gallery.findMine(Long.valueOf(jwt.getSubject()));
    }

    @GetMapping("/me/perfil/galeria/{photoId}")
    public ResponseEntity<byte[]> myImage(@AuthenticationPrincipal Jwt jwt, @PathVariable Long photoId) {
        var image = gallery.findMineImage(Long.valueOf(jwt.getSubject()), photoId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType())).body(image.data());
    }

    @PostMapping(value = "/me/perfil/galeria", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public GalleryPhotoResponse upload(@AuthenticationPrincipal Jwt jwt, @RequestPart("file") MultipartFile file) {
        return gallery.upload(Long.valueOf(jwt.getSubject()), file);
    }

    @PutMapping("/me/perfil/galeria/ordem")
    public List<GalleryPhotoResponse> reorder(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody GalleryReorderRequest request) {
        return gallery.reorder(Long.valueOf(jwt.getSubject()), request.photoIds());
    }

    @DeleteMapping("/me/perfil/galeria/{photoId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long photoId) {
        gallery.delete(Long.valueOf(jwt.getSubject()), photoId);
        return ResponseEntity.noContent().build();
    }
}
