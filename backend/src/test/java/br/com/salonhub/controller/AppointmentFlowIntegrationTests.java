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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AppointmentFlowIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldCompleteClientAppointmentFlowAndRejectUnavailableTimes() throws Exception {
        MvcResult registration = mockMvc.perform(post("/api/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ownerName": "Administrador MVP",
                                  "ownerEmail": "admin.appointment@salonhub.test",
                                  "password": "senha123",
                                  "establishmentName": "Salão MVP",
                                  "cnpj": "66777888000199"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        Number establishmentId = JsonPath.read(
                registration.getResponse().getContentAsString(), "$.establishmentId");
        String administratorToken = login("admin.appointment@salonhub.test");

        MvcResult professional = mockMvc.perform(post("/api/funcionarios")
                        .header("Authorization", bearer(administratorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Profissional MVP",
                                  "email": "professional.appointment@salonhub.test",
                                  "password": "senha123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        Number professionalId = JsonPath.read(professional.getResponse().getContentAsString(), "$.id");

        MvcResult service = mockMvc.perform(post("/api/servicos")
                        .header("Authorization", bearer(administratorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Corte MVP",
                                  "price": 50.00,
                                  "durationMinutes": 60
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.commissionPercentage").value(0))
                .andExpect(jsonPath("$.durationMinutes").value(60))
                .andReturn();
        Number serviceId = JsonPath.read(service.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/auth/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Cliente MVP",
                                  "email": "client.appointment@salonhub.test",
                                  "password": "senha123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CLIENT"))
                .andExpect(jsonPath("$.establishmentId").doesNotExist());

        String clientToken = login("client.appointment@salonhub.test");

        mockMvc.perform(get("/api/estabelecimentos/{id}/catalogo/servicos", establishmentId.longValue())
                        .header("Authorization", bearer(clientToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Corte MVP"));

        mockMvc.perform(get("/api/estabelecimentos/{id}/catalogo/funcionarios", establishmentId.longValue())
                        .header("Authorization", bearer(clientToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Profissional MVP"));

        LocalDate appointmentDate = LocalDate.now().plusDays(2);
        LocalDateTime startsAt = appointmentDate.atTime(10, 0);

        mockMvc.perform(get("/api/estabelecimentos/{id}/agendamentos/disponibilidade", establishmentId.longValue())
                        .header("Authorization", bearer(clientToken))
                        .param("serviceId", serviceId.toString())
                        .param("professionalId", professionalId.toString())
                        .param("date", appointmentDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durationMinutes").value(60))
                .andExpect(jsonPath("$.availableTimes").isArray())
                .andExpect(jsonPath("$.availableTimes").isNotEmpty());

        mockMvc.perform(post("/api/estabelecimentos/{id}/agendamentos", establishmentId.longValue())
                        .header("Authorization", bearer(clientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentPayload(serviceId, professionalId, startsAt)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serviceName").value("Corte MVP"))
                .andExpect(jsonPath("$.professionalName").value("Profissional MVP"))
                .andExpect(jsonPath("$.endsAt").value(appointmentDate + "T11:00:00"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(post("/api/estabelecimentos/{id}/agendamentos", establishmentId.longValue())
                        .header("Authorization", bearer(clientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentPayload(serviceId, professionalId, appointmentDate.atTime(10, 30))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        mockMvc.perform(post("/api/estabelecimentos/{id}/agendamentos", establishmentId.longValue())
                        .header("Authorization", bearer(clientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentPayload(serviceId, professionalId, appointmentDate.atTime(17, 30))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        mockMvc.perform(get("/api/agendamentos/me")
                        .header("Authorization", bearer(clientToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].serviceName").value("Corte MVP"));
    }

    @Test
    void shouldForbidAdministratorFromCreatingClientAppointment() throws Exception {
        MvcResult registration = mockMvc.perform(post("/api/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ownerName": "Administrador Sem Acesso",
                                  "ownerEmail": "admin.forbidden.appointment@salonhub.test",
                                  "password": "senha123",
                                  "establishmentName": "Salão Restrito",
                                  "cnpj": "99888777000166"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        Number establishmentId = JsonPath.read(
                registration.getResponse().getContentAsString(), "$.establishmentId");
        String administratorToken = login("admin.forbidden.appointment@salonhub.test");

        mockMvc.perform(post("/api/estabelecimentos/{id}/agendamentos", establishmentId.longValue())
                        .header("Authorization", bearer(administratorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceId": 1,
                                  "professionalId": 1,
                                  "startsAt": "2099-01-01T10:00:00"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "senha123"
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    private String appointmentPayload(Number serviceId, Number professionalId, LocalDateTime startsAt) {
        return """
                {
                  "serviceId": %d,
                  "professionalId": %d,
                  "startsAt": "%s"
                }
                """.formatted(serviceId.longValue(), professionalId.longValue(), startsAt);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
