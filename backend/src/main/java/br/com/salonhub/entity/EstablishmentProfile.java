package br.com.salonhub.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.Objects;

/** Guarda os dados públicos apresentados no perfil do estabelecimento. */
@Entity
@Table(name = "establishment_profiles")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class EstablishmentProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "establishment_id", nullable = false, unique = true)
    private Establishment establishment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "logo_id")
    private Media logo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cover_id")
    private Media cover;

    /** Escolhe capa ou logotipo conforme o tipo solicitado. */
    public Media image(String kind) { return "logo".equals(kind) ? logo : cover; }
    /** Substitui a referência à mídia da capa ou do logotipo. */
    public void replaceImage(String kind, Media image) {
        if ("logo".equals(kind)) logo = image;
        else cover = image;
    }

    @Column(name = "about_us", columnDefinition = "TEXT")
    private String aboutUs;

    @Column(length = 100)
    private String instagram;

    @Column(length = 20)
    private String whatsapp;

    @Column(length = 20)
    private String phone;

    @Column(name = "postal_code", length = 8)
    private String postalCode;

    @Column(length = 150)
    private String street;

    @Column(name = "street_number", length = 20)
    private String streetNumber;

    @Column(name = "address_complement", length = 100)
    private String addressComplement;

    @Column(length = 100)
    private String neighborhood;

    @Column(length = 100)
    private String city;

    @Column(length = 2)
    private String state;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Invalida as coordenadas antigas quando qualquer parte relevante do endereço muda. */
    public void update(
            String aboutUs,
            String instagram,
            String whatsapp,
            String phone,
            String postalCode,
            String street,
            String streetNumber,
            String addressComplement,
            String neighborhood,
            String city,
            String state) {
        boolean addressChanged = !Objects.equals(this.postalCode, postalCode) || !Objects.equals(this.street, street)
                || !Objects.equals(this.streetNumber, streetNumber) || !Objects.equals(this.neighborhood, neighborhood)
                || !Objects.equals(this.city, city) || !Objects.equals(this.state, state);
        this.aboutUs = aboutUs;
        this.instagram = instagram;
        this.whatsapp = whatsapp;
        this.phone = phone;
        this.postalCode = postalCode;
        this.street = street;
        this.streetNumber = streetNumber;
        this.addressComplement = addressComplement;
        this.neighborhood = neighborhood;
        this.city = city;
        this.state = state;
        if (addressChanged) { latitude = null; longitude = null; }
    }

    /** Indica se o endereço já possui as duas coordenadas necessárias para a busca próxima. */
    public boolean hasLocation() { return latitude != null && longitude != null; }
    /** Grava as coordenadas calculadas a partir do endereço atual. */
    public void updateLocation(BigDecimal latitude, BigDecimal longitude) { this.latitude = latitude; this.longitude = longitude; }
}
