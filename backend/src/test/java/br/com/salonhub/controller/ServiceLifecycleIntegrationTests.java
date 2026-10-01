package br.com.salonhub.controller;

import br.com.salonhub.entity.*;
import br.com.salonhub.repository.*;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ServiceLifecycleIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired EstablishmentRepository establishments;
    @Autowired EstablishmentServiceRepository services;
    @Autowired UserRepository users;
    @Autowired AppointmentRepository appointments;
    @Autowired EntityManager entityManager;

    private User admin;
    private User client;

    @BeforeEach
    void setUp() {
        Establishment establishment = establishments.saveAndFlush(Establishment.builder()
                .name("Lifecycle").cnpj("91919191000191").build());
        admin = user("admin", UserRole.ADMIN, establishment);
        client = user("client", UserRole.CLIENT, null);
    }

    @Test
    void shouldKeepInactiveServicesVisibleOnlyToAdminAndReactivateInClientCatalog() throws Exception {
        long id = create(admin, "Corte");
        mvc.perform(get("/api/servicos").with(as(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(id))
                .andExpect(jsonPath("$.content[0].active").value(true));
        catalogSize(1);
        mvc.perform(delete("/api/servicos/{id}", id).with(as(admin)))
                .andExpect(status().isNoContent());
        entityManager.flush();
        entityManager.clear();
        assertThat(services.findById(id).orElseThrow().isActive()).isFalse();
        mvc.perform(get("/api/servicos").with(as(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].active").value(false));
        catalogSize(0);
        mvc.perform(patch("/api/servicos/{id}/ativacao", id).with(as(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
        entityManager.flush();
        entityManager.clear();
        assertThat(services.findById(id).orElseThrow().isActive()).isTrue();
        catalogSize(1);
        mvc.perform(patch("/api/servicos/{id}/ativacao", id).with(as(admin)))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldRejectDuplicateNamesIncludingInactiveServices() throws Exception {
        long id = create(admin, "  Corte  ");
        assertThat(services.findById(id).orElseThrow().getName()).isEqualTo("Corte");
        duplicate("CORTE");
        mvc.perform(delete("/api/servicos/{id}", id).with(as(admin))).andExpect(status().isNoContent());
        duplicate("  corte  ");
    }

    @Test
    void shouldValidateRenameWithoutConflictingWithItself() throws Exception {
        long first = create(admin, "Corte");
        long second = create(admin, "Barba");
        mvc.perform(put("/api/servicos/{id}", second).with(as(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(payload(" CORTE ")))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/servicos/{id}", first).with(as(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(payload(" corte ")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("corte"));
    }

    @Test
    void shouldAllowSameNameInAnotherEstablishmentAndRejectCrossTenantActivation() throws Exception {
        long id = create(admin, "Corte");
        Establishment other = establishments.saveAndFlush(Establishment.builder()
                .name("Other").cnpj("92929292000192").build());
        User otherAdmin = user("other", UserRole.ADMIN, other);
        create(otherAdmin, " corte ");
        mvc.perform(delete("/api/servicos/{id}", id).with(as(admin))).andExpect(status().isNoContent());
        mvc.perform(patch("/api/servicos/{id}/ativacao", id).with(as(otherAdmin)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/servicos").with(as(otherAdmin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1));
        assertThat(services.findById(id).orElseThrow().isActive()).isFalse();
    }

    @Test
    void shouldPageServicesOrderedByNameAndIsolatedByEstablishment() throws Exception {
        create(admin, "Zeta");
        create(admin, "Alfa");
        create(admin, "Beta");
        Establishment other = establishments.saveAndFlush(Establishment.builder()
                .name("Other page").cnpj("96969696000196").build());
        User otherAdmin = user("other-page", UserRole.ADMIN, other);
        create(otherAdmin, "Outro serviço");

        mvc.perform(get("/api/servicos").with(as(admin)).param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].name").value("Alfa"))
                .andExpect(jsonPath("$.content[1].name").value("Beta"));
        mvc.perform(get("/api/servicos").with(as(admin)).param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Zeta"));
        mvc.perform(get("/api/servicos").with(as(otherAdmin)).param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Outro serviço"));
    }

    @Test
    void shouldSearchAndFilterServicesWithCatalogWideCounts() throws Exception {
        create(admin, "Corte feminino");
        long inactiveId = create(admin, "Corte masculino");
        create(admin, "Barba");
        mvc.perform(delete("/api/servicos/{id}", inactiveId).with(as(admin)))
                .andExpect(status().isNoContent());

        Establishment other = establishments.saveAndFlush(Establishment.builder()
                .name("Other filters").cnpj("97979797000197").build());
        User otherAdmin = user("other-filters", UserRole.ADMIN, other);
        create(otherAdmin, "Corte de outro salão");

        mvc.perform(get("/api/servicos/resumo").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.active").value(2))
                .andExpect(jsonPath("$.inactive").value(1));
        mvc.perform(get("/api/servicos").with(as(admin))
                        .param("search", "  CORTE  ").param("active", "true").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Corte feminino"));
        mvc.perform(get("/api/servicos").with(as(admin))
                        .param("search", "corte").param("active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Corte masculino"));
        mvc.perform(get("/api/servicos/resumo").with(as(otherAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/servicos/resumo").with(as(client)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldForbidClientAndProfessionalFromManagingServiceLifecycle() throws Exception {
        long id = create(admin, "Corte");
        User professional = user("professional", UserRole.PROFESSIONAL, admin.getEstablishment());
        for (User user : List.of(client, professional)) {
            mvc.perform(get("/api/servicos").with(as(user))).andExpect(status().isForbidden());
            mvc.perform(delete("/api/servicos/{id}", id).with(as(user))).andExpect(status().isForbidden());
            mvc.perform(patch("/api/servicos/{id}/ativacao", id).with(as(user))).andExpect(status().isForbidden());
        }
    }

    @Test
    void shouldPreserveAppointmentsSnapshotsAndProfessionalLinks() throws Exception {
        long id = create(admin, "Corte");
        EstablishmentService service = services.findById(id).orElseThrow();
        User professional = user("linked", UserRole.PROFESSIONAL, admin.getEstablishment());
        professional.updateOfferedServices(false, List.of(service));
        User unlinked = user("unlinked", UserRole.PROFESSIONAL, admin.getEstablishment());
        unlinked.updateOfferedServices(false, List.of());
        Appointment appointment = appointments.saveAndFlush(schedule(admin.getEstablishment(),
                client, professional, service, LocalDateTime.now().plusDays(1).withNano(0)));
        Long appointmentId = appointment.getId();
        Long professionalId = professional.getId();
        LocalDateTime endsAt = appointment.getEndsAt();
        mvc.perform(delete("/api/servicos/{id}", id).with(as(admin))).andExpect(status().isNoContent());
        entityManager.flush();
        entityManager.clear();
        assertPreserved(appointmentId, professionalId, id, endsAt);
        mvc.perform(patch("/api/servicos/{id}/ativacao", id).with(as(admin))).andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();
        assertPreserved(appointmentId, professionalId, id, endsAt);
        mvc.perform(get("/api/estabelecimentos/{id}/catalogo/funcionarios", admin.getEstablishment().getId())
                        .param("serviceId", Long.toString(id)).with(as(client)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(professionalId));
    }

    @Test
    void shouldEnforceNormalizedUniquenessInDatabase() throws Exception {
        create(admin, "Corte");
        EstablishmentService duplicate = EstablishmentService.builder().establishment(admin.getEstablishment())
                .name(" CORTE ").price(new BigDecimal("50.00")).commissionPercentage(BigDecimal.TEN)
                .durationMinutes(30).active(false).build();
        assertThatThrownBy(() -> services.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void assertPreserved(Long appointmentId, Long professionalId, long serviceId, LocalDateTime endsAt) {
        Appointment saved = appointments.findById(appointmentId).orElseThrow();
        assertThat(saved.getService().getId()).isEqualTo(serviceId);
        assertThat(saved.getServiceName()).isEqualTo("Corte");
        assertThat(saved.getAgreedPrice()).isEqualByComparingTo("50.00");
        assertThat(saved.getCommissionPercentage()).isEqualByComparingTo("10.00");
        assertThat(saved.getCommissionAmount()).isEqualByComparingTo("5.00");
        assertThat(saved.getDurationMinutes()).isEqualTo(30);
        assertThat(saved.getEndsAt()).isEqualTo(endsAt);
        assertThat(saved.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(users.findById(professionalId).orElseThrow().getOfferedServices())
                .extracting(EstablishmentService::getId).containsExactly(serviceId);
    }

    private User user(String name, UserRole role, Establishment establishment) {
        return users.saveAndFlush(User.builder().name(name).email(name + "@lifecycle.test")
                .password("test-password").role(role).establishment(establishment).build());
    }

    private RequestPostProcessor as(User user) {
        return jwt().jwt(token -> token.subject(user.getId().toString()));
    }

    private long create(User user, String name) throws Exception {
        String response = mvc.perform(post("/api/servicos").with(as(user))
                        .contentType(MediaType.APPLICATION_JSON).content(payload(name)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private void duplicate(String name) throws Exception {
        mvc.perform(post("/api/servicos").with(as(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(payload(name)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
    }

    private void catalogSize(int size) throws Exception {
        mvc.perform(get("/api/estabelecimentos/{id}/catalogo/servicos", admin.getEstablishment().getId())
                        .with(as(client)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(size));
    }

    private String payload(String name) {
        return """
                {"name":"%s","price":50.00,"commissionPercentage":10.00,"durationMinutes":30}
                """.formatted(name);
    }

    @org.springframework.beans.factory.annotation.Autowired
    private CustomerRepository customerRepository;

    private Appointment schedule(br.com.salonhub.entity.Establishment establishment,
            br.com.salonhub.entity.User client, br.com.salonhub.entity.User professional,
            br.com.salonhub.entity.EstablishmentService service, java.time.LocalDateTime startsAt) {
        Customer customer = customerRepository.findByEstablishmentIdAndUserId(establishment.getId(), client.getId())
                .orElseGet(() -> {
                    Customer created = Customer.create(establishment, client.getName(), client.getPhone(), client.getEmail());
                    created.link(client);
                    return customerRepository.saveAndFlush(created);
                });
        return Appointment.schedule(establishment, customer, professional, service, startsAt);
    }}
