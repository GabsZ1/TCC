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

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cobre o relatório de comissões por funcionário e período (US 2.21).
 * O serviço usado nos fixtures custa R$ 100,00 com 20% de comissão, ou
 * seja, cada atendimento concluído gera R$ 20,00 de comissão.
 *
 * Os atendimentos concluídos são montados diretamente via repositório
 * (em vez do endpoint de conclusão) para poder controlar exatamente o
 * instante de conclusão (`completedAt`) — o endpoint sempre usa o
 * relógio real no momento da chamada, o que tornaria impossível simular
 * "concluído há 40 dias" de forma determinística.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CommissionReportIntegrationTests extends FirstAccessIntegrationSupport {

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
    void shouldReturnCurrentMonthReportWhenNoFilterIsApplied() throws Exception {
        Fixture fixture = setUp("commissions-current-month", "44000000010049");
        createCompletedAppointment(fixture, fixture.professional(), LocalDateTime.now().minusDays(1));

        mockMvc.perform(get("/api/estabelecimentos/me/relatorio-comissoes")
                        .header("Authorization", bearer(fixture.administratorToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.entries[0].professionalName").value(fixture.professional().getName()))
                .andExpect(jsonPath("$.entries[0].completedServicesCount").value(1))
                .andExpect(jsonPath("$.entries[0].totalServiceValue").value(100.0))
                .andExpect(jsonPath("$.entries[0].totalCommission").value(20.0))
                .andExpect(jsonPath("$.totalCommission").value(20.0));
    }

    @Test
    void shouldFilterByAGivenProfessional() throws Exception {
        Fixture fixture = setUp("commissions-by-professional", "44000100010156");
        User secondProfessional = addSecondProfessional(fixture, "commissions-by-professional");

        createCompletedAppointment(fixture, fixture.professional(), LocalDateTime.now().minusHours(2));
        createCompletedAppointment(fixture, secondProfessional, LocalDateTime.now().minusHours(1));

        mockMvc.perform(get("/api/estabelecimentos/me/relatorio-comissoes")
                        .header("Authorization", bearer(fixture.administratorToken()))
                        .param("professionalId", String.valueOf(fixture.professional().getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.entries[0].professionalId").value(fixture.professional().getId()))
                .andExpect(jsonPath("$.totalCommission").value(20.0));
    }

    @Test
    void shouldFilterByAValidPeriodUsingTheCompletionDate() throws Exception {
        Fixture fixture = setUp("commissions-period", "44000200010263");
        createCompletedAppointment(fixture, fixture.professional(), LocalDateTime.now().minusDays(40));
        createCompletedAppointment(fixture, fixture.professional(), LocalDateTime.now().minusDays(1));

        LocalDate today = LocalDate.now();
        mockMvc.perform(get("/api/estabelecimentos/me/relatorio-comissoes")
                        .header("Authorization", bearer(fixture.administratorToken()))
                        .param("startDate", today.minusDays(3).toString())
                        .param("endDate", today.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].completedServicesCount").value(1))
                .andExpect(jsonPath("$.totalCommission").value(20.0));
    }

    @Test
    void shouldExcludeInvalidatedAppointmentsFromTheReport() throws Exception {
        Fixture fixture = setUp("commissions-invalidated", "44000300010370");
        Long appointmentId = createCompletedAppointment(fixture, fixture.professional(), LocalDateTime.now().minusHours(2));
        mockMvc.perform(patch("/api/agendamentos/{id}/invalidacao", appointmentId)
                        .header("Authorization", bearer(fixture.administratorToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Cliente não compareceu"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/estabelecimentos/me/relatorio-comissoes")
                        .header("Authorization", bearer(fixture.administratorToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries").isEmpty())
                .andExpect(jsonPath("$.totalCommission").value(0.0));
    }

    @Test
    void shouldFreezeCommissionPercentageRegisteredAtCompletionEvenIfServiceChangesLater() throws Exception {
        Fixture fixture = setUp("commissions-frozen-percentage", "44000400010488");
        createCompletedAppointment(fixture, fixture.professional(), LocalDateTime.now().minusHours(2));

        mockMvc.perform(put("/api/servicos/{id}", fixture.service().getId())
                        .header("Authorization", bearer(fixture.administratorToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","price":100.00,"commissionPercentage":30.00,"durationMinutes":60}
                                """.formatted(fixture.service().getName())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/estabelecimentos/me/relatorio-comissoes")
                        .header("Authorization", bearer(fixture.administratorToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].totalCommission").value(20.0))
                .andExpect(jsonPath("$.entries[0].averageCommissionPercentage").value(20.0));
    }

    @Test
    void shouldShowOverallTotalAcrossMultipleProfessionals() throws Exception {
        Fixture fixture = setUp("commissions-total", "44000500010595");
        User secondProfessional = addSecondProfessional(fixture, "commissions-total");

        createCompletedAppointment(fixture, fixture.professional(), LocalDateTime.now().minusHours(2));
        createCompletedAppointment(fixture, secondProfessional, LocalDateTime.now().minusHours(1));

        mockMvc.perform(get("/api/estabelecimentos/me/relatorio-comissoes")
                        .header("Authorization", bearer(fixture.administratorToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.totalCommission").value(40.0));
    }

    @Test
    void shouldForbidProfessionalFromAccessingReport() throws Exception {
        Fixture fixture = setUp("commissions-forbidden-professional", "44000600010600");

        mockMvc.perform(get("/api/estabelecimentos/me/relatorio-comissoes")
                        .header("Authorization", bearer(fixture.professionalToken())))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectPeriodFilterMissingOneOfTheBounds() throws Exception {
        Fixture fixture = setUp("commissions-partial-period", "44000800010817");

        mockMvc.perform(get("/api/estabelecimentos/me/relatorio-comissoes")
                        .header("Authorization", bearer(fixture.administratorToken()))
                        .param("startDate", LocalDate.now().toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldIsolateCommissionReportBetweenEstablishments() throws Exception {
        Fixture fixtureA = setUp("commissions-tenant-a", "44000900010924");
        Fixture fixtureB = setUp("commissions-tenant-b", "44001000011035");
        createCompletedAppointment(fixtureA, fixtureA.professional(), LocalDateTime.now().minusHours(1));

        mockMvc.perform(get("/api/estabelecimentos/me/relatorio-comissoes")
                        .header("Authorization", bearer(fixtureB.administratorToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries").isEmpty())
                .andExpect(jsonPath("$.totalCommission").value(0.0));
    }

    private Long createCompletedAppointment(Fixture fixture, User professional, LocalDateTime completedAt) {
        Appointment appointment = schedule(
                fixture.establishment(), fixture.client(), professional, fixture.service(),
                completedAt.minusMinutes(90));
        appointment.complete(completedAt);
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
