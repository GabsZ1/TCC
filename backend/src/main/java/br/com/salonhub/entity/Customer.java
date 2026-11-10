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
/** Representa o cadastro de um cliente dentro de um estabelecimento. */
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

    /** Cria o cadastro local associado ao estabelecimento informado. */
    public static Customer create(Establishment establishment, String name, String phone, String email) {
        Customer customer = new Customer();
        customer.establishment = establishment;
        customer.name = name;
        customer.phone = PhoneNumbers.normalize(phone);
        customer.email = normalizeEmail(email);
        return customer;
    }

    /** Associa um usuário à ficha local sem mudar seu estabelecimento. */
    public void link(User user) { this.user = user; }

    /** Dados de contato de clientes vinculados são espelho da conta do cliente. */
    public void synchronizeFrom(User user) {
        this.name = user.getName();
        this.phone = PhoneNumbers.normalize(user.getPhone());
        this.email = normalizeEmail(user.getEmail());
    }

    /** Remove dados pessoais da ficha após o encerramento da conta vinculada. */
    public void anonymizeClosedAccount() {
        this.user = null;
        this.name = "Cliente removido";
        this.phone = null;
        this.email = null;
        this.active = false;
    }

    /** Altera os dados do cadastro local e seu estado de atividade. */
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
