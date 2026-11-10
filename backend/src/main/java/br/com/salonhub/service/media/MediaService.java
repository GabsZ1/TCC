package br.com.salonhub.service.media;

import br.com.salonhub.entity.Establishment;
import br.com.salonhub.entity.Media;
import br.com.salonhub.entity.User;
import br.com.salonhub.exception.InvalidRequestException;
import br.com.salonhub.repository.MediaRepository;
import br.com.salonhub.storage.StorageException;
import br.com.salonhub.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.UUID;

/**
 * Upload, leitura e exclusão de imagens. Coordena o armazenamento físico
 * ({@link StorageService}) com o registro de metadados (tabela media):
 * <ul>
 *   <li>valida tamanho (até 2 MB) e formato pela assinatura do arquivo (JPG, PNG, WEBP);</li>
 *   <li>grava o arquivo como {@code establishments/{id}/media/{uuid}.{ext}};</li>
 *   <li>se a transação for desfeita depois do upload, o arquivo é removido do storage;</li>
 *   <li>na exclusão, o arquivo só é removido do storage depois do commit.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class MediaService {

    static final long MAX_IMAGE_BYTES = 2L * 1024 * 1024;
    private static final Logger log = LoggerFactory.getLogger(MediaService.class);

    private final MediaRepository mediaRepository;
    private final StorageService storageService;
    private final Clock clock;

    /** Valida o arquivo e seus limites antes de registrar a mídia e gravar o conteúdo. */
    @Transactional
    public Media storeImage(MultipartFile file, Establishment establishment, User uploadedBy) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("Selecione uma imagem.");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new InvalidRequestException("A foto deve ter no máximo 2 MB.");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new InvalidRequestException("Não foi possível ler a imagem enviada.");
        }
        ImageType type = ImageType.detect(content);
        if (type == null) {
            throw new InvalidRequestException("Formato de imagem não suportado. Use JPG, PNG ou WEBP.");
        }

        String key = "establishments/" + (establishment == null ? "shared" : establishment.getId())
                + "/media/" + UUID.randomUUID() + "." + type.extension;
        storageService.put(key, content, type.mimeType);
        removeFromStorageIfRolledBack(key);

        return mediaRepository.save(new Media(
                establishment, key, originalFilename(file), type.mimeType, content.length, uploadedBy,
                OffsetDateTime.now(clock)));
    }

    /** Recupera o conteúdo da mídia pelo identificador de armazenamento. */
    public byte[] load(Media media) {
        return storageService.get(media.getStorageKey());
    }

    /** Remove o conteúdo armazenado e o registro correspondente da mídia. */
    @Transactional
    public void delete(Media media) {
        String key = media.getStorageKey();
        mediaRepository.delete(media);
        afterCommit(() -> {
            try {
                storageService.delete(key);
            } catch (StorageException exception) {
                log.warn("Imagem {} removida do banco, mas não do storage: {}", key, exception.getMessage());
            }
        });
    }

    private void removeFromStorageIfRolledBack(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try {
                        storageService.delete(key);
                    } catch (StorageException exception) {
                        log.warn("Não foi possível remover a imagem órfã {}: {}", key, exception.getMessage());
                    }
                }
            }
        });
    }

    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private static String originalFilename(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            return null;
        }
        String clean = name.replaceAll("[\\\\/]", "_").trim();
        return clean.length() > 255 ? clean.substring(clean.length() - 255) : clean;
    }

    enum ImageType {
        JPEG("image/jpeg", "jpg"),
        PNG("image/png", "png"),
        WEBP("image/webp", "webp");

        final String mimeType;
        final String extension;

        ImageType(String mimeType, String extension) {
            this.mimeType = mimeType;
            this.extension = extension;
        }

        static ImageType detect(byte[] data) {
            if (startsWith(data, 0xFF, 0xD8, 0xFF)) {
                return JPEG;
            }
            if (startsWith(data, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
                return PNG;
            }
            if (data.length >= 12 && startsWith(data, 'R', 'I', 'F', 'F')
                    && Arrays.equals(Arrays.copyOfRange(data, 8, 12), new byte[]{'W', 'E', 'B', 'P'})) {
                return WEBP;
            }
            return null;
        }

        private static boolean startsWith(byte[] data, int... signature) {
            if (data.length < signature.length) {
                return false;
            }
            for (int index = 0; index < signature.length; index++) {
                if ((data[index] & 0xFF) != signature[index]) {
                    return false;
                }
            }
            return true;
        }
    }
}
