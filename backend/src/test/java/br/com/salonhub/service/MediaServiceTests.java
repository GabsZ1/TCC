package br.com.salonhub.service;

import br.com.salonhub.entity.Establishment;
import br.com.salonhub.entity.Media;
import br.com.salonhub.exception.InvalidRequestException;
import br.com.salonhub.repository.MediaRepository;
import br.com.salonhub.storage.InMemoryStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** MediaService isolado: validação, organização no storage e exclusão. */
class MediaServiceTests {

    private static final byte[] PNG = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};

    private MediaRepository repository;
    private InMemoryStorageService storage;
    private MediaService service;
    private final Establishment establishment = Establishment.builder().id(7L).name("Salão").cnpj("1").build();

    @BeforeEach
    void setUp() {
        repository = mock(MediaRepository.class);
        when(repository.save(any(Media.class))).thenAnswer(invocation -> invocation.getArgument(0));
        storage = new InMemoryStorageService();
        service = new MediaService(repository, storage, Clock.fixed(Instant.parse("2026-09-28T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void shouldStoreTheFileInTheEstablishmentFolderAndKeepOnlyMetadataInTheDatabase() {
        Media media = service.storeImage(new MockMultipartFile("file", "corte.jpg", "image/jpeg", JPEG), establishment, null);

        assertThat(media.getStorageKey()).matches("establishments/7/media/[0-9a-f-]{36}\\.jpg");
        assertThat(media.getMimeType()).isEqualTo("image/jpeg");
        assertThat(media.getSizeBytes()).isEqualTo(JPEG.length);
        assertThat(media.getOriginalFilename()).isEqualTo("corte.jpg");
        assertThat(storage.get(media.getStorageKey())).isEqualTo(JPEG);
    }

    @Test
    void shouldDetectTheRealFormatInsteadOfTrustingTheInformedOne() {
        Media media = service.storeImage(new MockMultipartFile("file", "foto.jpg", "image/jpeg", PNG), establishment, null);

        assertThat(media.getMimeType()).isEqualTo("image/png");
        assertThat(media.getStorageKey()).endsWith(".png");
    }

    @Test
    void shouldRejectInvalidFilesWithoutTouchingTheStorage() {
        assertThatThrownBy(() -> service.storeImage(
                new MockMultipartFile("file", "a.txt", "text/plain", "texto".getBytes()), establishment, null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Formato de imagem não suportado. Use JPG, PNG ou WEBP.");
        assertThatThrownBy(() -> service.storeImage(
                new MockMultipartFile("file", "vazia.png", "image/png", new byte[0]), establishment, null))
                .hasMessage("Selecione uma imagem.");
        byte[] tooLarge = new byte[(int) MediaService.MAX_IMAGE_BYTES + 1];
        System.arraycopy(PNG, 0, tooLarge, 0, PNG.length);
        assertThatThrownBy(() -> service.storeImage(
                new MockMultipartFile("file", "grande.png", "image/png", tooLarge), establishment, null))
                .hasMessage("A foto deve ter no máximo 2 MB.");

        assertThat(storage.keys()).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    void shouldRemoveTheFileFromTheStorageWhenTheMediaIsDeleted() {
        Media media = service.storeImage(new MockMultipartFile("file", "foto.png", "image/png", PNG), establishment, null);

        service.delete(media);

        verify(repository).delete(media);
        assertThat(storage.keys()).doesNotContain(media.getStorageKey());
    }
}
