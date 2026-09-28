package br.com.salonhub.controller;

import br.com.salonhub.entity.Appointment;
import br.com.salonhub.entity.Establishment;
import br.com.salonhub.entity.EstablishmentService;
import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.repository.AppointmentRepository;
import br.com.salonhub.repository.EstablishmentRepository;
import br.com.salonhub.repository.EstablishmentServiceRepository;
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
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cobre os indicadores do dashboard do administrador (US 2.14):
 * serviços concluídos hoje, comissões do mês corrente e funcionários ativos.
 * O serviço usado nos fixtures tem comissão de 20% sobre R$ 100,00, ou seja,
 * cada atendimento concluído gera R$ 20,00 de comissão.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DashboardIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Clock clock;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private EstablishmentRepository establishmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EstablishmentServiceRepository serviceRepository;

    @Test
    void shouldReturnZeroedSummaryWhenThereIsNoCompletedActivity() throws Exception {
        Fixture fixture = setUp("dashboard-empty", "43000000010086");

        mockMvc.perform(get("/api/estabelecimentos/me/dashboard")
                        .header("Authorization", bearer(fixture.administratorToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todaysCompletedServices").value(0))
                .andExpect(jsonPath("$.monthlyCommissionTotal").value(0.0))
                .andExpect(jsonPath("$.activeEmployees").value(1));
    }

    @Test
    void shouldCountOnlyTodaysCompletedServicesAndSumCurrentMonthCommission() throws Exception {
        Fixture fixture = setUp("dashboard-activity", "43000100010193");

        createCompletedAppointment(
                fixture, LocalDateTime.now(clock).minusMonths(2), LocalDateTime.now(clock).minusHours(2));
        createCompletedAppointment(
                fixture, LocalDateTime.now(clock).minusHours(2), LocalDateTime.now(clock).minusMonths(2));
        createAppointment(fixture, LocalDateTime.now(clock).plusHours(1));
        Long toCancel = createAppointment(fixture, LocalDateTime.now(clock).plusHours(2));
        cancelAsClient(fixture, toCancel);

        mockMvc.perform(get("/api/estabelecimentos/me/dashboard")
                        .header("Authorization", bearer(fixture.administratorToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todaysCompletedServices").value(1))
                .andExpect(jsonPath("$.monthlyCommissionTotal").value(20.0))
                .andExpect(jsonPath("$.activeEmployees").value(1));
    }

    @Test
    void shouldExcludeCommissionFromAppointmentsCompletedInAPreviousMonth() throws Exception {
        Fixture fixture = setUp("dashboard-previous-month", "43000200010209");
        createCompletedAppointment(
                fixture, LocalDateTime.now(clock).minusHours(1), LocalDateTime.now(clock).minusMonths(2));

        mockMvc.perform(get("/api/estabelecimentos/me/dashboard")
                        .header("Authorization", bearer(fixture.administratorToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todaysCompletedServices").value(0))
                .andExpect(jsonPath("$.monthlyCommissionTotal").value(0.0));
    }

    @Test
    void shouldCountOnlyActiveProfessionalsAsEmployees() throws Exception {
        Fixture fixture = setUp("dashboard-employees", "43000300010308");

        MvcResult secondProfessional = mockMvc.perform(post("/api/funcionarios")
                        .header("Authorization", bearer(fixture.administratorToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Segundo Profissional","email":"second.professional.dashboard-employees@salonhub.test"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        Number secondProfessionalId = JsonPath.read(
                secondProfessional.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(patch("/api/funcionarios/{id}/desativacao", secondProfessionalId)
                        .header("Authorization", bearer(fixture.administratorToken())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/estabelecimentos/me/dashboard")
                        .header("Authorization", bearer(fixture.administratorToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeEmployees").value(1));
    }

    @Test
    void shouldForbidProfessionalFromViewingDashboard() throws Exception {
        Fixture fixture = setUp("dashboard-forbidden-professional", "43000400010415");

        mockMvc.perform(get("/api/estabelecimentos/me/dashboard")
                        .header("Authorization", bearer(fixture.professionalToken())))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldForbidClientFromViewingDashboard() throws Exception {
        Fixture fixture = setUp("dashboard-forbidden-client", "43000500010522");

        mockMvc.perform(get("/api/estabelecimentos/me/dashboard")
                        .header("Authorization", bearer(fixture.clientToken())))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldIsolateDashboardDataBetweenEstablishments() throws Exception {
        Fixture fixtureA = setUp("dashboard-tenant-a", "43000600010630");
        Fixture fixtureB = setUp("dashboard-tenant-b", "43000700010747");
        completeAppointment(fixtureA, LocalDateTime.now(clock).minusHours(1));

        mockMvc.perform(get("/api/estabelecimentos/me/dashboard")
                        .header("Authorization", bearer(fixtureB.administratorToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todaysCompletedServices").value(0))
                .andExpect(jsonPath("$.monthlyCommissionTotal").value(0.0));
    }

    private void completeAppointment(Fixture fixture, LocalDateTime startsAt) throws Exception {
        Long appointmentId = createAppointment(fixture, startsAt);
        mockMvc.perform(patch("/api/agendamentos/{id}/conclusao", appointmentId)
                        .header("Authorization", bearer(fixture.professionalToken())))
                .andExpect(status().isOk());
    }

    private void createCompletedAppointment(
            Fixture fixture, LocalDateTime startsAt, LocalDateTime completedAt) {
        Appointment appointment = Appointment.schedule(
                fixture.establishment(), fixture.client(), fixture.professional(), fixture.service(), startsAt);
        appointment.complete(completedAt);
        appointmentRepository.saveAndFlush(appointment);
    }

    private Long createAppointment(Fixture fixture, LocalDateTime startsAt) {
        Appointment appointment = Appointment.schedule(
                fixture.establishment(), fixture.client(), fixture.professional(), fixture.service(), startsAt);
        return appointmentRepository.saveAndFlush(appointment).getId();
    }

    private void cancelAsClient(Fixture fixture, Long appointmentId) throws Exception {
        mockMvc.perform(patch("/api/agendamentos/{id}/cancelamento", appointmentId)
                        .header("Authorization", bearer(fixture.clientToken())))
                .andExpect(status().isOk());
    }

    private Fixture setUp(String suffix, String cnpj) throws Exception {
        MvcResult registration = mockMvc.perform(post("/api/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ownerName": "Administrador %s",
                                  "ownerEmail": "admin.%s@salonhub.test",
                                  "password": "senha123",
                                  "establishmentName": "Salão %s",
                                  "cnpj": "%s"
                                }
                                """.formatted(suffix, suffix, suffix, cnpj)))
                .andExpect(status().isCreated())
                .andReturn();
        Number establishmentId = JsonPath.read(
                registration.getResponse().getContentAsString(), "$.establishmentId");
        String administratorToken = login("admin.%s@salonhub.test".formatted(suffix));

        MvcResult professional = mockMvc.perform(post("/api/funcionarios")
                        .header("Authorization", bearer(administratorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Profissional %s","email":"professional.%s@salonhub.test"}
                                """.formatted(suffix, suffix)))
                .andExpect(status().isCreated())
                .andReturn();
        String firstAccessToken = JsonPath.read(
                professional.getResponse().getContentAsString(), "$.firstAccessToken");
        completeFirstAccess(firstAccessToken, "senha123");
        String professionalToken = login("professional.%s@salonhub.test".formatted(suffix));

        mockMvc.perform(post("/api/servicos")
                        .header("Authorization", bearer(administratorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Corte %s","price":100.00,"commissionPercentage":20.00,"durationMinutes":60}
                                """.formatted(suffix)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Cliente %s","email":"client.%s@salonhub.test","phone":"44999990000","password":"senha123","passwordConfirmation":"senha123"}
                                """.formatted(suffix, suffix)))
                .andExpect(status().isCreated());
        String clientToken = login("client.%s@salonhub.test".formatted(suffix));

        Establishment establishment = establishmentRepository.findById(establishmentId.longValue()).orElseThrow();
        User professionalUser = userRepository.findByEmailIgnoreCase("professional.%s@salonhub.test".formatted(suffix))
                .filter(user -> user.getRole() == UserRole.PROFESSIONAL)
                .orElseThrow();
        User clientUser = userRepository.findByEmailIgnoreCase("client.%s@salonhub.test".formatted(suffix))
                .orElseThrow();
        EstablishmentService establishmentService = serviceRepository
                .findByEstablishmentId(establishmentId.longValue()).stream()
                .findFirst()
                .orElseThrow();

        return new Fixture(
                administratorToken, professionalToken, clientToken,
                establishment, professionalUser, clientUser, establishmentService);
    }

    private record Fixture(
            String administratorToken,
            String professionalToken,
            String clientToken,
            Establishment establishment,
            User professional,
            User client,
            EstablishmentService service) {
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"senha123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    private void completeFirstAccess(String token, String password) throws Exception {
        mockMvc.perform(post("/api/auth/primeiro-acesso")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","password":"%s"}
                                """.formatted(token, password)))
                .andExpect(status().isNoContent());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
