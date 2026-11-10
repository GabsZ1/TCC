package br.com.salonhub.service.establishment;

import br.com.salonhub.service.media.MediaService;
import br.com.salonhub.service.tenant.TenantAuthorizationService;
import br.com.salonhub.dto.establishment.GalleryPhotoResponse;
import br.com.salonhub.entity.Establishment;
import br.com.salonhub.entity.EstablishmentGalleryPhoto;
import br.com.salonhub.exception.InvalidRequestException;
import br.com.salonhub.exception.ResourceNotFoundException;
import br.com.salonhub.repository.EstablishmentGalleryPhotoRepository;
import br.com.salonhub.repository.EstablishmentRepository;
import br.com.salonhub.storage.StorageException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Gerencia as fotos e a ordem de exibição da galeria pública. */
@Service
@RequiredArgsConstructor
public class EstablishmentGalleryService {
    private static final int MAX_PHOTOS = 15;
    private final TenantAuthorizationService authorization;
    private final EstablishmentRepository establishments;
    private final EstablishmentGalleryPhotoRepository photos;
    private final MediaService media;

    public record Image(String contentType, byte[] data) {}

    /** Mostra a galeria somente quando o estabelecimento pode receber clientes. */
    @Transactional(readOnly = true)
    public List<GalleryPhotoResponse> findPublic(Long establishmentId) {
        publicEstablishment(establishmentId);
        return list(establishmentId);
    }

    /** Lista as fotos da própria galeria para o administrador reorganizá-las. */
    @Transactional(readOnly = true)
    public List<GalleryPhotoResponse> findMine(Long administratorId) {
        return list(authorization.requireAdministrator(administratorId).getEstablishment().getId());
    }

    /** Respeita o limite da galeria e grava a nova foto na última posição. */
    @Transactional
    public GalleryPhotoResponse upload(Long administratorId, MultipartFile file) {
        var administrator = authorization.requireAdministrator(administratorId);
        Establishment establishment = lockMine(administrator.getEstablishment().getId());
        long count = photos.countByEstablishmentId(establishment.getId());
        if (count >= MAX_PHOTOS) throw new InvalidRequestException("A galeria aceita no máximo 15 fotos.");
        var stored = media.storeImage(file, establishment, administrator);
        var photo = photos.save(new EstablishmentGalleryPhoto(establishment, stored, (int) count));
        return new GalleryPhotoResponse(photo.getId(), photo.getDisplayOrder());
    }

    /** Remove foto e mídia, depois recompõe a sequência de posições da galeria. */
    @Transactional
    public void delete(Long administratorId, Long photoId) {
        Long establishmentId = authorization.requireAdministrator(administratorId).getEstablishment().getId();
        lockMine(establishmentId);
        var photo = photos.findByIdAndEstablishmentId(photoId, establishmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Foto não encontrada."));
        var stored = photo.getMedia();
        photos.delete(photo);
        photos.flush();
        media.delete(stored);
        normalizeOrder(establishmentId);
    }

    /** Aceita uma permutação completa dos IDs da própria galeria para evitar perdas de posição. */
    @Transactional
    public List<GalleryPhotoResponse> reorder(Long administratorId, List<Long> photoIds) {
        Long establishmentId = authorization.requireAdministrator(administratorId).getEstablishment().getId();
        lockMine(establishmentId);
        var current = photos.findByEstablishmentIdOrderByDisplayOrderAscIdAsc(establishmentId);
        if (photoIds == null || photoIds.size() != current.size() || photoIds.stream().distinct().count() != current.size())
            throw new InvalidRequestException("Envie todas as fotos da galeria uma única vez.");
        Map<Long, EstablishmentGalleryPhoto> byId = current.stream()
                .collect(Collectors.toMap(EstablishmentGalleryPhoto::getId, Function.identity()));
        for (int index = 0; index < photoIds.size(); index++) {
            var photo = byId.get(photoIds.get(index));
            if (photo == null) throw new InvalidRequestException("A lista contém uma foto de outra galeria.");
            photo.reorder(index);
        }
        photos.flush();
        return list(establishmentId);
    }

    /** Carrega uma foto pública após validar o estado do estabelecimento. */
    @Transactional(readOnly = true)
    public Image findPublicImage(Long establishmentId, Long photoId) {
        publicEstablishment(establishmentId);
        return load(photoId, establishmentId);
    }

    /** Carrega uma foto da galeria no escopo do administrador autenticado. */
    @Transactional(readOnly = true)
    public Image findMineImage(Long administratorId, Long photoId) {
        return load(photoId, authorization.requireAdministrator(administratorId).getEstablishment().getId());
    }

    private Image load(Long photoId, Long establishmentId) {
        var photo = photos.findByIdAndEstablishmentId(photoId, establishmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Foto não encontrada."));
        try { return new Image(photo.getMedia().getMimeType(), media.load(photo.getMedia())); }
        catch (StorageException exception) {
            if (exception.isNotFound()) throw new ResourceNotFoundException("Foto não encontrada.");
            throw exception;
        }
    }

    private List<GalleryPhotoResponse> list(Long establishmentId) {
        return photos.findByEstablishmentIdOrderByDisplayOrderAscIdAsc(establishmentId).stream()
                .map(photo -> new GalleryPhotoResponse(photo.getId(), photo.getDisplayOrder())).toList();
    }

    private void normalizeOrder(Long establishmentId) {
        var remaining = photos.findByEstablishmentIdOrderByDisplayOrderAscIdAsc(establishmentId);
        for (int index = 0; index < remaining.size(); index++) remaining.get(index).reorder(index);
    }

    private Establishment lockMine(Long establishmentId) {
        return establishments.findByIdForUpdate(establishmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Estabelecimento não encontrado."));
    }

    private void publicEstablishment(Long establishmentId) {
        establishments.findById(establishmentId).filter(Establishment::acceptsNewAppointments)
                .orElseThrow(() -> new ResourceNotFoundException("Estabelecimento não encontrado."));
    }
}
