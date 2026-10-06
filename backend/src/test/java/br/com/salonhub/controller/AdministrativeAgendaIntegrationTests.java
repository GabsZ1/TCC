package br.com.salonhub.controller;

import br.com.salonhub.support.TestJwt;
import br.com.salonhub.entity.*;
import br.com.salonhub.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdministrativeAgendaIntegrationTests {
    private static final LocalDate DATE = LocalDate.of(2026, 9, 16);
    private static final String ENDPOINT = "/api/agendamentos/estabelecimento/me";

    @Autowired MockMvc mvc;
    @Autowired EstablishmentRepository establishments;
    @Autowired UserRepository users;
    @Autowired EstablishmentServiceRepository services;
    @Autowired AppointmentRepository appointments;
    @Autowired EstablishmentBusinessHourRepository establishmentHours;
    @Autowired ProfessionalCalendarEventRepository calendarEvents;
    @Autowired ProfessionalBusinessHourRepository professionalHours;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private User admin;
    private User professional;
    private User secondProfessional;
    private User otherProfessional;
    private final Map<String, Long> ids = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        Establishment establishment = establishments.saveAndFlush(Establishment.builder()
                .name("Agenda").cnpj("93939393000193").build());
        admin = user("admin", UserRole.ADMIN, establishment);
        professional = user("professional", UserRole.PROFESSIONAL, establishment);
        secondProfessional = user("second", UserRole.PROFESSIONAL, establishment);
        User client = user("client", UserRole.CLIENT, null);
        EstablishmentService service = service(establishment);
        add("before", client, professional, service, DATE.minusDays(1).atTime(23, 30), false);
        add("start", client, professional, service, DATE.atStartOfDay(), false);
        add("cancelled", client, professional, service, DATE.atTime(10, 0), true);
        add("afternoon", client, secondProfessional, service, DATE.atTime(15, 0), false);
        add("end", client, professional, service, DATE.plusDays(1).atStartOfDay(), false);

        Establishment other = establishments.saveAndFlush(Establishment.builder()
                .name("Other").cnpj("94949494000194").build());
        otherProfessional = user("other", UserRole.PROFESSIONAL, other);
        add("foreign", client, otherProfessional, service(other), DATE.atTime(12, 0), false);
    }

    @ParameterizedTest(name = "date={0}, professional={1}, status={2}")
    @CsvSource({
            "false,false,false,end afternoon cancelled start before",
            "true,false,false,afternoon cancelled start",
            "false,true,false,end cancelled start before",
            "true,true,false,cancelled start",
            "false,false,true,end afternoon start before",
            "true,false,true,afternoon start",
            "false,true,true,end start before",
            "true,true,true,start"
    })
    void shouldApplyOptionalFiltersAndKeepDescendingOrderAndTenantIsolation(
            boolean date, boolean filterProfessional, boolean status, String expected) throws Exception {
        var request = get(ENDPOINT).with(TestJwt.as(admin));
        if (date) request.param("date", DATE.toString());
        if (filterProfessional) request.param("professionalId", professional.getId().toString());
        if (status) request.param("status", "CONFIRMED");
        String[] names = expected.split(" ");
        var result = mvc.perform(request).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(names.length));
        for (int i = 0; i < names.length; i++) {
            result.andExpect(jsonPath("$.content[" + i + "].id").value(ids.get(names[i])));
        }
    }

    @Test
    void shouldFilterCancelledAppointmentsWithoutDateOrProfessional() throws Exception {
        mvc.perform(get(ENDPOINT).with(TestJwt.as(admin))
                        .param("status", "CANCELLED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(ids.get("cancelled")));
    }

    @Test
    void shouldReturnEmptyForDateWithoutAppointmentsOrProfessionalFromAnotherEstablishment() throws Exception {
        mvc.perform(get(ENDPOINT).with(TestJwt.as(admin))
                        .param("date", DATE.plusDays(2).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get(ENDPOINT).with(TestJwt.as(admin))
                        .param("professionalId", otherProfessional.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void shouldShowEffectiveWorkingHoursAndRedactedPersonalBlocksToAdministrator() throws Exception {
        establishmentHours.saveAndFlush(EstablishmentBusinessHour.builder()
                .establishment(admin.getEstablishment()).dayOfWeek(DATE.getDayOfWeek())
                .closed(false).openingTime(java.time.LocalTime.of(8, 0))
                .closingTime(java.time.LocalTime.of(18, 0)).build());
        calendarEvents.saveAndFlush(ProfessionalCalendarEvent.create(
                admin.getEstablishment(), professional, "Consulta médica", "Informação privada",
                DATE.atTime(11, 0), DATE.atTime(12, 0), false));

        mvc.perform(get(ENDPOINT + "/disponibilidade-equipe")
                        .with(TestJwt.as(admin))
                        .param("date", DATE.toString())
                        .param("professionalId", professional.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].professionalId").value(professional.getId()))
                .andExpect(jsonPath("$[0].working").value(true))
                .andExpect(jsonPath("$[0].openingTime").value("08:00:00"))
                .andExpect(jsonPath("$[0].closingTime").value("18:00:00"))
                .andExpect(jsonPath("$[0].blocks.length()").value(1))
                .andExpect(jsonPath("$[0].blocks[0].startsAt").value("2026-09-16T11:00:00"))
                .andExpect(jsonPath("$[0].blocks[0].title").doesNotExist())
                .andExpect(jsonPath("$[0].blocks[0].description").doesNotExist());
    }

    @Test
    void shouldKeepEachProfessionalsWorkingHoursAndBlocksWhenLoadingTeamAvailability() throws Exception {
        establishmentHours.saveAndFlush(EstablishmentBusinessHour.builder()
                .establishment(admin.getEstablishment()).dayOfWeek(DATE.getDayOfWeek())
                .closed(false).openingTime(java.time.LocalTime.of(8, 0))
                .closingTime(java.time.LocalTime.of(18, 0)).build());
        professionalHours.saveAllAndFlush(java.util.List.of(
                ProfessionalBusinessHour.create(professional, DATE.getDayOfWeek(), false,
                        java.time.LocalTime.of(9, 0), java.time.LocalTime.of(16, 0)),
                ProfessionalBusinessHour.create(secondProfessional, DATE.getDayOfWeek(), true, null, null)));
        calendarEvents.saveAndFlush(ProfessionalCalendarEvent.create(
                admin.getEstablishment(), professional, "Bloqueio", "Privado",
                DATE.atTime(10, 0), DATE.atTime(11, 0), false));
        calendarEvents.saveAndFlush(ProfessionalCalendarEvent.create(
                admin.getEstablishment(), secondProfessional, "Bloqueio", "Privado",
                DATE.atTime(13, 0), DATE.atTime(14, 0), false));

        String body = mvc.perform(get(ENDPOINT + "/disponibilidade-equipe")
                        .with(TestJwt.as(admin))
                        .param("date", DATE.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Map<Long, com.fasterxml.jackson.databind.JsonNode> availabilityByProfessional = new LinkedHashMap<>();
        for (var availability : objectMapper.readTree(body)) {
            availabilityByProfessional.put(availability.get("professionalId").asLong(), availability);
        }
        assertThat(availabilityByProfessional).hasSize(2);
        var firstProfessional = availabilityByProfessional.get(professional.getId());
        assertThat(firstProfessional.get("working").asBoolean()).isTrue();
        assertThat(firstProfessional.get("openingTime").asText()).isEqualTo("09:00:00");
        assertThat(firstProfessional.get("closingTime").asText()).isEqualTo("16:00:00");
        assertThat(firstProfessional.get("blocks")).hasSize(1);
        var closedProfessional = availabilityByProfessional.get(secondProfessional.getId());
        assertThat(closedProfessional.get("working").asBoolean()).isFalse();
        assertThat(closedProfessional.get("blocks")).hasSize(1);
    }

    @Test
    void shouldRejectForeignProfessionalInTeamAvailability() throws Exception {
        mvc.perform(get(ENDPOINT + "/disponibilidade-equipe")
                        .with(TestJwt.as(admin))
                        .param("date", DATE.toString())
                        .param("professionalId", otherProfessional.getId().toString()))
                .andExpect(status().isNotFound());
    }

    private User user(String name, UserRole role, Establishment establishment) {
        return users.saveAndFlush(User.builder().name(name).email(name + "@agenda.test")
                .password("test").role(role).establishment(establishment).build());
    }

    private EstablishmentService service(Establishment establishment) {
        return services.saveAndFlush(EstablishmentService.builder().establishment(establishment)
                .name("Corte").price(new BigDecimal("40.00")).commissionPercentage(BigDecimal.TEN)
                .durationMinutes(30).build());
    }

    private void add(String name, User client, User professional, EstablishmentService service,
                     LocalDateTime start, boolean cancelled) {
        Appointment appointment = schedule(service.getEstablishment(), client, professional, service, start);
        if (cancelled) appointment.cancel();
        ids.put(name, appointments.saveAndFlush(appointment).getId());
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
