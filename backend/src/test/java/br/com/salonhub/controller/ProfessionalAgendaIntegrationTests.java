package br.com.salonhub.controller;

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
import java.time.LocalTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProfessionalAgendaIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldManageProfessionalScheduleEventsAndUnifiedAgenda() throws Exception {
        TestContext context = createContext();
        LocalDate date = LocalDate.now().plusDays(2);
        LocalDateTime eventStart = date.atTime(10, 0);
        LocalDateTime eventEnd = date.atTime(12, 0);

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
        LocalDate date = LocalDate.now().plusDays(3);

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

    private TestContext createContext() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        MvcResult registration = mockMvc.perform(post("/api/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ownerName":"Admin Agenda","ownerEmail":"admin.%s@agenda.test","password":"senha123","establishmentName":"Salão Agenda","cnpj":"66.777.888/0001-81"}
                                """.formatted(suffix)))
                .andExpect(status().isCreated()).andReturn();
        Number establishmentId = JsonPath.read(registration.getResponse().getContentAsString(), "$.establishmentId");
        String adminToken = login("admin." + suffix + "@agenda.test");

        MvcResult professional = mockMvc.perform(post("/api/funcionarios")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Profissional Agenda","email":"professional.%s@agenda.test"}
                                """.formatted(suffix)))
                .andExpect(status().isCreated()).andReturn();
        Number professionalId = JsonPath.read(professional.getResponse().getContentAsString(), "$.id");
        String firstAccessToken = JsonPath.read(professional.getResponse().getContentAsString(), "$.firstAccessToken");
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
                                {"name":"Cliente Agenda","email":"%s","password":"senha123"}
                                """.formatted(clientEmail)))
                .andExpect(status().isCreated());
        return new TestContext(establishmentId, professionalId, serviceId,
                professionalToken, login(clientEmail));
    }

    private String weeklySchedule() {
        return """
                {"businessHours":[
                  {"dayOfWeek":"MONDAY","closed":false,"openingTime":"09:00","closingTime":"17:00"},
                  {"dayOfWeek":"TUESDAY","closed":false,"openingTime":"09:00","closingTime":"17:00"},
                  {"dayOfWeek":"WEDNESDAY","closed":false,"openingTime":"09:00","closingTime":"17:00"},
                  {"dayOfWeek":"THURSDAY","closed":false,"openingTime":"09:00","closingTime":"17:00"},
                  {"dayOfWeek":"FRIDAY","closed":false,"openingTime":"09:00","closingTime":"17:00"},
                  {"dayOfWeek":"SATURDAY","closed":false,"openingTime":"09:00","closingTime":"17:00"},
                  {"dayOfWeek":"SUNDAY","closed":false,"openingTime":"09:00","closingTime":"17:00"}
                ]}
                """;
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
                               String professionalToken, String clientToken) { }
}
