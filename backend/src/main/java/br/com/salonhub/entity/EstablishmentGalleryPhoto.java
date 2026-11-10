package br.com.salonhub.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Representa uma foto e sua posição na galeria pública. */
@Entity
@Table(name = "establishment_gallery_photos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EstablishmentGalleryPhoto {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "establishment_id", nullable = false)
    private Establishment establishment;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_id", nullable = false, unique = true)
    private Media media;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public EstablishmentGalleryPhoto(Establishment establishment, Media media, int displayOrder) {
        this.establishment = establishment;
        this.media = media;
        this.displayOrder = displayOrder;
    }

    /** Atualiza a posição da foto após uma reorganização completa da galeria. */
    public void reorder(int displayOrder) { this.displayOrder = displayOrder; }
}
