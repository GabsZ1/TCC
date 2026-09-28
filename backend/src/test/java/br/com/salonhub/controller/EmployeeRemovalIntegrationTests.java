package br.com.salonhub.controller;

import br.com.salonhub.support.TestDocuments;

import br.com.salonhub.entity.User;
import br.com.salonhub.entity.Appointment;
import br.com.salonhub.entity.AppointmentStatus;
import br.com.salonhub.entity.EstablishmentService;
import br.com.salonhub.repository.AppointmentRepository;
import br.com.salonhub.repository.EstablishmentServiceRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Desativação, reativação e exclusão definitiva são operações distintas. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EmployeeRemovalIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired private EstablishmentServiceRepository serviceRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private Clock clock;

    @Test
    void shouldReactivateOnlyAnEmployeeFromTheAdministratorsEstablishment() throws Exception {
        String firstAdminEmail = "admin.reactivation.first@salonhub.test";
        String firstAdminToken = registerAndLogin(
                firstAdminEmail, "30303030000138", "Salão Reativação Um");
        String secondAdminToken = registerAndLogin(
                "admin.reactivation.second@salonhub.test", "40404040000113", "Salão Reativação Dois");

        User firstAdmin = userRepository.findByEmailIgnoreCase(firstAdminEmail).orElseThrow();
        User inactiveEmployee = userRepository.saveAndFlush(User.builder()
                .name("Funcionário Inativo")
                .email("inactive.reactivation@salonhub.test")
                .password("senha-codificada-para-o-teste")
                .role(UserRole.PROFESSIONAL)
                .establishment(firstAdmin.getEstablishment())
                .active(false)
                .build());

        mockMvc.perform(post("/api/funcionarios")
                        .header("Authorization", bearer(secondAdminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeePayload("Tentativa de transferência", inactiveEmployee.getEmail())))
                .andExpect(status().isConflict());

        mockMvc.perform(patch("/api/funcionarios/{id}/ativacao", inactiveEmployee.getId())
                        .header("Authorization", bearer(secondAdminToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/funcionarios/{id}/ativacao", inactiveEmployee.getId())
                        .header("Authorization", bearer(firstAdminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.establishmentName").value("Salão Reativação Um"));

        mockMvc.perform(patch("/api/funcionarios/{id}/ativacao", inactiveEmployee.getId())
                        .header("Authorization", bearer(firstAdminToken)))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldPermanentlyRemoveEmployeeWithoutAppointments() throws Exception {
        String adminToken = registerAndLogin(
                "admin.removal.clean@salonhub.test", "10101010000177", "Salão Remoção Limpa");

        MvcResult created = mockMvc.perform(post("/api/funcionarios")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeePayload("Funcionário Sem Vínculo", "sem.vinculo@salonhub.test")))
                .andExpect(status().isCreated())
                .andReturn();
        Number employeeId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(delete("/api/funcionarios/{id}", employeeId.longValue())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.removedPermanently").value(true));

        mockMvc.perform(get("/api/funcionarios")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldRejectDeletionWithAppointmentsAndAllowExplicitDeactivation() throws Exception {
        String adminToken = registerAndLogin(
                "admin.removal.linked@salonhub.test", "20202020000152", "Salão Remoção Vinculada");

        MvcResult employeeResult = mockMvc.perform(post("/api/funcionarios")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeePayload("Funcionário Com Vínculo", "com.vinculo@salonhub.test")))
                .andExpect(status().isCreated())
                .andReturn();
        Number employeeId = JsonPath.read(employeeResult.getResponse().getContentAsString(), "$.id");

        MvcResult serviceResult = mockMvc.perform(post("/api/servicos")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Corte Remoção",
                                  "price": 40.00,
                                  "durationMinutes": 30
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        Number serviceId = JsonPath.read(serviceResult.getResponse().getContentAsString(), "$.id");

        MvcResult establishmentResult = mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn();
        Number establishmentId = JsonPath.read(
                establishmentResult.getResponse().getContentAsString(), "$.establishmentId");

        mockMvc.perform(post("/api/auth/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Cliente Remoção",
                                  "email": "cliente.removal@salonhub.test",
                                  "phone": "44999990000",
                                  "password": "senha123",
                                  "passwordConfirmation": "senha123"
                                }
                                """))
                .andExpect(status().isCreated());
        String clientToken = login("cliente.removal@salonhub.test");

        LocalDateTime startsAt = LocalDate.now().plusDays(1).atTime(10, 0);
        MvcResult appointmentResult = mockMvc.perform(post("/api/estabelecimentos/{id}/agendamentos", establishmentId.longValue())
                        .header("Authorization", bearer(clientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceId": %d,
                                  "professionalId": %d,
                                  "startsAt": "%s"
                                }
                                """.formatted(serviceId.longValue(), employeeId.longValue(), startsAt)))
                .andExpect(status().isCreated())
                .andReturn();
        Number appointmentId = JsonPath.read(appointmentResult.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(delete("/api/funcionarios/{id}", employeeId.longValue())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict());
        assertThat(userRepository.findById(employeeId.longValue()).orElseThrow().isActive()).isTrue();

        // Não é possível desativar um funcionário com agendamento futuro confirmado:
        // isso deixaria o agendamento "órfão" (confirmado, mas o profissional não consegue
        // mais acessar o sistema para atendê-lo).
        mockMvc.perform(patch("/api/funcionarios/{id}/desativacao", employeeId.longValue())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict());
        assertThat(userRepository.findById(employeeId.longValue()).orElseThrow().isActive()).isTrue();

        mockMvc.perform(patch("/api/agendamentos/{id}/cancelamento", appointmentId.longValue())
                        .header("Authorization", bearer(clientToken)))
                .andExpect(status().isOk());

        // Sem agendamentos futuros confirmados pendentes, a desativação passa a ser permitida.
        mockMvc.perform(patch("/api/funcionarios/{id}/desativacao", employeeId.longValue())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/api/funcionarios")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].active").value(false));

        // Mesmo inativo, o histórico impede a exclusão.
        mockMvc.perform(delete("/api/funcionarios/{id}", employeeId.longValue())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldBlockDeactivationWithFutureConfirmedAppointment() throws Exception {
        String token = registerAndLogin("future.confirmed@employee.test", "30303030000138", "Future Confirmed");
        User admin = userRepository.findByEmailIgnoreCase("future.confirmed@employee.test").orElseThrow();
        User employee = employee(admin, "future-confirmed");
        appointment(employee, "future-confirmed", LocalDateTime.now(clock).plusDays(1), AppointmentStatus.CONFIRMED);

        mockMvc.perform(patch("/api/funcionarios/{id}/desativacao", employee.getId())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isConflict());
        assertThat(userRepository.findById(employee.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void shouldBlockDeactivationWithConfirmedAppointmentInProgress() throws Exception {
        String token = registerAndLogin("ongoing.confirmed@employee.test", "30303030000138", "Ongoing Confirmed");
        User admin = userRepository.findByEmailIgnoreCase("ongoing.confirmed@employee.test").orElseThrow();
        User employee = employee(admin, "ongoing-confirmed");
        appointment(employee, "ongoing-confirmed", LocalDateTime.now(clock).minusMinutes(30), AppointmentStatus.CONFIRMED);

        mockMvc.perform(patch("/api/funcionarios/{id}/desativacao", employee.getId())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isConflict());
        assertThat(userRepository.findById(employee.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void shouldAllowDeactivationWhenConfirmedAppointmentHasEnded() throws Exception {
        String token = registerAndLogin("ended.confirmed@employee.test", "30303030000138", "Ended Confirmed");
        User admin = userRepository.findByEmailIgnoreCase("ended.confirmed@employee.test").orElseThrow();
        User employee = employee(admin, "ended-confirmed");
        appointment(employee, "ended-confirmed", LocalDateTime.now(clock).minusHours(2), AppointmentStatus.CONFIRMED);

        mockMvc.perform(patch("/api/funcionarios/{id}/desativacao", employee.getId())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void shouldAllowDeactivationWithOnlyNonConfirmedAppointments() throws Exception {
        String token = registerAndLogin("terminal.statuses@employee.test", "30303030000138", "Terminal Statuses");
        User admin = userRepository.findByEmailIgnoreCase("terminal.statuses@employee.test").orElseThrow();
        User employee = employee(admin, "terminal-statuses");
        LocalDateTime future = LocalDateTime.now(clock).plusDays(1);
        appointment(employee, "cancelled", future, AppointmentStatus.CANCELLED);
        appointment(employee, "completed", future.plusHours(2), AppointmentStatus.COMPLETED);
        appointment(employee, "invalidated", future.plusHours(4), AppointmentStatus.INVALIDATED);

        mockMvc.perform(patch("/api/funcionarios/{id}/desativacao", employee.getId())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }
    @Test
    void shouldDeactivateWithoutAppointmentsKeepVisibleReactivateAndDeleteSafely() throws Exception {
        String token = registerAndLogin("lifecycle@employee.test", "51515151000113", "Lifecycle");
        User admin = userRepository.findByEmailIgnoreCase("lifecycle@employee.test").orElseThrow();
        User employee = employee(admin, "clean");
        mockMvc.perform(get("/api/funcionarios").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].active").value(true));
        mockMvc.perform(patch("/api/funcionarios/{id}/desativacao", employee.getId())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        entityManager.flush();
        entityManager.clear();
        assertThat(userRepository.findById(employee.getId()).orElseThrow().isActive()).isFalse();
        mockMvc.perform(get("/api/funcionarios").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].active").value(false));
        mockMvc.perform(patch("/api/funcionarios/{id}/desativacao", employee.getId())
                        .header("Authorization", bearer(token))).andExpect(status().isConflict());
        mockMvc.perform(patch("/api/funcionarios/{id}/ativacao", employee.getId())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(employee.getId()))
                .andExpect(jsonPath("$.active").value(true));
        mockMvc.perform(patch("/api/funcionarios/{id}/desativacao", employee.getId())
                        .header("Authorization", bearer(token))).andExpect(status().isOk());
        mockMvc.perform(delete("/api/funcionarios/{id}", employee.getId())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.removedPermanently").value(true));
        entityManager.clear();
        assertThat(userRepository.findById(employee.getId())).isEmpty();
    }

    @Test
    void shouldPreserveServiceLinksAndRejectDeletionEvenForInactiveServices() throws Exception {
        String token = registerAndLogin("links@employee.test", "61616161000107", "Links");
        User admin = userRepository.findByEmailIgnoreCase("links@employee.test").orElseThrow();
        User employee = employee(admin, "linked");
        EstablishmentService service = serviceRepository.saveAndFlush(EstablishmentService.builder()
                .establishment(admin.getEstablishment()).name("Corte").price(new BigDecimal("40.00"))
                .commissionPercentage(BigDecimal.TEN).durationMinutes(30).active(false).build());
        employee.updateOfferedServices(false, List.of(service));
        entityManager.flush();
        mockMvc.perform(delete("/api/funcionarios/{id}", employee.getId())
                        .header("Authorization", bearer(token))).andExpect(status().isConflict());
        assertThat(employee.isActive()).isTrue();
        mockMvc.perform(patch("/api/funcionarios/{id}/desativacao", employee.getId())
                        .header("Authorization", bearer(token))).andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();
        assertLinks(employee.getId(), service.getId(), false);
        mockMvc.perform(delete("/api/funcionarios/{id}", employee.getId())
                        .header("Authorization", bearer(token))).andExpect(status().isConflict());
        mockMvc.perform(patch("/api/funcionarios/{id}/ativacao", employee.getId())
                        .header("Authorization", bearer(token))).andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();
        assertLinks(employee.getId(), service.getId(), true);
    }

    @Test
    void shouldPreserveCancelledAppointmentHistoryAndSnapshots() throws Exception {
        String token = registerAndLogin("history@employee.test", "71717171000184", "History");
        User admin = userRepository.findByEmailIgnoreCase("history@employee.test").orElseThrow();
        User employee = employee(admin, "history");
        User client = userRepository.saveAndFlush(User.builder().name("Client").email("client@employee.test")
                .password("test").role(UserRole.CLIENT).build());
        EstablishmentService service = serviceRepository.saveAndFlush(EstablishmentService.builder()
                .establishment(admin.getEstablishment()).name("Corte").price(new BigDecimal("40.00"))
                .commissionPercentage(BigDecimal.TEN).durationMinutes(30).build());
        Appointment appointment = Appointment.schedule(admin.getEstablishment(), client, employee, service,
                LocalDateTime.now().minusDays(2).withNano(0));
        appointment.cancel();
        appointmentRepository.saveAndFlush(appointment);
        Long id = appointment.getId();
        for (String action : List.of("desativacao", "ativacao")) {
            mockMvc.perform(patch("/api/funcionarios/{id}/" + action, employee.getId())
                            .header("Authorization", bearer(token))).andExpect(status().isOk());
            mockMvc.perform(delete("/api/funcionarios/{id}", employee.getId())
                            .header("Authorization", bearer(token))).andExpect(status().isConflict());
            entityManager.flush();
            entityManager.clear();
            Appointment saved = appointmentRepository.findById(id).orElseThrow();
            assertThat(saved.getProfessional().getId()).isEqualTo(employee.getId());
            assertThat(saved.getStatus()).isEqualTo(appointment.getStatus());
            assertThat(saved.getServiceName()).isEqualTo("Corte");
            assertThat(saved.getAgreedPrice()).isEqualByComparingTo("40.00");
            assertThat(saved.getCommissionPercentage()).isEqualByComparingTo("10.00");
            assertThat(saved.getCommissionAmount()).isEqualByComparingTo("4.00");
            assertThat(saved.getDurationMinutes()).isEqualTo(30);
            assertThat(saved.getStartsAt()).isEqualTo(appointment.getStartsAt());
            assertThat(saved.getEndsAt()).isEqualTo(appointment.getEndsAt());
        }
    }

    @Test
    void shouldRejectCrossTenantDeactivationAndNonAdminLifecycleChanges() throws Exception {
        String token = registerAndLogin("owner@employee.test", "81818181000160", "Owner");
        User admin = userRepository.findByEmailIgnoreCase("owner@employee.test").orElseThrow();
        User employee = employee(admin, "secured");
        String otherToken = registerAndLogin("other@employee.test", "82828282000184", "Other");
        mockMvc.perform(patch("/api/funcionarios/{id}/desativacao", employee.getId())
                        .header("Authorization", bearer(otherToken))).andExpect(status().isNotFound());
        User client = userRepository.saveAndFlush(User.builder().name("Client").email("security@employee.test")
                .password("test").role(UserRole.CLIENT).build());
        for (User actor : List.of(employee, client)) {
            for (String action : List.of("desativacao", "ativacao")) {
                mockMvc.perform(patch("/api/funcionarios/{id}/" + action, employee.getId())
                                .with(jwt().jwt(jwt -> jwt.subject(actor.getId().toString()))))
                        .andExpect(status().isForbidden());
            }
            mockMvc.perform(delete("/api/funcionarios/{id}", employee.getId())
                            .with(jwt().jwt(jwt -> jwt.subject(actor.getId().toString()))))
                    .andExpect(status().isForbidden());
        }
        assertThat(employee.isActive()).isTrue();
    }

    private Appointment appointment(
            User employee, String suffix, LocalDateTime startsAt, AppointmentStatus status) {
        User client = userRepository.saveAndFlush(User.builder()
                .name("Client " + suffix)
                .email("client." + suffix + "@employee.test")
                .password("test")
                .role(UserRole.CLIENT)
                .build());
        EstablishmentService service = serviceRepository.saveAndFlush(EstablishmentService.builder()
                .establishment(employee.getEstablishment())
                .name("Service " + suffix)
                .price(new BigDecimal("40.00"))
                .commissionPercentage(BigDecimal.TEN)
                .durationMinutes(60)
                .build());
        Appointment appointment = Appointment.schedule(
                employee.getEstablishment(), client, employee, service, startsAt.withNano(0));
        switch (status) {
            case CANCELLED -> appointment.cancel();
            case COMPLETED -> appointment.complete(startsAt.plusMinutes(30));
            case INVALIDATED -> appointment.invalidate("Teste de desativação");
            case CONFIRMED -> { }
        }
        return appointmentRepository.saveAndFlush(appointment);
    }
    private User employee(User admin, String name) {
        return userRepository.saveAndFlush(User.builder().name(name).email(name + "@fixture.test")
                .password("test").role(UserRole.PROFESSIONAL).establishment(admin.getEstablishment()).build());
    }

    private void assertLinks(Long employeeId, Long serviceId, boolean active) {
        User employee = userRepository.findById(employeeId).orElseThrow();
        assertThat(employee.isActive()).isEqualTo(active);
        assertThat(employee.isOffersAllServices()).isFalse();
        assertThat(employee.getOfferedServices()).extracting(EstablishmentService::getId).containsExactly(serviceId);
    }

    private String registerAndLogin(String email, String cnpj, String establishmentName) throws Exception {
        mockMvc.perform(post("/api/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ownerName": "Administrador",
                                  "ownerEmail": "%s",
                                  "password": "senha123",
                                  "establishmentName": "%s",
                                  "cnpj": "%s"
                                }
                                """.formatted(email, establishmentName, cnpj)))
                .andExpect(status().isCreated());

        return login(email);
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "senha123"
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    private String employeePayload(String name, String email) {
        return """
                {
                  "name": "%s",
                  "email": "%s",
                  "occupation": "Cabeleireiro(a)",
                  "cpf": "%s",
                  "hireDate": "2025-01-02",
                  "password": "senha123"
                }
                """.formatted(name, email, TestDocuments.cpf());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
