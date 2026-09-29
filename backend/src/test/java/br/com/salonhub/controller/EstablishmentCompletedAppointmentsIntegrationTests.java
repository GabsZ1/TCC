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
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US 2.13 — Visualizar e filtrar atendimentos dos funcionários (CT01 a CT07).
 * "Corte" custa R$ 100,00 com 20% (R$ 20,00) e "Escova" R$ 50,00 com 10% (R$ 5,00).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EstablishmentCompletedAppointmentsIntegrationTests {

    private static final String ENDPOINT = "/api/estabelecimentos/me/atendimentos-realizados";

    @Autowired private MockMvc mockMvc;
    @Autowired private EstablishmentRepository establishmentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EstablishmentServiceRepository serviceRepository;
    @Autowired private AppointmentRepository appointmentRepository;

    @Test
    void shouldListAllFinishedAppointmentsOfTheEstablishmentWhenNoFilterIsApplied() throws Exception {
        Fixture fixture = fixture();
        Fixture otherEstablishment = fixture();
        Long oldest = completed(fixture, fixture.professional(), fixture.haircut(), LocalDateTime.of(2025, 11, 3, 10, 0));
        Long middle = completed(fixture, fixture.secondProfessional(), fixture.blowDry(), LocalDateTime.of(2026, 2, 10, 10, 0));
        Long newest = completed(fixture, fixture.professional(), fixture.blowDry(), LocalDateTime.of(2026, 3, 15, 10, 0));
        invalidated(fixture, LocalDateTime.of(2026, 3, 16, 10, 0));
        cancelled(fixture, LocalDateTime.of(2026, 3, 17, 10, 0));
        completed(otherEstablishment, otherEstablishment.professional(), otherEstablishment.haircut(),
                LocalDateTime.of(2026, 3, 15, 10, 0));

        perform(fixture.administrator(), get(ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startDate").doesNotExist())
                .andExpect(jsonPath("$.entries[*].appointmentId").value(contains(
                        newest.intValue(), middle.intValue(), oldest.intValue())))
                .andExpect(jsonPath("$.entries[0].professionalName").value(fixture.professional().getName()))
                .andExpect(jsonPath("$.entries[0].serviceName").value("Escova"))
                .andExpect(jsonPath("$.entries[0].servicePrice").value(50.0))
                .andExpect(jsonPath("$.entries[0].commissionAmount").value(5.0))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalServiceValue").value(200.0))
                .andExpect(jsonPath("$.totalCommission").value(30.0));
    }

    @Test
    void shouldFilterByProfessional() throws Exception {
        Fixture fixture = fixture();
        completed(fixture, fixture.professional(), fixture.haircut(), LocalDateTime.of(2026, 3, 5, 10, 0));
        Long fromSecond = completed(fixture, fixture.secondProfessional(), fixture.haircut(), LocalDateTime.of(2026, 3, 6, 10, 0));

        perform(fixture.administrator(), get(ENDPOINT)
                        .param("professionalId", fixture.secondProfessional().getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.professionalId").value(fixture.secondProfessional().getId()))
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.entries[0].appointmentId").value(fromSecond))
                .andExpect(jsonPath("$.totalCommission").value(20.0));
    }

    @Test
    void shouldCombineProfessionalAndPeriodUsingTheCompletionDate() throws Exception {
        Fixture fixture = fixture();
        // Agendado em fevereiro, concluído em março: deve contar em março.
        Appointment lateCompletion = schedule(fixture.establishment(), fixture.client(),
                fixture.professional(), fixture.haircut(), LocalDateTime.of(2026, 2, 28, 18, 0));
        lateCompletion.complete(LocalDateTime.of(2026, 3, 1, 9, 0));
        Long expected = appointmentRepository.saveAndFlush(lateCompletion).getId();
        completed(fixture, fixture.professional(), fixture.haircut(), LocalDateTime.of(2026, 4, 1, 10, 0));
        completed(fixture, fixture.secondProfessional(), fixture.haircut(), LocalDateTime.of(2026, 3, 10, 10, 0));

        perform(fixture.administrator(), get(ENDPOINT)
                        .param("startDate", "2026-03-01").param("endDate", "2026-03-31")
                        .param("professionalId", fixture.professional().getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startDate").value("2026-03-01"))
                .andExpect(jsonPath("$.endDate").value("2026-03-31"))
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.entries[0].appointmentId").value(expected));
    }

    @Test
    void shouldPaginateButKeepTotalsOverAllFilteredResults() throws Exception {
        Fixture fixture = fixture();
        Long first = completed(fixture, fixture.professional(), fixture.haircut(), LocalDateTime.of(2026, 3, 1, 10, 0));
        Long second = completed(fixture, fixture.professional(), fixture.haircut(), LocalDateTime.of(2026, 3, 2, 10, 0));
        Long third = completed(fixture, fixture.professional(), fixture.blowDry(), LocalDateTime.of(2026, 3, 3, 10, 0));

        perform(fixture.administrator(), get(ENDPOINT).param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[*].appointmentId").value(contains(third.intValue(), second.intValue())))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalServiceValue").value(250.0))
                .andExpect(jsonPath("$.totalCommission").value(45.0));

        perform(fixture.administrator(), get(ENDPOINT).param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[*].appointmentId").value(contains(first.intValue())))
                .andExpect(jsonPath("$.totalServiceValue").value(250.0));
    }

    @Test
    void shouldReturnEmptyResultWhenNothingMatches() throws Exception {
        Fixture fixture = fixture();
        completed(fixture, fixture.professional(), fixture.haircut(), LocalDateTime.of(2026, 3, 1, 10, 0));

        perform(fixture.administrator(), get(ENDPOINT).param("startDate", "2025-01-01").param("endDate", "2025-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalServiceValue").value(0.0))
                .andExpect(jsonPath("$.totalCommission").value(0.0));
    }

    @Test
    void shouldRejectInvalidFilters() throws Exception {
        Fixture fixture = fixture();
        Fixture otherEstablishment = fixture();

        perform(fixture.administrator(), get(ENDPOINT).param("startDate", "2026-03-31").param("endDate", "2026-03-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Período informado é inválido"));
        perform(fixture.administrator(), get(ENDPOINT).param("endDate", "2026-03-01"))
                .andExpect(status().isBadRequest());
        perform(fixture.administrator(), get(ENDPOINT)
                        .param("professionalId", otherEstablishment.professional().getId().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Funcionário não encontrado neste estabelecimento."));
        perform(fixture.administrator(), get(ENDPOINT)
                        .param("professionalId", fixture.client().getId().toString()))
                .andExpect(status().isBadRequest());
        perform(fixture.administrator(), get(ENDPOINT).param("size", "500"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldBeAvailableOnlyToTheEstablishmentAdministrator() throws Exception {
        Fixture fixture = fixture();

        perform(fixture.professional(), get(ENDPOINT)).andExpect(status().isForbidden());
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

    private void invalidated(Fixture fixture, LocalDateTime completedAt) {
        Appointment appointment = schedule(
                fixture.establishment(), fixture.client(), fixture.professional(), fixture.haircut(),
                completedAt.minusMinutes(60));
        appointment.complete(completedAt);
        appointment.invalidate("Lançado em duplicidade");
        appointmentRepository.saveAndFlush(appointment);
    }

    private void cancelled(Fixture fixture, LocalDateTime startsAt) {
        Appointment appointment = schedule(
                fixture.establishment(), fixture.client(), fixture.professional(), fixture.haircut(), startsAt);
        appointment.cancel();
        appointmentRepository.saveAndFlush(appointment);
    }

    private Fixture fixture() {
        String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String cnpj = String.format("%014d", Math.floorMod(System.nanoTime(), 100_000_000_000_000L));
        Establishment establishment = establishmentRepository.saveAndFlush(Establishment.builder()
                .name("Salão 2.13 " + unique).cnpj(cnpj).build());
        return new Fixture(
                establishment,
                user("Admin " + unique, "admin-" + unique, UserRole.ADMIN, establishment),
                user("Ana " + unique, "ana-" + unique, UserRole.PROFESSIONAL, establishment),
                user("Bruno " + unique, "bruno-" + unique, UserRole.PROFESSIONAL, establishment),
                user("Cliente " + unique, "cliente-" + unique, UserRole.CLIENT, null),
                service(establishment, "Corte", "100.00", "20.00"),
                service(establishment, "Escova", "50.00", "10.00"));
    }

    private User user(String name, String emailPrefix, UserRole role, Establishment establishment) {
        return userRepository.saveAndFlush(User.builder()
                .name(name)
                .email(emailPrefix + "@atendimentos213.test")
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
                .build());
    }

    private record Fixture(
            Establishment establishment,
            User administrator,
            User professional,
            User secondProfessional,
            User client,
            EstablishmentService haircut,
            EstablishmentService blowDry) {
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
