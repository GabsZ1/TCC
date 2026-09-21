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

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cobre o relatório de atendimentos por profissional e período: ao
 * contrário do relatório de comissões (que só olha para atendimentos
 * concluídos, agrupados pela data de conclusão), este relatório soma
 * TODAS as situações — confirmado, concluído, cancelado e invalidado —
 * agrupadas pela data agendada (startsAt), para mostrar o volume total
 * recebido por cada profissional e sua taxa de conclusão.
 *
 * Os agendamentos são montados diretamente via repositório (em vez do
 * endpoint de criação) para poder controlar exatamente o instante
 * agendado (startsAt), inclusive no passado — o endpoint de criação não
 * permite agendar no passado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AppointmentsReportIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private EstablishmentRepository establishmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EstablishmentServiceRepository serviceRepository;

    @Test
    void shouldCountAppointmentsByStatusForTheCurrentMonthWhenNoFilterIsApplied() throws Exception {
        Fixture fixture = setUp("appointments-report-current-month", "45000000010049");
        createAppointment(fixture, fixture.professional(), LocalDateTime.now().minusDays(1), Status.COMPLETED);
        createAppointment(fixture, fixture.professional(), LocalDateTime.now().minusHours(3), Status.CANCELLED);
        createAppointment(fixture, fixture.professional(), LocalDateTime.now().plusDays(2), Status.CONFIRMED);

        mockMvc.perform(get("/api/estabelecimentos/me/relatorio-atendimentos")
                        .header("Authorization", bearer(fixture.administratorToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.entries[0].professionalName").value(fixture.professional().getName()))
                .andExpect(jsonPath("$.entries[0].confirmedCount").value(1))
                .andExpect(jsonPath("$.entries[0].completedCount").value(1))
                .andExpect(jsonPath("$.entries[0].cancelledCount").value(1))
                .andExpect(jsonPath("$.entries[0].invalidatedCount").value(0))
                .andExpect(jsonPath("$.entries[0].totalCount").value(3))
                .andExpect(jsonPath("$.entries[0].completionRate").value(50.0))
                .andExpect(jsonPath("$.totalConfirmed").value(1))
                .andExpect(jsonPath("$.totalCompleted").value(1))
                .andExpect(jsonPath("$.totalCancelled").value(1))
                .andExpect(jsonPath("$.totalInvalidated").value(0));
    }

    @Test
    void shouldFilterByAGivenProfessional() throws Exception {
        Fixture fixture = setUp("appointments-report-by-professional", "45000100010156");
        User secondProfessional = addSecondProfessional(fixture, "appointments-report-by-professional");

        createAppointment(fixture, fixture.professional(), LocalDateTime.now().minusHours(2), Status.COMPLETED);
        createAppointment(fixture, secondProfessional, LocalDateTime.now().minusHours(1), Status.COMPLETED);

        mockMvc.perform(get("/api/estabelecimentos/me/relatorio-atendimentos")
                        .header("Authorization", bearer(fixture.administratorToken()))
                        .param("professionalId", String.valueOf(fixture.professional().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.entries[0].professionalId").value(fixture.professional().getId()))
                .andExpect(jsonPath("$.totalCompleted").value(1));
    }

    @Test
    void shouldFilterByAValidPeriodUsingTheScheduledDate() throws Exception {
        Fixture fixture = setUp("appointments-report-period", "45000200010263");
        createAppointment(fixture, fixture.professional(), LocalDateTime.now().minusDays(40), Status.COMPLETED);
        createAppointment(fixture, fixture.professional(), LocalDateTime.now().minusDays(1), Status.COMPLETED);

        LocalDate today = LocalDate.now();
        mockMvc.perform(get("/api/estabelecimentos/me/relatorio-atendimentos")
                        .header("Authorization", bearer(fixture.administratorToken()))
                        .param("startDate", today.minusDays(3).toString())
                        .param("endDate", today.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].completedCount").value(1))
                .andExpect(jsonPath("$.totalCompleted").value(1));
    }

    @Test
    void shouldIncludeInvalidatedAppointmentsUnlikeTheCommissionReport() throws Exception {
        Fixture fixture = setUp("appointments-report-invalidated", "45000300010370");
        Long appointmentId = createAppointment(
                fixture, fixture.professional(), LocalDateTime.now().minusHours(2), Status.COMPLETED);
        mockMvc.perform(patch("/api/agendamentos/{id}/invalidacao", appointmentId)
                        .header("Authorization", bearer(fixture.administratorToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Cliente não compareceu"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/estabelecimentos/me/relatorio-atendimentos")
                        .header("Authorization", bearer(fixture.administratorToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].invalidatedCount").value(1))
                .andExpect(jsonPath("$.entries[0].completedCount").value(0))
                .andExpect(jsonPath("$.totalInvalidated").value(1));
    }

    @Test
    void shouldShowOverallTotalsAcrossMultipleProfessionals() throws Exception {
        Fixture fixture = setUp("appointments-report-total", "45000500010595");
        User secondProfessional = addSecondProfessional(fixture, "appointments-report-total");

        createAppointment(fixture, fixture.professional(), LocalDateTime.now().minusHours(2), Status.COMPLETED);
        createAppointment(fixture, secondProfessional, LocalDateTime.now().minusHours(1), Status.CANCELLED);

        mockMvc.perform(get("/api/estabelecimentos/me/relatorio-atendimentos")
                        .header("Authorization", bearer(fixture.administratorToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.totalCompleted").value(1))
                .andExpect(jsonPath("$.totalCancelled").value(1));
    }

    @Test
    void shouldForbidProfessionalFromAccessingReport() throws Exception {
        Fixture fixture = setUp("appointments-report-forbidden-professional", "45000600010600");

        mockMvc.perform(get("/api/estabelecimentos/me/relatorio-atendimentos")
                        .header("Authorization", bearer(fixture.professionalToken())))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldForbidClientFromAccessingReport() throws Exception {
        Fixture fixture = setUp("appointments-report-forbidden-client", "45000700010700");

        mockMvc.perform(get("/api/estabelecimentos/me/relatorio-atendimentos")
                        .header("Authorization", bearer(fixture.clientToken())))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectPeriodFilterMissingOneOfTheBounds() throws Exception {
        Fixture fixture = setUp("appointments-report-partial-period", "45000800010817");

        mockMvc.perform(get("/api/estabelecimentos/me/relatorio-atendimentos")
                        .header("Authorization", bearer(fixture.administratorToken()))
                        .param("startDate", LocalDate.now().toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldIsolateAppointmentsReportBetweenEstablishments() throws Exception {
        Fixture fixtureA = setUp("appointments-report-tenant-a", "45000900010924");
        Fixture fixtureB = setUp("appointments-report-tenant-b", "45001000011035");
        createAppointment(fixtureA, fixtureA.professional(), LocalDateTime.now().minusHours(1), Status.COMPLETED);

        mockMvc.perform(get("/api/estabelecimentos/me/relatorio-atendimentos")
                        .header("Authorization", bearer(fixtureB.administratorToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries").isEmpty())
                .andExpect(jsonPath("$.totalCompleted").value(0));
    }

    private enum Status { CONFIRMED, COMPLETED, CANCELLED }

    private Long createAppointment(Fixture fixture, User professional, LocalDateTime startsAt, Status status) {
        Appointment appointment = Appointment.schedule(
                fixture.establishment(), fixture.client(), professional, fixture.service(), startsAt);
        switch (status) {
            case COMPLETED -> appointment.complete(startsAt.plusMinutes(60));
            case CANCELLED -> appointment.cancel();
            case CONFIRMED -> { }
        }
        return appointmentRepository.saveAndFlush(appointment).getId();
    }

    private User addSecondProfessional(Fixture fixture, String suffix) throws Exception {
        MvcResult secondProfessional = mockMvc.perform(post("/api/funcionarios")
                        .header("Authorization", bearer(fixture.administratorToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Segundo Profissional","email":"second.professional.%s@salonhub.test"}
                                """.formatted(suffix)))
                .andExpect(status().isCreated())
                .andReturn();
        String firstAccessToken = JsonPath.read(
                secondProfessional.getResponse().getContentAsString(), "$.firstAccessToken");
        completeFirstAccess(firstAccessToken, "senha123");
        return userRepository.findByEmailIgnoreCase("second.professional.%s@salonhub.test".formatted(suffix))
                .filter(user -> user.getRole() == UserRole.PROFESSIONAL)
                .orElseThrow();
    }

    private Fixture setUp(String suffix, String cnpjSeed) throws Exception {
        String cnpj = validCnpj(cnpjSeed.substring(0, 12));
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
                                {"name":"Cliente %s","email":"client.%s@salonhub.test","password":"senha123"}
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

    private String validCnpj(String base) {
        String firstDigitBase = base;
        int firstDigit = cnpjDigit(firstDigitBase, new int[]{5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2});
        String secondDigitBase = firstDigitBase + firstDigit;
        int secondDigit = cnpjDigit(secondDigitBase, new int[]{6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2});
        return secondDigitBase + secondDigit;
    }

    private int cnpjDigit(String digits, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += Character.digit(digits.charAt(i), 10) * weights[i];
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
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
