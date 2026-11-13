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

/** Representa contas, papéis e vínculos com estabelecimentos e serviços. */
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

    @Column(name = "first_access_version", nullable = false)
    @Builder.Default
    private int firstAccessVersion = 0;

    /** Invalida o convite anterior antes de emitir outro para a mesma conta. */
    public void renewFirstAccess() { this.firstAccessVersion++; }

    /** Vai no JWT; incrementar encerra todas as sessões abertas (ver SessionVersionValidator). */
    @Column(name = "session_version", nullable = false)
    @Builder.Default
    private int sessionVersion = 0;

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

    /** Altera os dados gerais permitidos ao perfil do usuário. */
    public void updateProfile(String name, String email) {
        this.name = name;
        this.email = email;
    }

    /** Conclui o primeiro acesso gravando a senha inicial. */
    public void initializePassword(String encodedPassword) {
        this.password = encodedPassword;
        this.passwordInitialized = true;
    }

    /** Troca a senha e encerra todas as sessões abertas com a senha antiga. */
    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
        endAllSessions();
    }

    /** Incrementa a versão da sessão para rejeitar tokens emitidos antes da alteração. */
    public void endAllSessions() {
        this.sessionVersion++;
    }

    /** Troca a referência à foto vinculada ao usuário. */
    public void replacePhoto(Media photo) {
        this.photo = photo;
    }

    /** Nome do funcionário: continua sendo alterado pelo administrador. */
    public void renameEmployee(String name) {
        this.name = name;
    }

    /** Ocupação do funcionário: definida e alterada pelo administrador. */
    public void updateEmployeeOccupation(String occupation) {
        this.occupation = occupation;
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
            String email, String phone, String instagram, String address) {
        this.email = email;
        this.phone = phone;
        this.instagram = instagram;
        this.address = address;
    }

    /** Atualiza os dados pessoais da conta cliente. */
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
        endAllSessions();
    }

    /** Devolve os serviços atualmente atribuídos ao profissional. */
    public Set<EstablishmentService> getOfferedServices() {
        return Collections.unmodifiableSet(offeredServices);
    }

    /** Substitui o conjunto de serviços oferecidos pelo profissional. */
    public void updateOfferedServices(
            boolean offersAllServices,
            Collection<EstablishmentService> services) {
        this.offersAllServices = offersAllServices;
        this.offeredServices.clear();
        if (!offersAllServices) {
            this.offeredServices.addAll(services);
        }
    }

    /** Confere se o serviço pertence ao conjunto atribuído ao profissional. */
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

    /** Bloqueia o acesso da conta sem remover seus vínculos históricos. */
    public void deactivate() {
        this.active = false;
    }

    /** Restaura o acesso da conta mantendo seus vínculos existentes. */
    public void reactivate() {
        this.active = true;
    }
}
