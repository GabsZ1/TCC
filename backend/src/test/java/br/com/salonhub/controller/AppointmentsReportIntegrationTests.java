package br.com.salonhub.controller;

import br.com.salonhub.support.TestDocuments;

import br.com.salonhub.entity.Appointment;
import br.com.salonhub.entity.Customer;
import br.com.salonhub.repository.CustomerRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
class AppointmentsReportIntegrationTests extends FirstAccessIntegrationSupport {

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
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atTime(10, 0);
        createAppointment(fixture, fixture.professional(), monthStart, Status.COMPLETED);
        createAppointment(fixture, fixture.professional(), monthStart.plusHours(2), Status.CANCELLED);
        createAppointment(fixture, fixture.professional(), monthStart.plusHours(4), Status.CONFIRMED);

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
    void shouldIncludeNoShowsInTotalsAndCompletionRate() throws Exception {
        Fixture fixture = setUp("appointments-report-no-show", "45000000010049");
        LocalDateTime start = LocalDate.now().withDayOfMonth(1).atTime(10, 0);
        createAppointment(fixture, fixture.professional(), start, Status.COMPLETED);
        createAppointment(fixture, fixture.professional(), start.plusHours(2), Status.NO_SHOW);

        mockMvc.perform(get("/api/estabelecimentos/me/relatorio-atendimentos")
                        .header("Authorization", bearer(fixture.administratorToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].noShowCount").value(1))
                .andExpect(jsonPath("$.entries[0].totalCount").value(2))
                .andExpect(jsonPath("$.entries[0].completionRate").value(50.0))
                .andExpect(jsonPath("$.totalNoShows").value(1));
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

    @Test
    void shouldComputeAttendanceRatesWithoutCountingInvalidatedRecords() throws Exception {
        Fixture fixture = setUp("appointments-report-rates", "45001100011146");
        LocalDateTime start = LocalDate.now().withDayOfMonth(1).atTime(9, 0);
        createAppointment(fixture, fixture.professional(), start, Status.COMPLETED);
        createAppointment(fixture, fixture.professional(), start.plusHours(1), Status.CANCELLED);
        createAppointment(fixture, fixture.professional(), start.plusHours(2), Status.NO_SHOW);
        createAppointment(fixture, fixture.professional(), start.plusHours(3), Status.INVALIDATED);

        // Desfechos = concluído + cancelado + falta = 3. O invalidado conta no total, mas não nas taxas.
        mockMvc.perform(get("/api/estabelecimentos/me/relatorio-atendimentos")
                        .header("Authorization", bearer(fixture.administratorToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].totalCount").value(4))
                .andExpect(jsonPath("$.entries[0].completionRate").value(33.33))
                .andExpect(jsonPath("$.entries[0].attendanceRate").value(50.0))
                .andExpect(jsonPath("$.entries[0].cancellationRate").value(33.33))
                .andExpect(jsonPath("$.entries[0].noShowRate").value(33.33))
                .andExpect(jsonPath("$.completionRate").value(33.33))
                .andExpect(jsonPath("$.attendanceRate").value(50.0));

        mockMvc.perform(get("/api/estabelecimentos/me/relatorio-atendimentos/csv")
                        .header("Authorization", bearer(fixture.administratorToken())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", containsString("relatorio-comparecimento_")))
                .andExpect(content().string(containsString("Taxa de comparecimento (%)")))
                .andExpect(content().string(containsString("33,33")));
    }

    @Test
    void shouldBuildTheFinancialReportFromCompletedAppointments() throws Exception {
        Fixture fixture = setUp("financial-report", "45001200011257");
        EstablishmentService barba = serviceRepository.saveAndFlush(EstablishmentService.builder()
                .establishment(fixture.establishment()).name("Barba financeiro").price(new BigDecimal("50.00"))
                .commissionPercentage(new BigDecimal("10.00")).durationMinutes(30).build());
        LocalDateTime start = LocalDate.now().withDayOfMonth(1).atTime(9, 0);
        // Corte: 100,00 com 20% de comissão; barba: 50,00 com 10%.
        createAppointment(fixture, fixture.professional(), start, Status.COMPLETED);
        createAppointment(fixture, fixture.professional(), start.plusHours(1), Status.COMPLETED);
        createAppointment(fixture, fixture.professional(), fixture.service(), start.plusHours(2), Status.CANCELLED);
        createAppointment(fixture, fixture.professional(), barba, start.plusHours(3), Status.COMPLETED);
        createAppointment(fixture, fixture.professional(), barba, start.plusHours(4), Status.INVALIDATED);

        mockMvc.perform(get("/api/estabelecimentos/me/relatorio-financeiro")
                        .header("Authorization", bearer(fixture.administratorToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedCount").value(3))
                .andExpect(jsonPath("$.grossRevenue").value(250.0))
                .andExpect(jsonPath("$.totalCommission").value(45.0))
                .andExpect(jsonPath("$.revenueAfterCommission").value(205.0))
                .andExpect(jsonPath("$.averageTicket").value(83.33))
                .andExpect(jsonPath("$.byService.length()").value(2))
                .andExpect(jsonPath("$.byService[0].name").value(fixture.service().getName()))
                .andExpect(jsonPath("$.byService[0].completedCount").value(2))
                .andExpect(jsonPath("$.byProfessional[0].revenue").value(250.0));

        mockMvc.perform(get("/api/estabelecimentos/me/relatorio-financeiro")
                        .header("Authorization", bearer(fixture.administratorToken()))
                        .param("serviceId", String.valueOf(barba.getId())))
                .andExpect(jsonPath("$.completedCount").value(1))
                .andExpect(jsonPath("$.grossRevenue").value(50.0));

        mockMvc.perform(get("/api/estabelecimentos/me/relatorio-financeiro/csv")
                        .header("Authorization", bearer(fixture.administratorToken())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Faturamento bruto (R$)")))
                .andExpect(content().string(containsString("250,00")));
        mockMvc.perform(get("/api/estabelecimentos/me/relatorio-comissoes/csv")
                        .header("Authorization", bearer(fixture.administratorToken())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("45,00")));

        mockMvc.perform(get("/api/estabelecimentos/me/relatorio-financeiro")
                        .header("Authorization", bearer(fixture.professionalToken())))
                .andExpect(status().isForbidden());
    }

    private enum Status { CONFIRMED, COMPLETED, CANCELLED, NO_SHOW, INVALIDATED }

    private Long createAppointment(Fixture fixture, User professional, LocalDateTime startsAt, Status status) {
        return createAppointment(fixture, professional, fixture.service(), startsAt, status);
    }

    private Long createAppointment(Fixture fixture, User professional, EstablishmentService service,
                                   LocalDateTime startsAt, Status status) {
        Appointment appointment = schedule(
                fixture.establishment(), fixture.client(), professional, service, startsAt);
        switch (status) {
            case INVALIDATED -> {
                appointment.complete(startsAt.plusMinutes(60));
                appointment.invalidate("Registro feito por engano");
            }
            case COMPLETED -> appointment.complete(startsAt.plusMinutes(60));
            case CANCELLED -> appointment.cancel();
            case NO_SHOW -> appointment.markNoShow();
            case CONFIRMED -> { }
        }
        return appointmentRepository.saveAndFlush(appointment).getId();
    }

    private User addSecondProfessional(Fixture fixture, String suffix) throws Exception {
        MvcResult secondProfessional = mockMvc.perform(post("/api/funcionarios")
                        .header("Authorization", bearer(fixture.administratorToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Segundo Profissional","email":"second.professional.%s@salonhub.test","occupation":"Cabeleireiro(a)","cpf":"%s","hireDate":"2025-01-02"}
                                """.formatted(suffix, TestDocuments.cpf())))
                .andExpect(status().isCreated())
                .andReturn();
        String firstAccessToken = firstAccessToken(secondProfessional.getResponse().getContentAsString());
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
                                {"name":"Profissional %s","email":"professional.%s@salonhub.test","occupation":"Cabeleireiro(a)","cpf":"%s","hireDate":"2025-01-02"}
                                """.formatted(suffix, suffix, TestDocuments.cpf())))
                .andExpect(status().isCreated())
                .andReturn();
        String firstAccessToken = firstAccessToken(professional.getResponse().getContentAsString());
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
