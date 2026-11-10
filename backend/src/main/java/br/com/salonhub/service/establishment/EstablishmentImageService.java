package br.com.salonhub.service.establishment;

import br.com.salonhub.service.media.MediaService;
import br.com.salonhub.service.tenant.TenantAuthorizationService;
import br.com.salonhub.entity.EstablishmentProfile;
import br.com.salonhub.entity.Media;
import br.com.salonhub.exception.InvalidRequestException;
import br.com.salonhub.exception.ResourceNotFoundException;
import br.com.salonhub.repository.EstablishmentProfileRepository;
import br.com.salonhub.repository.EstablishmentRepository;
import br.com.salonhub.storage.StorageException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Gerencia as imagens de capa e logotipo do estabelecimento. */
@Service
@RequiredArgsConstructor
public class EstablishmentImageService {
    private final TenantAuthorizationService authorization;
    private final EstablishmentProfileRepository profiles;
    private final EstablishmentRepository establishments;
    private final MediaService media;

    public record Image(String contentType, byte[] data) {}

    /** Substitui capa ou logotipo e delega a validação do arquivo ao serviço de mídia. */
    @Transactional
    public void upload(Long administratorId, String kind, MultipartFile file) {
        validateKind(kind);
        var administrator = authorization.requireAdministrator(administratorId);
        var establishment = administrator.getEstablishment();
        EstablishmentProfile profile = profiles.findByEstablishmentId(establishment.getId())
                .orElseGet(() -> profiles.save(EstablishmentProfile.builder().establishment(establishment).build()));
        Media previous = profile.image(kind);
        Media uploaded = media.storeImage(file, establishment, administrator);
        profile.replaceImage(kind, uploaded);
        profiles.flush();
        if (previous != null) media.delete(previous);
    }

    /** Remove capa ou logotipo e apaga a mídia que deixou de ser referenciada. */
    @Transactional
    public void delete(Long administratorId, String kind) {
        validateKind(kind);
        var establishment = authorization.requireAdministrator(administratorId).getEstablishment();
        EstablishmentProfile profile = profiles.findByEstablishmentId(establishment.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Imagem não encontrada."));
        Media previous = profile.image(kind);
        if (previous == null) throw new ResourceNotFoundException("Imagem não encontrada.");
        profile.replaceImage(kind, null);
        profiles.flush();
        media.delete(previous);
    }

    /** Carrega apenas imagens de um perfil de estabelecimento acessível ao público. */
    @Transactional(readOnly = true)
    public Image findPublic(Long establishmentId, String kind) {
        validateKind(kind);
        establishments.findById(establishmentId).filter(e -> e.acceptsNewAppointments())
                .orElseThrow(() -> new ResourceNotFoundException("Estabelecimento não encontrado."));
        Media image = profiles.findByEstablishmentId(establishmentId).map(p -> p.image(kind))
                .orElse(null);
        if (image == null) throw new ResourceNotFoundException("Imagem não encontrada.");
        try { return new Image(image.getMimeType(), media.load(image)); }
        catch (StorageException exception) {
            if (exception.isNotFound()) throw new ResourceNotFoundException("Imagem não encontrada.");
            throw exception;
        }
    }

    /** Carrega a imagem da própria unidade para a tela administrativa. */
    @Transactional(readOnly = true)
    public Image findMine(Long administratorId, String kind) {
        Long establishmentId = authorization.requireAdministrator(administratorId).getEstablishment().getId();
        validateKind(kind);
        Media image = profiles.findByEstablishmentId(establishmentId).map(p -> p.image(kind))
                .orElse(null);
        if (image == null) throw new ResourceNotFoundException("Imagem não encontrada.");
        return new Image(image.getMimeType(), media.load(image));
    }

    private void validateKind(String kind) {
        if (!"logo".equals(kind) && !"capa".equals(kind))
            throw new InvalidRequestException("Tipo de imagem inválido.");
    }
}
