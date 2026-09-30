package br.com.salonhub.controller;

import br.com.salonhub.support.TestDocuments;

import br.com.salonhub.entity.Appointment;
import br.com.salonhub.entity.Customer;
import br.com.salonhub.repository.CustomerRepository;
import br.com.salonhub.entity.Establishment;
import br.com.salonhub.entity.EstablishmentService;
import br.com.salonhub.entity.User;
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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProfessionalAgendaIntegrationTests extends FirstAccessIntegrationSupport {

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
    private EstablishmentServiceRepository establishmentServiceRepository;

    @Test
    void administratorManagesOwnProfessionalAgendaButCannotCrossTenant() throws Exception {
        TestContext own = createContext();
        TestContext foreign = createContext("11.444.777/0001-61");
        LocalDate day = LocalDate.now(clock).plusDays(2);
        String path = "/api/funcionarios/{id}/agenda";

        mockMvc.perform(get(path + "/horarios", own.professionalId())
                .header("Authorization", bearer(own.administratorToken())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(7));
        mockMvc.perform(put(path + "/horarios", own.professionalId())
                .header("Authorization", bearer(own.administratorToken()))
                .contentType(MediaType.APPLICATION_JSON).content(weeklySchedule()))
                .andExpect(status().isOk());
        MvcResult created = mockMvc.perform(post(path + "/eventos", own.professionalId())
                .header("Authorization", bearer(own.administratorToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventPayload("Bloqueio admin", day.atTime(10, 0), day.atTime(11, 0), false)))
                .andExpect(status().isCreated()).andReturn();
        Number eventId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        mockMvc.perform(get(path, own.professionalId())
                .header("Authorization", bearer(own.administratorToken()))
                .param("startsAt", day.atStartOfDay().toString())
                .param("endsAt", day.plusDays(1).atStartOfDay().toString()))
                .andExpect(jsonPath("$[0].title").value("Bloqueio admin"));
        mockMvc.perform(put(path + "/eventos/{eventId}", own.professionalId(), eventId.longValue())
                .header("Authorization", bearer(own.administratorToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventPayload("Editado admin", day.atTime(10, 0), day.atTime(11, 0), false)))
                .andExpect(status().isOk());
        mockMvc.perform(delete(path + "/eventos/{eventId}", own.professionalId(), eventId.longValue())
                .header("Authorization", bearer(own.administratorToken())))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(path + "/horarios", foreign.professionalId())
                .header("Authorization", bearer(own.administratorToken())))
                .andExpect(status().isNotFound());
        mockMvc.perform(put(path + "/horarios", foreign.professionalId())
                .header("Authorization", bearer(own.administratorToken()))
                .contentType(MediaType.APPLICATION_JSON).content(weeklySchedule()))
                .andExpect(status().isNotFound());
        mockMvc.perform(put(path + "/horarios", foreign.professionalId())
                .header("Authorization", bearer(own.professionalToken()))
                .contentType(MediaType.APPLICATION_JSON).content(weeklySchedule()))
                .andExpect(status().isForbidden());
    }

    @Test
    void establishmentBlockHidesAvailabilityAndRejectsDirectBooking() throws Exception {
        TestContext context = createContext();
        LocalDate day = LocalDate.now(clock).plusDays(2);
        String path = "/api/estabelecimentos/me/bloqueios";
        String blockPayload = "{\"startsAt\":\"%s\",\"endsAt\":\"%s\",\"reason\":\"Manutenção\"}"
                .formatted(day.atTime(10, 0), day.atTime(12, 0));
        MvcResult created = mockMvc.perform(post(path).header("Authorization", bearer(context.administratorToken()))
                .contentType(MediaType.APPLICATION_JSON).content(blockPayload))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.reason").value("Manutenção")).andReturn();
        Number blockId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        mockMvc.perform(get(path).header("Authorization", bearer(context.administratorToken()))
                .param("startsAt", day.atStartOfDay().toString())
                .param("endsAt", day.plusDays(1).atStartOfDay().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(blockId));
        mockMvc.perform(get("/api/estabelecimentos/{id}/agendamentos/disponibilidade", context.establishmentId())
                .header("Authorization", bearer(context.clientToken()))
                .param("serviceId", context.serviceId().toString())
                .param("professionalId", context.professionalId().toString()).param("date", day.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.availableTimes[?(@ == '10:00:00')]").isEmpty());
        mockMvc.perform(post("/api/estabelecimentos/{id}/agendamentos", context.establishmentId())
                .header("Authorization", bearer(context.clientToken())).contentType(MediaType.APPLICATION_JSON)
                .content(appointmentPayload(context, day.atTime(10, 0))))
                .andExpect(status().isConflict());
        mockMvc.perform(put(path + "/{id}", blockId.longValue())
                .header("Authorization", bearer(context.professionalToken()))
                .contentType(MediaType.APPLICATION_JSON).content(blockPayload))
                .andExpect(status().isForbidden());
        TestContext foreign = createContext("11.444.777/0001-61");
        mockMvc.perform(delete(path + "/{id}", blockId.longValue())
                .header("Authorization", bearer(foreign.administratorToken())))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(path + "/{id}", blockId.longValue())
                .header("Authorization", bearer(context.administratorToken())))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldManageProfessionalScheduleEventsAndUnifiedAgenda() throws Exception {
        TestContext context = createContext();
        LocalDate date = LocalDate.now(clock).plusDays(2);
        LocalDateTime eventStart = date.atTime(10, 0);
        LocalDateTime eventEnd = date.atTime(12, 0);

        mockMvc.perform(get("/api/profissionais/me/agenda/configuracao")
                        .header("Authorization", bearer(context.professionalToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeZone").value("America/Sao_Paulo"));

        mockMvc.perform(get("/api/profissionais/me/agenda/horarios")
                        .header("Authorization", bearer(context.professionalToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[0].inheritedFromEstablishment").value(true));

        mockMvc.perform(put("/api/profissionais/me/agenda/horarios")
                        .header("Authorization", bearer(context.professionalToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(weeklySchedule()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[0].inheritedFromEstablishment").value(false));

        MvcResult createdEvent = mockMvc.perform(post("/api/profissionais/me/agenda/eventos")
                        .header("Authorization", bearer(context.professionalToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventPayload("Compromisso pessoal", eventStart, eventEnd, false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Compromisso pessoal"))
                .andExpect(jsonPath("$.allDay").value(false))
                .andReturn();
        Number eventId = JsonPath.read(createdEvent.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/profissionais/me/agenda/eventos")
                        .header("Authorization", bearer(context.professionalToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventPayload("Conflito", date.atTime(11, 0), date.atTime(13, 0), false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Já existe um evento pessoal nesse período."));

        mockMvc.perform(get("/api/estabelecimentos/{id}/agendamentos/disponibilidade", context.establishmentId())
                        .header("Authorization", bearer(context.clientToken()))
                        .param("serviceId", context.serviceId().toString())
                        .param("professionalId", context.professionalId().toString())
                        .param("date", date.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableTimes[?(@ == '10:00:00')]").isEmpty())
                .andExpect(jsonPath("$.availableTimes[?(@ == '12:00:00')]").isNotEmpty());

        mockMvc.perform(post("/api/estabelecimentos/{id}/agendamentos", context.establishmentId())
                        .header("Authorization", bearer(context.clientToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentPayload(context, date.atTime(10, 30))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("O profissional possui um bloqueio pessoal nesse período."));

        mockMvc.perform(post("/api/estabelecimentos/{id}/agendamentos", context.establishmentId())
                        .header("Authorization", bearer(context.clientToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentPayload(context, date.atTime(14, 0))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/profissionais/me/agenda")
                        .header("Authorization", bearer(context.professionalToken()))
                        .param("startsAt", date.atStartOfDay().toString())
                        .param("endsAt", date.plusDays(1).atStartOfDay().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("EVENT"))
                .andExpect(jsonPath("$[1].type").value("APPOINTMENT"))
                .andExpect(jsonPath("$[1].clientName").value("Cliente Agenda"));

        mockMvc.perform(put("/api/profissionais/me/agenda/eventos/{id}", eventId.longValue())
                        .header("Authorization", bearer(context.professionalToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventPayload("Evento atualizado", eventStart, date.atTime(11, 0), false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Evento atualizado"));

        mockMvc.perform(delete("/api/profissionais/me/agenda/eventos/{id}", eventId.longValue())
                        .header("Authorization", bearer(context.professionalToken())))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldValidateAllDayEventsAndForbidOtherRoles() throws Exception {
        TestContext context = createContext();
        LocalDate date = LocalDate.now(clock).plusDays(3);

        mockMvc.perform(post("/api/profissionais/me/agenda/eventos")
                        .header("Authorization", bearer(context.professionalToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventPayload("Folga", date.atStartOfDay(), date.plusDays(1).atStartOfDay(), true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.allDay").value(true));

        mockMvc.perform(post("/api/profissionais/me/agenda/eventos")
                        .header("Authorization", bearer(context.professionalToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventPayload("Inválido", date.plusDays(2).atTime(9, 0), date.plusDays(3).atStartOfDay(), true)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/profissionais/me/agenda/horarios")
                        .header("Authorization", bearer(context.clientToken())))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectCreatingCalendarEventThatAlreadyEnded() throws Exception {
        TestContext context = createContext();
        LocalDateTime end = LocalDateTime.now(clock).minusMinutes(5);

        mockMvc.perform(post("/api/profissionais/me/agenda/eventos")
                        .header("Authorization", bearer(context.professionalToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventPayload("Compromisso passado", end.minusHours(1), end, false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Não é possível criar um compromisso que já terminou."));
    }

    @Test
    void shouldNeverOfferAvailabilitySlotsThatCreationWouldReject() throws Exception {
        TestContext context = createContext();
        // Expediente do salão abrindo em um minuto que não é múltiplo de 30 (caso relatado:
        // a disponibilidade oferecia 09:15 e a criação do agendamento rejeitava por não estar
        // alinhado aos slots de 30 minutos).
        mockMvc.perform(put("/api/estabelecimentos/me")
                        .header("Authorization", bearer(context.administratorToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(establishmentWeeklyHours("09:15", "18:00")))
                .andExpect(status().isOk());

        LocalDate date = LocalDate.now(clock).plusDays(2);
        mockMvc.perform(get("/api/estabelecimentos/{id}/agendamentos/disponibilidade", context.establishmentId())
                        .header("Authorization", bearer(context.clientToken()))
                        .param("serviceId", context.serviceId().toString())
                        .param("professionalId", context.professionalId().toString())
                        .param("date", date.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableTimes[?(@ == '09:15:00')]").isEmpty())
                .andExpect(jsonPath("$.availableTimes[?(@ == '09:30:00')]").isNotEmpty());

        // Todo horário oferecido pela disponibilidade precisa ser aceito pela criação.
        mockMvc.perform(post("/api/estabelecimentos/{id}/agendamentos", context.establishmentId())
                        .header("Authorization", bearer(context.clientToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentPayload(context, date.atTime(9, 30))))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldReflectEstablishmentHourChangesEvenAfterProfessionalCustomizedSchedule() throws Exception {
        TestContext context = createContext();
        // Profissional personaliza a jornada (09:00-17:00), dentro do expediente padrão do
        // salão (09:00-18:00).
        mockMvc.perform(put("/api/profissionais/me/agenda/horarios")
                        .header("Authorization", bearer(context.professionalToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(weeklySchedule()))
                .andExpect(status().isOk());

        LocalDate date = LocalDate.now(clock).plusDays(2);
        mockMvc.perform(get("/api/estabelecimentos/{id}/agendamentos/disponibilidade", context.establishmentId())
                        .header("Authorization", bearer(context.clientToken()))
                        .param("serviceId", context.serviceId().toString())
                        .param("professionalId", context.professionalId().toString())
                        .param("date", date.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableTimes[?(@ == '09:00:00')]").isNotEmpty());

        // O salão reduz o expediente para 10:00-17:00 depois que o profissional já tinha
        // personalizado a própria jornada.
        mockMvc.perform(put("/api/estabelecimentos/me")
                        .header("Authorization", bearer(context.administratorToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(establishmentWeeklyHours("10:00", "17:00")))
                .andExpect(status().isOk());

        // A disponibilidade não pode mais oferecer 09:00 (a interseção agora começa às 10:00).
        mockMvc.perform(get("/api/estabelecimentos/{id}/agendamentos/disponibilidade", context.establishmentId())
                        .header("Authorization", bearer(context.clientToken()))
                        .param("serviceId", context.serviceId().toString())
                        .param("professionalId", context.professionalId().toString())
                        .param("date", date.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableTimes[?(@ == '09:00:00')]").isEmpty())
                .andExpect(jsonPath("$.availableTimes[?(@ == '10:00:00')]").isNotEmpty());

        // E a criação fica consistente com a disponibilidade: 09:00 passa a ser rejeitado.
        mockMvc.perform(post("/api/estabelecimentos/{id}/agendamentos", context.establishmentId())
                        .header("Authorization", bearer(context.clientToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentPayload(context, date.atTime(9, 0))))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/estabelecimentos/{id}/agendamentos", context.establishmentId())
                        .header("Authorization", bearer(context.clientToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentPayload(context, date.atTime(10, 0))))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldRestoreEstablishmentHoursWhenSwitchingBackToInherit() throws Exception {
        TestContext context = createContext();

        mockMvc.perform(put("/api/profissionais/me/agenda/horarios")
                        .header("Authorization", bearer(context.professionalToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(weeklySchedule()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].inheritedFromEstablishment").value(false));

        // Voltar para "usar horário do salão" remove o override salvo.
        mockMvc.perform(put("/api/profissionais/me/agenda/horarios")
                        .header("Authorization", bearer(context.professionalToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleAllInherited()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].inheritedFromEstablishment").value(true))
                .andExpect(jsonPath("$[0].openingTime").value("09:00:00"))
                .andExpect(jsonPath("$[0].closingTime").value("18:00:00"));

        // Sem override, uma mudança futura do salão passa a refletir automaticamente.
        mockMvc.perform(put("/api/estabelecimentos/me")
                        .header("Authorization", bearer(context.administratorToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(establishmentWeeklyHours("10:00", "16:00")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/profissionais/me/agenda/horarios")
                        .header("Authorization", bearer(context.professionalToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].inheritedFromEstablishment").value(true))
                .andExpect(jsonPath("$[0].openingTime").value("10:00:00"))
                .andExpect(jsonPath("$[0].closingTime").value("16:00:00"));
    }

    @Test
    void shouldBlockScheduleChangeThatWouldOrphanFutureConfirmedAppointment() throws Exception {
        TestContext context = createContext();
        LocalDate date = LocalDate.now(clock).plusDays(2);
        DayOfWeek day = date.getDayOfWeek();

        mockMvc.perform(post("/api/estabelecimentos/{id}/agendamentos", context.establishmentId())
                        .header("Authorization", bearer(context.clientToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentPayload(context, date.atTime(10, 0))))
                .andExpect(status().isCreated());

        // Fechar o dia do agendamento confirmado é rejeitado.
        MvcResult closedAttempt = mockMvc.perform(put("/api/profissionais/me/agenda/horarios")
                        .header("Authorization", bearer(context.professionalToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleWithCustomDay(day, null, null, true)))
                .andExpect(status().isConflict())
                .andReturn();
        assertThat(closedAttempt.getResponse().getContentAsString()).contains("agendamento(s) confirmado(s)");

        // Reduzir a jornada de forma que o horário do agendamento (10:00-11:00) fique fora
        // também é rejeitado.
        mockMvc.perform(put("/api/profissionais/me/agenda/horarios")
                        .header("Authorization", bearer(context.professionalToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleWithCustomDay(day, "12:00", "18:00", false)))
                .andExpect(status().isConflict());

        // Uma jornada que ainda comporta o horário do agendamento é aceita normalmente
        // (09:00-12:00 está dentro do expediente do salão e cobre o atendimento das 10h).
        mockMvc.perform(put("/api/profissionais/me/agenda/horarios")
                        .header("Authorization", bearer(context.professionalToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleWithCustomDay(day, "09:00", "12:00", false)))
                .andExpect(status().isOk());
    }

    @Test
    void shouldBlockScheduleChangeThatWouldOrphanAppointmentInProgress() throws Exception {
        TestContext context = createContext();

        // Agendamento já em andamento: começou há 10 minutos e só termina em 50 (serviço de
        // 60 min de duração). Inserido direto no repositório porque a API não permite criar
        // um agendamento com início no passado — mas é exatamente esse o caso que a checagem
        // baseada em "início no futuro" deixava passar antes da correção.
        Establishment establishment = establishmentRepository.findById(context.establishmentId().longValue())
                .orElseThrow();
        User professional = userRepository.findById(context.professionalId().longValue()).orElseThrow();
        EstablishmentService service = establishmentServiceRepository
                .findById(context.serviceId().longValue()).orElseThrow();
        Long clientId = fetchUserId(context.clientToken());
        User client = userRepository.findById(clientId).orElseThrow();

        LocalDateTime startsAt = LocalDateTime.now(clock).minusMinutes(10);
        DayOfWeek day = startsAt.getDayOfWeek();
        Appointment inProgress = schedule(establishment, client, professional, service, startsAt);
        appointmentRepository.saveAndFlush(inProgress);

        // Fechar o dia do agendamento em andamento é rejeitado, mesmo o início já estando no
        // passado — é justamente o caso que "início no futuro" não cobria.
        MvcResult blocked = mockMvc.perform(put("/api/profissionais/me/agenda/horarios")
                        .header("Authorization", bearer(context.professionalToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleWithCustomDay(day, null, null, true)))
                .andExpect(status().isConflict())
                .andReturn();
        assertThat(blocked.getResponse().getContentAsString()).contains("em andamento");

        // Depois de concluído, o agendamento deixa de ser CONFIRMED e para de bloquear a
        // mudança de jornada.
        mockMvc.perform(patch("/api/agendamentos/{id}/conclusao", inProgress.getId())
                        .header("Authorization", bearer(context.professionalToken())))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/profissionais/me/agenda/horarios")
                        .header("Authorization", bearer(context.professionalToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleWithCustomDay(day, null, null, true)))
                .andExpect(status().isOk());
    }

    private Long fetchUserId(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private TestContext createContext() throws Exception {
        return createContext("66.777.888/0001-81");
    }

    private TestContext createContext(String cnpj) throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        MvcResult registration = mockMvc.perform(post("/api/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ownerName":"Admin Agenda","ownerEmail":"admin.%s@agenda.test","password":"senha123","establishmentName":"Salão Agenda","cnpj":"%s"}
                                """.formatted(suffix, cnpj)))
                .andExpect(status().isCreated()).andReturn();
        Number establishmentId = JsonPath.read(registration.getResponse().getContentAsString(), "$.establishmentId");
        String adminToken = login("admin." + suffix + "@agenda.test");

        MvcResult professional = mockMvc.perform(post("/api/funcionarios")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Profissional Agenda","email":"professional.%s@agenda.test","occupation":"Cabeleireiro(a)","cpf":"%s","hireDate":"2025-01-02"}
                                """.formatted(suffix, TestDocuments.cpf())))
                .andExpect(status().isCreated()).andReturn();
        Number professionalId = JsonPath.read(professional.getResponse().getContentAsString(), "$.id");
        String firstAccessToken = firstAccessToken(professional.getResponse().getContentAsString());
        completeFirstAccess(firstAccessToken);
        String professionalToken = login("professional." + suffix + "@agenda.test");

        MvcResult service = mockMvc.perform(post("/api/servicos")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Serviço Agenda","price":50.00,"durationMinutes":60}
                                """))
                .andExpect(status().isCreated()).andReturn();
        Number serviceId = JsonPath.read(service.getResponse().getContentAsString(), "$.id");

        String clientEmail = "client." + suffix + "@agenda.test";
        mockMvc.perform(post("/api/auth/clientes").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Cliente Agenda","email":"%s","phone":"44999990000","password":"senha123","passwordConfirmation":"senha123"}
                                """.formatted(clientEmail)))
                .andExpect(status().isCreated());
        return new TestContext(establishmentId, professionalId, serviceId,
                professionalToken, login(clientEmail), adminToken);
    }

    private String weeklySchedule() {
        return """
                {"businessHours":[
                  {"dayOfWeek":"MONDAY","useEstablishmentHours":false,"closed":false,"openingTime":"09:00","closingTime":"17:00"},
                  {"dayOfWeek":"TUESDAY","useEstablishmentHours":false,"closed":false,"openingTime":"09:00","closingTime":"17:00"},
                  {"dayOfWeek":"WEDNESDAY","useEstablishmentHours":false,"closed":false,"openingTime":"09:00","closingTime":"17:00"},
                  {"dayOfWeek":"THURSDAY","useEstablishmentHours":false,"closed":false,"openingTime":"09:00","closingTime":"17:00"},
                  {"dayOfWeek":"FRIDAY","useEstablishmentHours":false,"closed":false,"openingTime":"09:00","closingTime":"17:00"},
                  {"dayOfWeek":"SATURDAY","useEstablishmentHours":false,"closed":false,"openingTime":"09:00","closingTime":"17:00"},
                  {"dayOfWeek":"SUNDAY","useEstablishmentHours":false,"closed":false,"openingTime":"09:00","closingTime":"17:00"}
                ]}
                """;
    }

    private String establishmentWeeklyHours(String openingTime, String closingTime) {
        StringBuilder builder = new StringBuilder("{\"name\":\"Salão Agenda\",\"businessHours\":[");
        DayOfWeek[] days = DayOfWeek.values();
        for (int i = 0; i < days.length; i++) {
            builder.append("{\"dayOfWeek\":\"").append(days[i])
                    .append("\",\"closed\":false,\"openingTime\":\"").append(openingTime)
                    .append("\",\"closingTime\":\"").append(closingTime).append("\"}");
            if (i < days.length - 1) builder.append(",");
        }
        builder.append("]}");
        return builder.toString();
    }

    private String scheduleAllInherited() {
        StringBuilder builder = new StringBuilder("{\"businessHours\":[");
        DayOfWeek[] days = DayOfWeek.values();
        for (int i = 0; i < days.length; i++) {
            builder.append("{\"dayOfWeek\":\"").append(days[i])
                    .append("\",\"useEstablishmentHours\":true,\"closed\":false,\"openingTime\":null,\"closingTime\":null}");
            if (i < days.length - 1) builder.append(",");
        }
        builder.append("]}");
        return builder.toString();
    }

    private String scheduleWithCustomDay(DayOfWeek customDay, String openingTime, String closingTime, boolean closed) {
        StringBuilder builder = new StringBuilder("{\"businessHours\":[");
        DayOfWeek[] days = DayOfWeek.values();
        for (int i = 0; i < days.length; i++) {
            DayOfWeek day = days[i];
            builder.append("{\"dayOfWeek\":\"").append(day).append("\"");
            if (day == customDay) {
                builder.append(",\"useEstablishmentHours\":false,\"closed\":").append(closed);
                if (closed) {
                    builder.append(",\"openingTime\":null,\"closingTime\":null");
                } else {
                    builder.append(",\"openingTime\":\"").append(openingTime)
                            .append("\",\"closingTime\":\"").append(closingTime).append("\"");
                }
            } else {
                builder.append(",\"useEstablishmentHours\":true,\"closed\":false,\"openingTime\":null,\"closingTime\":null");
            }
            builder.append("}");
            if (i < days.length - 1) builder.append(",");
        }
        builder.append("]}");
        return builder.toString();
    }

    private String eventPayload(String title, LocalDateTime startsAt, LocalDateTime endsAt, boolean allDay) {
        return """
                {"title":"%s","description":"Detalhes","startsAt":"%s","endsAt":"%s","allDay":%s}
                """.formatted(title, startsAt, endsAt, allDay);
    }

    private String appointmentPayload(TestContext context, LocalDateTime startsAt) {
        return """
                {"serviceId":%d,"professionalId":%d,"startsAt":"%s"}
                """.formatted(context.serviceId().longValue(), context.professionalId().longValue(), startsAt);
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"senha123"}
                                """.formatted(email)))
                .andExpect(status().isOk()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    private void completeFirstAccess(String token) throws Exception {
        mockMvc.perform(post("/api/auth/primeiro-acesso").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","password":"senha123"}
                                """.formatted(token)))
                .andExpect(status().isNoContent());
    }

    private String bearer(String token) { return "Bearer " + token; }

    private record TestContext(Number establishmentId, Number professionalId, Number serviceId,
                               String professionalToken, String clientToken, String administratorToken) { }

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
