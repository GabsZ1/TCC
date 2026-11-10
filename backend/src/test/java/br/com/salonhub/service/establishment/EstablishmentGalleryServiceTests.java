package br.com.salonhub.service.establishment;

import br.com.salonhub.service.media.MediaService;
import br.com.salonhub.service.tenant.TenantAuthorizationService;
import br.com.salonhub.entity.Establishment;
import br.com.salonhub.entity.EstablishmentGalleryPhoto;
import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.exception.InvalidRequestException;
import br.com.salonhub.repository.EstablishmentGalleryPhotoRepository;
import br.com.salonhub.repository.EstablishmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Verifica envio, ordem e remoção das fotos da galeria. */
class EstablishmentGalleryServiceTests {
    private final TenantAuthorizationService authorization = mock(TenantAuthorizationService.class);
    private final EstablishmentRepository establishments = mock(EstablishmentRepository.class);
    private final EstablishmentGalleryPhotoRepository photos = mock(EstablishmentGalleryPhotoRepository.class);
    private final MediaService media = mock(MediaService.class);
    private final Establishment establishment = Establishment.builder().id(7L).name("Studio").cnpj("1").build();
    private final User administrator = User.builder().id(3L).name("Admin").email("admin@test.com")
            .password("x").role(UserRole.ADMIN).establishment(establishment).build();
    private EstablishmentGalleryService gallery;

    @BeforeEach
    void setUp() {
        gallery = new EstablishmentGalleryService(authorization, establishments, photos, media);
        when(authorization.requireAdministrator(3L)).thenReturn(administrator);
        when(establishments.findByIdForUpdate(7L)).thenReturn(Optional.of(establishment));
    }

    @Test
    void rejectsSixteenthPhotoBeforeStoringIt() {
        when(photos.countByEstablishmentId(7L)).thenReturn(15L);
        var file = new MockMultipartFile("file", "foto.png", "image/png", new byte[]{1});

        assertThatThrownBy(() -> gallery.upload(3L, file)).isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("15 fotos");
        verify(media, never()).storeImage(any(), any(), any());
    }

    @Test
    void rejectsIncompleteOrRepeatedReorder() {
        var first = mock(EstablishmentGalleryPhoto.class);
        var second = mock(EstablishmentGalleryPhoto.class);
        when(first.getId()).thenReturn(10L);
        when(second.getId()).thenReturn(11L);
        when(photos.findByEstablishmentIdOrderByDisplayOrderAscIdAsc(7L)).thenReturn(List.of(first, second));

        assertThatThrownBy(() -> gallery.reorder(3L, List.of(10L)))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> gallery.reorder(3L, List.of(10L, 10L)))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> gallery.reorder(3L, List.of(10L, 99L)))
                .isInstanceOf(InvalidRequestException.class);
        verify(photos, never()).flush();
    }

    @Test
    void reordersEveryPhotoUsingTheRequestedSequence() {
        var first = mock(EstablishmentGalleryPhoto.class);
        var second = mock(EstablishmentGalleryPhoto.class);
        when(first.getId()).thenReturn(10L);
        when(second.getId()).thenReturn(11L);
        when(photos.findByEstablishmentIdOrderByDisplayOrderAscIdAsc(7L))
                .thenReturn(List.of(first, second), List.of(second, first));

        var result = gallery.reorder(3L, List.of(11L, 10L));

        verify(second).reorder(0);
        verify(first).reorder(1);
        verify(photos).flush();
        assertThat(result).extracting(photo -> photo.id()).containsExactly(11L, 10L);
    }
}
