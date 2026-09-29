package br.com.salonhub.controller;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US 2.15 — Visualizar atendimentos realizados (CT01 a CT06).
 * Serviços usados: "Corte" R$ 100,00 com 20% (R$ 20,00 de comissão) e
 * "Escova" R$ 50,00 com 10% (R$ 5,00). Os atendimentos são montados via
 * repositório para controlar exatamente a data de conclusão.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProfessionalCompletedServicesIntegrationTests {

    private static final String ENDPOINT = "/api/profissionais/me/atendimentos-realizados";

    @Autowired private MockMvc mockMvc;
    @Autowired private EstablishmentRepository establishmentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EstablishmentServiceRepository serviceRepository;
    @Autowired private AppointmentRepository appointmentRepository;

    @Test
    void shouldListOnlyOwnCompletedServicesOfTheCurrentMonthWhenNoFilterIsApplied() throws Exception {
        Fixture fixture = fixture();
        YearMonth currentMonth = YearMonth.now(ZoneId.of("America/Sao_Paulo"));
        LocalDateTime thisMonth = currentMonth.atDay(1).atTime(10, 0);

        Long mine = completed(fixture, fixture.professional(), fixture.haircut(), thisMonth);
        completed(fixture, fixture.professional(), fixture.blowDry(), currentMonth.minusMonths(1).atDay(15).atTime(10, 0));
        completed(fixture, fixture.otherProfessional(), fixture.haircut(), thisMonth);
        cancelled(fixture, fixture.professional(), thisMonth.plusHours(2));
        invalidated(fixture, fixture.professional(), thisMonth.plusHours(4));

        perform(fixture.professional(), get(ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startDate").value(currentMonth.atDay(1).toString()))
                .andExpect(jsonPath("$.endDate").value(currentMonth.atEndOfMonth().toString()))
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.entries[0].appointmentId").value(mine))
                .andExpect(jsonPath("$.entries[0].serviceName").value("Corte"))
                .andExpect(jsonPath("$.entries[0].clientName").value(fixture.client().getName()))
                .andExpect(jsonPath("$.entries[0].servicePrice").value(100.0))
                .andExpect(jsonPath("$.entries[0].commissionPercentage").value(20.0))
                .andExpect(jsonPath("$.entries[0].commissionAmount").value(20.0))
                .andExpect(jsonPath("$.summary.totalServices").value(1))
                .andExpect(jsonPath("$.summary.totalServiceValue").value(100.0))
                .andExpect(jsonPath("$.summary.totalCommission").value(20.0));
    }

    @Test
    void shouldFilterByPeriodUsingTheCompletionDate() throws Exception {
        Fixture fixture = fixture();
        Appointment appointment = schedule(fixture.establishment(), fixture.client(),
                fixture.professional(), fixture.haircut(), LocalDateTime.of(2026, 2, 27, 18, 0));
        appointment.complete(LocalDateTime.of(2026, 3, 2, 9, 0));
        appointmentRepository.saveAndFlush(appointment);

        perform(fixture.professional(), get(ENDPOINT).param("startDate", "2026-03-01").param("endDate", "2026-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(1));
        perform(fixture.professional(), get(ENDPOINT).param("startDate", "2026-02-01").param("endDate", "2026-02-28"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(0));
    }

    @Test
    void shouldCombinePeriodAndServiceFiltersAndSummarizeTheResult() throws Exception {
        Fixture fixture = fixture();
        Long first = completed(fixture, fixture.professional(), fixture.haircut(), LocalDateTime.of(2026, 3, 5, 10, 0));
        Long second = completed(fixture, fixture.professional(), fixture.blowDry(), LocalDateTime.of(2026, 3, 12, 10, 0));
        Long third = completed(fixture, fixture.professional(), fixture.haircut(), LocalDateTime.of(2026, 3, 20, 15, 0));
        completed(fixture, fixture.professional(), fixture.blowDry(), LocalDateTime.of(2026, 4, 2, 10, 0));

        // Sem filtro de serviço: mais recentes primeiro e resumo de todos do período.
        perform(fixture.professional(), get(ENDPOINT).param("startDate", "2026-03-01").param("endDate", "2026-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[*].appointmentId").value(contains(
                        third.intValue(), second.intValue(), first.intValue())))
                .andExpect(jsonPath("$.summary.totalServices").value(3))
                .andExpect(jsonPath("$.summary.totalServiceValue").value(250.0))
                .andExpect(jsonPath("$.summary.totalCommission").value(45.0));

        // Período + tipo de serviço.
        perform(fixture.professional(), get(ENDPOINT)
                        .param("startDate", "2026-03-01").param("endDate", "2026-03-31")
                        .param("serviceId", fixture.blowDry().getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceId").value(fixture.blowDry().getId()))
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.entries[0].appointmentId").value(second))
                .andExpect(jsonPath("$.summary.totalServices").value(1))
                .andExpect(jsonPath("$.summary.totalServiceValue").value(50.0))
                .andExpect(jsonPath("$.summary.totalCommission").value(5.0));
    }

    @Test
    void shouldKeepTheCommissionFrozenAtCompletionEvenIfTheServiceChangesLater() throws Exception {
        Fixture fixture = fixture();
        completed(fixture, fixture.professional(), fixture.haircut(), LocalDateTime.of(2026, 3, 5, 10, 0));

        fixture.haircut().updateDetails("Corte", null, new BigDecimal("150.00"), new BigDecimal("30.00"), 60);
        serviceRepository.saveAndFlush(fixture.haircut());

        perform(fixture.professional(), get(ENDPOINT).param("startDate", "2026-03-01").param("endDate", "2026-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].servicePrice").value(100.0))
                .andExpect(jsonPath("$.entries[0].commissionPercentage").value(20.0))
                .andExpect(jsonPath("$.entries[0].commissionAmount").value(20.0));
    }

    @Test
    void shouldOfferAsFilterOptionsOnlyServicesTheProfessionalHasCompleted() throws Exception {
        Fixture fixture = fixture();
        completed(fixture, fixture.professional(), fixture.haircut(), LocalDateTime.of(2026, 1, 5, 10, 0));
        completed(fixture, fixture.professional(), fixture.blowDry(), LocalDateTime.of(2026, 3, 5, 10, 0));
        completed(fixture, fixture.otherProfessional(), fixture.coloring(), LocalDateTime.of(2026, 3, 5, 10, 0));

        perform(fixture.professional(), get(ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableServices[*].name").value(contains("Corte", "Escova")));
    }

    @Test
    void shouldReturnAnEmptyResultWithZeroedSummary() throws Exception {
        Fixture fixture = fixture();

        perform(fixture.professional(), get(ENDPOINT).param("startDate", "2026-03-01").param("endDate", "2026-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries").isEmpty())
                .andExpect(jsonPath("$.summary.totalServices").value(0))
                .andExpect(jsonPath("$.summary.totalServiceValue").value(0.0))
                .andExpect(jsonPath("$.summary.totalCommission").value(0.0));
    }

    @Test
    void shouldRejectInvalidOrIncompletePeriodAndUnknownService() throws Exception {
        Fixture fixture = fixture();
        Fixture otherEstablishment = fixture();

        perform(fixture.professional(), get(ENDPOINT).param("startDate", "2026-03-31").param("endDate", "2026-03-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Período informado é inválido: a data inicial é posterior à data final."));
        perform(fixture.professional(), get(ENDPOINT).param("startDate", "2026-03-01"))
                .andExpect(status().isBadRequest());
        perform(fixture.professional(), get(ENDPOINT)
                        .param("serviceId", otherEstablishment.haircut().getId().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Tipo de serviço não encontrado neste estabelecimento."));
    }

    @Test
    void shouldBeAvailableOnlyToProfessionals() throws Exception {
        Fixture fixture = fixture();

        perform(fixture.administrator(), get(ENDPOINT)).andExpect(status().isForbidden());
        perform(fixture.client(), get(ENDPOINT)).andExpect(status().isForbidden());
        mockMvc.perform(get(ENDPOINT)).andExpect(status().isUnauthorized());
    }

    private ResultActions perform(User user, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(jwt().jwt(token -> token.subject(user.getId().toString()))));
    }

    private Long completed(Fixture fixture, User professional, EstablishmentService service, LocalDateTime completedAt) {
        Appointment appointment = schedule(
                fixture.establishment(), fixture.client(), professional, service, completedAt.minusMinutes(60));
        appointment.complete(completedAt);
        return appointmentRepository.saveAndFlush(appointment).getId();
    }

    private void cancelled(Fixture fixture, User professional, LocalDateTime startsAt) {
        Appointment appointment = schedule(
                fixture.establishment(), fixture.client(), professional, fixture.haircut(), startsAt);
        appointment.cancel();
        appointmentRepository.saveAndFlush(appointment);
    }

    private void invalidated(Fixture fixture, User professional, LocalDateTime completedAt) {
        Appointment appointment = schedule(
                fixture.establishment(), fixture.client(), professional, fixture.haircut(), completedAt.minusMinutes(60));
        appointment.complete(completedAt);
        appointment.invalidate("Registrado por engano");
        appointmentRepository.saveAndFlush(appointment);
    }

    private Fixture fixture() {
        String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String cnpj = String.format("%014d", Math.floorMod(System.nanoTime(), 100_000_000_000_000L));
        Establishment establishment = establishmentRepository.saveAndFlush(Establishment.builder()
                .name("Salão realizados " + unique).cnpj(cnpj).build());
        User administrator = user("Admin " + unique, "admin-" + unique, UserRole.ADMIN, establishment);
        User professional = user("Profissional " + unique, "prof-" + unique, UserRole.PROFESSIONAL, establishment);
        User otherProfessional = user("Outro " + unique, "outro-" + unique, UserRole.PROFESSIONAL, establishment);
        User client = user("Cliente " + unique, "cliente-" + unique, UserRole.CLIENT, null);
        return new Fixture(establishment, administrator, professional, otherProfessional, client,
                service(establishment, "Corte", "100.00", "20.00"),
                service(establishment, "Escova", "50.00", "10.00"),
                service(establishment, "Coloração", "200.00", "25.00"));
    }

    private User user(String name, String emailPrefix, UserRole role, Establishment establishment) {
        return userRepository.saveAndFlush(User.builder()
                .name(name)
                .email(emailPrefix + "@realizados.test")
                .password("{noop}nao-usada")
                .role(role)
                .establishment(establishment)
                .build());
    }

    private EstablishmentService service(Establishment establishment, String name, String price, String commission) {
        return serviceRepository.saveAndFlush(EstablishmentService.builder()
                .establishment(establishment)
                .name(name)
                .price(new BigDecimal(price))
                .commissionPercentage(new BigDecimal(commission))
                .durationMinutes(60)
                .active(true)
                .build());
    }

    private record Fixture(
            Establishment establishment,
            User administrator,
            User professional,
            User otherProfessional,
            User client,
            EstablishmentService haircut,
            EstablishmentService blowDry,
            EstablishmentService coloring) {
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
