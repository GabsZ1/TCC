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

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

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
    }
}
