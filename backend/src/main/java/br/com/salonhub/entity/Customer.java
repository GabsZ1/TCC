package br.com.salonhub.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.Locale;
import br.com.salonhub.validation.PhoneNumbers;

@Entity
@Table(name = "customers", uniqueConstraints = @UniqueConstraint(
        name = "uq_customers_establishment_user", columnNames = {"establishment_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Customer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "establishment_id", nullable = false)
    private Establishment establishment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 20)
    private String phone;

    @Column(length = 150)
    private String email;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static Customer create(Establishment establishment, String name, String phone, String email) {
        Customer customer = new Customer();
        customer.establishment = establishment;
        customer.name = name;
        customer.phone = PhoneNumbers.normalize(phone);
        customer.email = normalizeEmail(email);
        return customer;
    }

    public void link(User user) { this.user = user; }

    /** Dados de contato de clientes vinculados são espelho da conta do cliente. */
    public void synchronizeFrom(User user) {
        this.name = user.getName();
        this.phone = PhoneNumbers.normalize(user.getPhone());
        this.email = normalizeEmail(user.getEmail());
    }

    public void anonymizeClosedAccount() {
        this.user = null;
        this.name = "Cliente removido";
        this.phone = null;
        this.email = null;
        this.active = false;
    }

    public void update(String name, String phone, String email, boolean active) {
        this.name = name;
        this.phone = PhoneNumbers.normalize(phone);
        this.email = normalizeEmail(email);
        this.active = active;
    }

    private static String normalizeEmail(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
