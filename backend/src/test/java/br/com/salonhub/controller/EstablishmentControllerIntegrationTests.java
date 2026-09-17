package br.com.salonhub.controller;

import br.com.salonhub.entity.*;
import br.com.salonhub.repository.*;
import br.com.salonhub.service.TokenService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EstablishmentControllerIntegrationTests {

    private static final String ENDPOINT = "/api/estabelecimentos/me";
    private static final String VALID_UPDATE = """
            {"name":"  Salão Atualizado  ","email":"contato@salonhub.test","phone":" 44999999999 "}
            """;

    @Autowired private MockMvc mockMvc;
    @Autowired private EstablishmentRepository establishmentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EstablishmentServiceRepository serviceRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private TokenService tokenService;
    @Autowired private EntityManager entityManager;

    @Test
    void shouldReadAndUpdateOnlyOwnEstablishmentWithoutChangingBusinessHours() throws Exception {
        User first = administrator("first", "12345678000195");
        User second = administrator("second", "22345678000149");
        Long firstId = first.getEstablishment().getId();
        Long secondId = second.getEstablishment().getId();

        mockMvc.perform(get(ENDPOINT).header("Authorization", bearer(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(firstId))
                .andExpect(jsonPath("$.cnpj").value("12345678000195"))
                .andExpect(jsonPath("$.active").value(true));
        mockMvc.perform(get(ENDPOINT).header("Authorization", bearer(second)))
                .andExpect(jsonPath("$.id").value(secondId));

        mockMvc.perform(put(ENDPOINT).header("Authorization", bearer(first))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(firstId))
                .andExpect(jsonPath("$.name").value("Salão Atualizado"))
                .andExpect(jsonPath("$.cnpj").value("12345678000195"))
                .andExpect(jsonPath("$.phone").value("44999999999"));

        entityManager.flush();
        entityManager.clear();
        Establishment updated = establishmentRepository.findById(firstId).orElseThrow();
        assertThat(establishmentRepository.findById(secondId).orElseThrow().getName())
                .isEqualTo("Salão second");

        mockMvc.perform(put("/api/estabelecimentos/{id}", secondId)
                        .header("Authorization", bearer(first))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/estabelecimentos/{id}", secondId)
                        .header("Authorization", bearer(first)))
                .andExpect(status().isNotFound());

        mockMvc.perform(put(ENDPOINT).header("Authorization", bearer(first))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Novo nome","email":"","phone":" "}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cnpj").value("12345678000195"))
                .andExpect(jsonPath("$.email").isEmpty())
                .andExpect(jsonPath("$.phone").isEmpty());
    }

    @Test
    void shouldKeepCnpjWhenUpdatingRegistration() throws Exception {
        User admin = administrator("registration", "12345678000195");

        mockMvc.perform(put(ENDPOINT).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Nome atualizado","email":"","phone":" "}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cnpj").value("12345678000195"));
    }

    @Test
    void shouldReturnPortugueseValidationErrors() throws Exception {
        User admin = administrator("validation", "12345678000195");

        mockMvc.perform(put(ENDPOINT).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":" ","email":"invalido","phone":"123456789012345678901"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Dados inválidos"))
                .andExpect(jsonPath("$.fields.name").value("O nome do estabelecimento é obrigatório"))
                .andExpect(jsonPath("$.fields.email").value("E-mail em formato inválido"))
                .andExpect(jsonPath("$.fields.phone").value("O telefone deve ter no máximo 20 caracteres"));
    }

    @Test
    void shouldRequireAnActiveAdministratorWithAnEstablishment() throws Exception {
        User admin = administrator("authorization", "12345678000195");
        User client = user("client-authorization", UserRole.CLIENT, null);
        User professional = user("professional-authorization", UserRole.PROFESSIONAL, admin.getEstablishment());
        User unlinkedAdmin = user("unlinked", UserRole.ADMIN, null);

        for (MockHttpServletRequestBuilder request : establishmentRequests()) {
            mockMvc.perform(request).andExpect(status().isUnauthorized());
        }
        for (User denied : List.of(client, professional, unlinkedAdmin)) {
            for (MockHttpServletRequestBuilder request : establishmentRequests()) {
                mockMvc.perform(request.header("Authorization", bearer(denied)))
                        .andExpect(status().isForbidden());
            }
        }
        String token = bearer(admin);
        admin.deactivate();
        userRepository.saveAndFlush(admin);
        for (MockHttpServletRequestBuilder request : establishmentRequests()) {
            mockMvc.perform(request.header("Authorization", token))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void shouldDeactivateBlockNormalOperationsAndReactivateWithoutLosingData() throws Exception {
        User admin = administrator("deletion", "12345678000195");
        User otherAdmin = administrator("unaffected", "22345678000149");
        Establishment establishment = admin.getEstablishment();
        Long establishmentId = establishment.getId();
        User professional = user("professional-deletion", UserRole.PROFESSIONAL, establishment);
        User client = user("client-deletion", UserRole.CLIENT, null);
        EstablishmentService service = serviceRepository.saveAndFlush(EstablishmentService.builder()
                .establishment(establishment).name("Corte").price(new BigDecimal("50.00"))
                .commissionPercentage(BigDecimal.ZERO).durationMinutes(30).active(true).build());
        professional.updateOfferedServices(false, List.of(service));
        entityManager.flush();
        LocalDate date = LocalDate.now().plusDays(2);
        Appointment appointment = appointmentRepository.saveAndFlush(Appointment.schedule(
                establishment, client, professional, service, date.atTime(10, 0)));
        String adminToken = bearer(admin);
        String clientToken = bearer(client);
        String professionalToken = bearer(professional);

        mockMvc.perform(delete(ENDPOINT).header("Authorization", adminToken))
                .andExpect(status().isNoContent());
        entityManager.flush();
        entityManager.clear();

        assertThat(establishmentRepository.findById(establishmentId).orElseThrow().isActive()).isFalse();
        assertThat(userRepository.findById(professional.getId()).orElseThrow().isActive()).isTrue();
        assertThat(userRepository.findById(admin.getId()).orElseThrow().isActive()).isTrue();
        assertThat(serviceRepository.findById(service.getId()).orElseThrow().isActive()).isTrue();
        Appointment preserved = appointmentRepository.findById(appointment.getId()).orElseThrow();
        assertThat(preserved.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(preserved.getEstablishment().getId()).isEqualTo(establishmentId);
        assertThat(preserved.getClient().getId()).isEqualTo(client.getId());
        assertThat(preserved.getProfessional().getId()).isEqualTo(professional.getId());
        assertThat(preserved.getService().getId()).isEqualTo(service.getId());
        assertThat(establishmentRepository.findById(otherAdmin.getEstablishment().getId()).orElseThrow().isActive())
                .isTrue();

        mockMvc.perform(get(ENDPOINT).header("Authorization", adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(delete(ENDPOINT).header("Authorization", adminToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(put(ENDPOINT).header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Não é possível editar um estabelecimento inativo."));

        String servicePayload = """
                {"name":"Corte","price":50,"durationMinutes":30}
                """;
        String employeePayload = """
                {"name":"Profissional","email":"novo@salonhub.test","password":"senha123"}
                """;
        for (MockHttpServletRequestBuilder request : List.of(
                get("/api/funcionarios"), get("/api/servicos"),
                get("/api/agendamentos/estabelecimento/me"),
                patch("/api/funcionarios/{id}/ativacao", professional.getId()),
                patch("/api/funcionarios/{id}/desativacao", professional.getId()),
                patch("/api/servicos/{id}/ativacao", service.getId()),
                put("/api/funcionarios/{id}/servicos", professional.getId())
                        .content("{\"allServices\":true,\"serviceIds\":[]}"),
                post("/api/funcionarios").content(employeePayload),
                put("/api/funcionarios/{id}", professional.getId()).content(employeePayload),
                delete("/api/funcionarios/{id}", professional.getId()),
                post("/api/servicos").content(servicePayload),
                put("/api/servicos/{id}", service.getId()).content(servicePayload),
                delete("/api/servicos/{id}", service.getId()))) {
            mockMvc.perform(request.header("Authorization", adminToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message")
                            .value("O estabelecimento está inativo. As operações administrativas estão bloqueadas."));
        }

        for (String catalog : List.of("servicos", "funcionarios")) {
            mockMvc.perform(get("/api/estabelecimentos/{id}/catalogo/" + catalog, establishmentId)
                            .header("Authorization", clientToken))
                    .andExpect(status().isNotFound());
        }
        mockMvc.perform(get("/api/estabelecimentos/{id}/agendamentos/disponibilidade", establishmentId)
                        .header("Authorization", clientToken)
                        .param("serviceId", service.getId().toString())
                        .param("professionalId", professional.getId().toString())
                        .param("date", date.toString()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/estabelecimentos/{id}/agendamentos", establishmentId)
                        .header("Authorization", clientToken).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"serviceId":%d,"professionalId":%d,"startsAt":"%sT11:00:00"}
                                """.formatted(service.getId(), professional.getId(), date)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/agendamentos/me").header("Authorization", clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(appointment.getId()))
                .andExpect(jsonPath("$[0].status").value("CONFIRMED"));
        mockMvc.perform(get("/api/agendamentos/profissional/me").header("Authorization", professionalToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(appointment.getId()));
        mockMvc.perform(get("/api/auth/me").header("Authorization", adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/servicos").header("Authorization", bearer(otherAdmin)))
                .andExpect(status().isOk());

        mockMvc.perform(patch(ENDPOINT + "/ativacao").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(establishmentId))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.name").value(establishment.getName()))
                .andExpect(jsonPath("$.cnpj").value(establishment.getCnpj()));
        entityManager.flush();
        entityManager.clear();
        Establishment reactivated = establishmentRepository.findById(establishmentId).orElseThrow();
        assertThat(reactivated.isActive()).isTrue();
        assertThat(reactivated.getMinimumAdvanceMinutes()).isEqualTo(establishment.getMinimumAdvanceMinutes());
        assertThat(reactivated.getMaximumAdvanceDays()).isEqualTo(establishment.getMaximumAdvanceDays());
        User preservedProfessional = userRepository.findById(professional.getId()).orElseThrow();
        assertThat(preservedProfessional.isActive()).isTrue();
        assertThat(preservedProfessional.isOffersAllServices()).isFalse();
        assertThat(preservedProfessional.getOfferedServices()).extracting(EstablishmentService::getId)
                .containsExactly(service.getId());
        assertThat(serviceRepository.findById(service.getId()).orElseThrow().isActive()).isTrue();
        Appointment afterReactivation = appointmentRepository.findById(appointment.getId()).orElseThrow();
        assertThat(afterReactivation.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(afterReactivation.getProfessional().getId()).isEqualTo(professional.getId());
        assertThat(afterReactivation.getService().getId()).isEqualTo(service.getId());
        assertThat(afterReactivation.getClient().getId()).isEqualTo(client.getId());
        assertThat(afterReactivation.getServiceName()).isEqualTo(appointment.getServiceName());
        assertThat(afterReactivation.getAgreedPrice()).isEqualByComparingTo(appointment.getAgreedPrice());
        assertThat(afterReactivation.getCommissionPercentage()).isEqualByComparingTo(appointment.getCommissionPercentage());
        assertThat(afterReactivation.getCommissionAmount()).isEqualByComparingTo(appointment.getCommissionAmount());
        assertThat(afterReactivation.getStartsAt()).isEqualTo(appointment.getStartsAt());
        assertThat(afterReactivation.getEndsAt()).isEqualTo(appointment.getEndsAt());
        mockMvc.perform(get(ENDPOINT).header("Authorization", adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
        mockMvc.perform(put(ENDPOINT).header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
                .andExpect(status().isOk());
        for (String path : List.of("/api/servicos", "/api/funcionarios", "/api/agendamentos/estabelecimento/me")) {
            mockMvc.perform(get(path).header("Authorization", adminToken)).andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/estabelecimentos/{id}/catalogo/servicos", establishmentId)
                        .header("Authorization", clientToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(service.getId()));
        mockMvc.perform(patch(ENDPOINT + "/ativacao").header("Authorization", adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReactivateOnlyTheAuthenticatedAdministratorsEstablishment() throws Exception {
        User first = administrator("first-reactivation", "12345678000195");
        User second = administrator("second-reactivation", "22345678000149");
        first.getEstablishment().deactivate();
        second.getEstablishment().deactivate();
        entityManager.flush();

        mockMvc.perform(patch("/api/estabelecimentos/{id}/ativacao", first.getEstablishment().getId())
                        .header("Authorization", bearer(second)))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch(ENDPOINT + "/ativacao").header("Authorization", bearer(second))
                        .param("establishmentId", first.getEstablishment().getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(second.getEstablishment().getId()));
        entityManager.flush();
        entityManager.clear();
        assertThat(establishmentRepository.findById(first.getEstablishment().getId()).orElseThrow().isActive()).isFalse();
        assertThat(establishmentRepository.findById(second.getEstablishment().getId()).orElseThrow().isActive()).isTrue();
    }

    private List<MockHttpServletRequestBuilder> establishmentRequests() {
        return List.of(get(ENDPOINT), delete(ENDPOINT), patch(ENDPOINT + "/ativacao"),
                put(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE));
    }

    private User administrator(String key, String cnpj) {
        Establishment establishment = establishmentRepository.saveAndFlush(
                Establishment.builder().name("Salão " + key).cnpj(cnpj).build());
        return user(key, UserRole.ADMIN, establishment);
    }

    private User user(String key, UserRole role, Establishment establishment) {
        return userRepository.saveAndFlush(User.builder().name(key).email(key + "@salonhub.test")
                .password("senha-de-teste").role(role).establishment(establishment).active(true).build());
    }

    private String bearer(User user) {
        return "Bearer " + tokenService.generate(user).value();
    }
}
