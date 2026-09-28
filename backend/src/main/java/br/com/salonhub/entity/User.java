package br.com.salonhub.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "establishment_id")
    private Establishment establishment;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(length = 20)
    private String phone;

    @Column(length = 11, unique = true)
    private String cpf;

    @Column(length = 80)
    private String occupation;

    @Column(length = 30)
    private String instagram;

    @Column(length = 255)
    private String address;

    @Column(name = "hire_date")
    private java.time.LocalDate hireDate;

    /** Foto do funcionário (arquivo no armazenamento S3; metadados na tabela media). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "photo_id", unique = true)
    private Media photo;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "password_initialized", nullable = false)
    @Builder.Default
    private boolean passwordInitialized = true;

    @Column(name = "offers_all_services", nullable = false)
    @Builder.Default
    private boolean offersAllServices = true;

    @ManyToMany
    @JoinTable(
            name = "professional_services",
            joinColumns = @JoinColumn(name = "professional_id"),
            inverseJoinColumns = @JoinColumn(name = "service_id"))
    @OrderBy("name ASC")
    @Builder.Default
    private Set<EstablishmentService> offeredServices = new LinkedHashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public void updateProfile(String name, String email) {
        this.name = name;
        this.email = email;
    }

    public void initializePassword(String encodedPassword) {
        this.password = encodedPassword;
        this.passwordInitialized = true;
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void replacePhoto(Media photo) {
        this.photo = photo;
    }

    /** Nome do funcionário: continua sendo alterado pelo administrador. */
    public void renameEmployee(String name) {
        this.name = name;
    }

    /** CPF e data de admissão: só podem ser gravados enquanto estiverem vazios. */
    public void fillEmployeeDocuments(String cpf, java.time.LocalDate hireDate) {
        if (cpf != null) {
            this.cpf = cpf;
        }
        if (hireDate != null) {
            this.hireDate = hireDate;
        }
    }

    /** Dados que o próprio funcionário administra no perfil dele. */
    public void updateOwnEmployeeProfile(
            String email, String occupation, String phone, String instagram, String address) {
        this.email = email;
        this.occupation = occupation;
        this.phone = phone;
        this.instagram = instagram;
        this.address = address;
    }

    public void updateClientProfile(String name, String email, String phone) {
        this.name = name;
        this.email = email;
        this.phone = phone;
    }

    /**
     * US 2.26 — encerra a conta do cliente sem apagar a linha: os agendamentos do
     * estabelecimento continuam apontando para ela, mas os dados pessoais são
     * substituídos e o e-mail original fica livre para um novo cadastro.
     */
    public void closeClientAccount(String anonymizedEmail, String unusablePasswordHash, OffsetDateTime closedAt) {
        this.name = "Cliente removido";
        this.email = anonymizedEmail;
        this.phone = null;
        this.password = unusablePasswordHash;
        this.active = false;
        this.deletedAt = closedAt;
    }

    public Set<EstablishmentService> getOfferedServices() {
        return Collections.unmodifiableSet(offeredServices);
    }

    public void updateOfferedServices(
            boolean offersAllServices,
            Collection<EstablishmentService> services) {
        this.offersAllServices = offersAllServices;
        this.offeredServices.clear();
        if (!offersAllServices) {
            this.offeredServices.addAll(services);
        }
    }

    public boolean offers(EstablishmentService service) {
        if (offersAllServices) {
            return true;
        }
        if (service == null) {
            return false;
        }
        return offeredServices.stream().anyMatch(offeredService ->
                offeredService == service
                        || service.getId() != null && service.getId().equals(offeredService.getId()));
    }

    public void deactivate() {
        this.active = false;
    }

    public void reactivate() {
        this.active = true;
    }
}
