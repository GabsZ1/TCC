package br.com.salonhub.controller;

import br.com.salonhub.entity.*;
import br.com.salonhub.repository.*;
import br.com.salonhub.service.auth.TokenService;
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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Verifica as operações HTTP de gestão do estabelecimento. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EstablishmentControllerIntegrationTests {

    private static final String ENDPOINT = "/api/estabelecimentos/me";
    private static final String VALID_UPDATE = """
            {"name":"  Salão Atualizado  ","email":"contato@salonhub.test"}
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
                .andExpect(jsonPath("$.cnpj").value("12345678000195"));

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
                                {"name":"Novo nome","email":""}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cnpj").value("12345678000195"))
                .andExpect(jsonPath("$.email").isEmpty());
    }

    @Test
    void shouldKeepCnpjWhenUpdatingRegistration() throws Exception {
        User admin = administrator("registration", "12345678000195");

        mockMvc.perform(put(ENDPOINT).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Nome atualizado","email":""}
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
                                {"name":" ","email":"invalido"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Dados inválidos"))
                .andExpect(jsonPath("$.fields.name").value("O nome do estabelecimento é obrigatório"))
                .andExpect(jsonPath("$.fields.email").value("E-mail em formato inválido"));
    }

    @Test
    void shouldBlockBusinessHourChangeThatWouldOrphanFutureConfirmedAppointment() throws Exception {
        User admin = administrator("hours-future", "12345678000195");
        Establishment establishment = admin.getEstablishment();
        User professional = user("professional-hours-future", UserRole.PROFESSIONAL, establishment);
        User client = user("client-hours-future", UserRole.CLIENT, null);
        EstablishmentService service = serviceRepository.saveAndFlush(EstablishmentService.builder()
                .establishment(establishment).name("Corte no fim do dia").price(new BigDecimal("50.00"))
                .commissionPercentage(BigDecimal.ZERO).durationMinutes(30).active(true).build());
        LocalDateTime startsAt = LocalDate.now().plusDays(2).atTime(17, 0);
        appointmentRepository.saveAndFlush(schedule(
                establishment, client, professional, service, startsAt));

        mockMvc.perform(put(ENDPOINT).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(businessHoursPayload("Expediente reduzido", "09:00", "16:00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Não foi possível alterar o expediente: 1 agendamento(s) confirmado(s) futuro(s) "
                                + "ou em andamento ficariam fora do novo horário. Cancele-os, aguarde a conclusão "
                                + "ou ajuste o expediente para mantê-los dentro do horário."));

        entityManager.clear();
        assertThat(establishmentRepository.findById(establishment.getId()).orElseThrow().getName())
                .isEqualTo("Salão hours-future");

        mockMvc.perform(put(ENDPOINT).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(businessHoursPayload("Expediente compatível", "09:00", "18:00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Expediente compatível"));
    }

    @Test
    void shouldBlockClosingCurrentDayWhileConfirmedAppointmentIsInProgress() throws Exception {
        User admin = administrator("hours-in-progress", "12345678000195");
        Establishment establishment = admin.getEstablishment();
        User professional = user("professional-hours-in-progress", UserRole.PROFESSIONAL, establishment);
        User client = user("client-hours-in-progress", UserRole.CLIENT, null);
        EstablishmentService service = serviceRepository.saveAndFlush(EstablishmentService.builder()
                .establishment(establishment).name("Atendimento em andamento").price(new BigDecimal("50.00"))
                .commissionPercentage(BigDecimal.ZERO).durationMinutes(60).active(true).build());
        LocalDateTime startsAt = LocalDateTime.now().minusMinutes(10);
        appointmentRepository.saveAndFlush(schedule(
                establishment, client, professional, service, startsAt));

        mockMvc.perform(put(ENDPOINT).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(businessHoursWithClosedDayPayload(
                                "Tentativa de fechamento", startsAt.getDayOfWeek())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("em andamento")));
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
    void shouldBlockBookingsAndPreserveFutureConfirmedAppointment() throws Exception {
        User admin = administrator("future-pending", "12345678000195");
        Establishment establishment = admin.getEstablishment();
        User professional = user("professional-future-pending", UserRole.PROFESSIONAL, establishment);
        User client = user("client-future-pending", UserRole.CLIENT, null);
        EstablishmentService service = serviceRepository.saveAndFlush(EstablishmentService.builder()
                .establishment(establishment).name("Corte futuro").price(new BigDecimal("50.00"))
                .commissionPercentage(BigDecimal.ZERO).durationMinutes(30).active(true).build());
        appointmentRepository.saveAndFlush(schedule(
                establishment, client, professional, service, LocalDateTime.now().plusDays(1)));

        mockMvc.perform(patch(ENDPOINT + "/bloqueio-agendamentos").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingsBlocked").value(true));

        Establishment blocked = establishmentRepository.findById(establishment.getId()).orElseThrow();
        assertThat(blocked.isActive()).isTrue();
        assertThat(blocked.isBookingsBlocked()).isTrue();
        assertThat(appointmentRepository.findAll()).anyMatch(appointment ->
                appointment.getEstablishment().getId().equals(establishment.getId())
                        && appointment.getStatus() == AppointmentStatus.CONFIRMED);
        mockMvc.perform(post("/api/estabelecimentos/{id}/agendamentos", establishment.getId())
                        .header("Authorization", bearer(client)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"serviceId":%d,"professionalId":%d,"startsAt":"%s"}
                                """.formatted(service.getId(), professional.getId(),
                                LocalDateTime.now().plusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0))))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(ENDPOINT + "/bloqueio-agendamentos").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingsBlocked").value(false));
    }

    @Test
    void shouldRejectPauseWithConfirmedAppointmentInProgress() throws Exception {
        User admin = administrator("ongoing-pending", "12345678000195");
        Establishment establishment = admin.getEstablishment();
        User professional = user("professional-ongoing-pending", UserRole.PROFESSIONAL, establishment);
        User client = user("client-ongoing-pending", UserRole.CLIENT, null);
        EstablishmentService service = serviceRepository.saveAndFlush(EstablishmentService.builder()
                .establishment(establishment).name("Corte em andamento").price(new BigDecimal("50.00"))
                .commissionPercentage(BigDecimal.ZERO).durationMinutes(60).active(true).build());
        appointmentRepository.saveAndFlush(schedule(
                establishment, client, professional, service, LocalDateTime.now().minusMinutes(30)));

        mockMvc.perform(patch(ENDPOINT + "/pausa").header("Authorization", bearer(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Não é possível pausar enquanto houver atendimentos futuros em aberto. "
                                + "Cancele ou conclua os atendimentos confirmados."));

        assertThat(establishmentRepository.findById(establishment.getId()).orElseThrow().isPaused()).isFalse();
    }

    @Test
    void shouldAllowPauseWhenOnlyConfirmedAppointmentHasAlreadyEnded() throws Exception {
        User admin = administrator("pause-old-confirmed", "42345678000116");
        Establishment establishment = admin.getEstablishment();
        User professional = user("professional-pause-old", UserRole.PROFESSIONAL, establishment);
        User client = user("client-pause-old", UserRole.CLIENT, null);
        EstablishmentService service = serviceRepository.saveAndFlush(EstablishmentService.builder()
                .establishment(establishment).name("Corte antigo").price(new BigDecimal("50.00"))
                .commissionPercentage(BigDecimal.ZERO).durationMinutes(30).active(true).build());
        appointmentRepository.saveAndFlush(schedule(establishment, client, professional, service,
                LocalDateTime.now().minusDays(60)));

        mockMvc.perform(patch(ENDPOINT + "/pausa").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paused").value(true));
    }

    @Test
    void shouldPauseWithoutPendingAppointmentsBlockProfessionalsAndAllowAdministratorToResume() throws Exception {
        User admin = administrator("pause-empty", "42345678000116");
        User professional = user("professional-pause-empty", UserRole.PROFESSIONAL, admin.getEstablishment());

        mockMvc.perform(patch(ENDPOINT + "/pausa").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paused").value(true))
                .andExpect(jsonPath("$.pausedBySystem").value(false));
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(professional)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(admin)))
                .andExpect(status().isOk());

        mockMvc.perform(delete(ENDPOINT + "/pausa").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paused").value(false));
    }


    private List<MockHttpServletRequestBuilder> establishmentRequests() {
        return List.of(get(ENDPOINT), patch(ENDPOINT + "/pausa"), delete(ENDPOINT + "/pausa"),
                patch(ENDPOINT + "/bloqueio-agendamentos"), delete(ENDPOINT + "/bloqueio-agendamentos"),
                put(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE));
    }

    private String businessHoursPayload(String name, String openingTime, String closingTime) {
        StringBuilder builder = new StringBuilder("{\"name\":\"").append(name)
                .append("\",\"businessHours\":[");
        DayOfWeek[] days = DayOfWeek.values();
        for (int i = 0; i < days.length; i++) {
            builder.append("{\"dayOfWeek\":\"").append(days[i])
                    .append("\",\"closed\":false,\"openingTime\":\"").append(openingTime)
                    .append("\",\"closingTime\":\"").append(closingTime).append("\"}");
            if (i < days.length - 1) builder.append(",");
        }
        return builder.append("]}").toString();
    }

    private String businessHoursWithClosedDayPayload(String name, DayOfWeek closedDay) {
        StringBuilder builder = new StringBuilder("{\"name\":\"").append(name)
                .append("\",\"businessHours\":[");
        DayOfWeek[] days = DayOfWeek.values();
        for (int i = 0; i < days.length; i++) {
            DayOfWeek day = days[i];
            builder.append("{\"dayOfWeek\":\"").append(day).append("\"");
            if (day == closedDay) {
                builder.append(",\"closed\":true,\"openingTime\":null,\"closingTime\":null}");
            } else {
                builder.append(",\"closed\":false,\"openingTime\":\"00:00\",\"closingTime\":\"23:59\"}");
            }
            if (i < days.length - 1) builder.append(",");
        }
        return builder.append("]}").toString();
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
