package br.com.salonhub.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Metadados de um arquivo guardado no armazenamento S3 (SeaweedFS no desenvolvimento). O arquivo em si não fica
 * no banco: {@code storageKey} diz onde encontrá-lo no bucket.
 */
@Entity
@Table(name = "media")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Media {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "establishment_id")
    private Establishment establishment;

    @Column(name = "storage_key", nullable = false, unique = true, length = 255)
    private String storageKey;

    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @Column(name = "mime_type", nullable = false, length = 50)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by")
    private User uploadedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public Media(Establishment establishment, String storageKey, String originalFilename, String mimeType,
                 long sizeBytes, User uploadedBy, OffsetDateTime createdAt) {
        this.establishment = establishment;
        this.storageKey = storageKey;
        this.originalFilename = originalFilename;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.uploadedBy = uploadedBy;
        this.createdAt = createdAt;
    }
}
