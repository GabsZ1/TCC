package br.com.salonhub.controller;

import br.com.salonhub.entity.Appointment;
import br.com.salonhub.entity.AppointmentStatus;
import br.com.salonhub.repository.AppointmentRepository;


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

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AppointmentFlowIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppointmentRepository appointmentRepository;

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
                                  "cnpj": "66777888000181"
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
                                  "email": "professional.appointment@salonhub.test"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        Number professionalId = JsonPath.read(professional.getResponse().getContentAsString(), "$.id");

        String firstAccessToken = JsonPath.read(
                professional.getResponse().getContentAsString(), "$.firstAccessToken");
        completeFirstAccess(firstAccessToken, "senha123");
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

        MvcResult appointment = mockMvc.perform(post("/api/estabelecimentos/{id}/agendamentos", establishmentId.longValue())
                        .header("Authorization", bearer(clientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentPayload(serviceId, professionalId, startsAt)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serviceName").value("Corte MVP"))
                .andExpect(jsonPath("$.professionalName").value("Profissional MVP"))
                .andExpect(jsonPath("$.endsAt").value(appointmentDate + "T11:00:00"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andReturn();
        Number appointmentId = JsonPath.read(appointment.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/estabelecimentos/{id}/agendamentos", establishmentId.longValue())
                        .header("Authorization", bearer(clientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentPayload(serviceId, professionalId, appointmentDate.atTime(10, 17))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("O horário deve estar alinhado aos intervalos de 30 minutos."));

        mockMvc.perform(get("/api/agendamentos/estabelecimento/me")
                        .header("Authorization", bearer(administratorToken))
                        .param("date", appointmentDate.toString())
                        .param("professionalId", professionalId.toString())
                        .param("status", "CONFIRMED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].clientName").value("Cliente MVP"));

        mockMvc.perform(get("/api/agendamentos/estabelecimento/me")
                        .header("Authorization", bearer(clientToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/agendamentos/{id}", appointmentId.longValue())
                        .header("Authorization", bearer(clientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentPayload(serviceId, professionalId, startsAt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(appointmentId))
                .andExpect(jsonPath("$.endsAt").value(appointmentDate + "T11:00:00"));

        mockMvc.perform(put("/api/agendamentos/{id}", appointmentId.longValue())
                        .header("Authorization", bearer(clientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentPayload(serviceId, professionalId, appointmentDate.atTime(10, 45))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("O horário deve estar alinhado aos intervalos de 30 minutos."));

        mockMvc.perform(post("/api/auth/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Outro cliente","email":"other.client.appointment@salonhub.test","password":"senha123"}
                                """))
                .andExpect(status().isCreated());
        String otherClientToken = login("other.client.appointment@salonhub.test");
        mockMvc.perform(put("/api/agendamentos/{id}", appointmentId.longValue())
                        .header("Authorization", bearer(otherClientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentPayload(serviceId, professionalId, startsAt)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/estabelecimentos/{id}/agendamentos", establishmentId.longValue())
                        .header("Authorization", bearer(clientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentPayload(serviceId, professionalId, appointmentDate.atTime(12, 0))))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/agendamentos/{id}", appointmentId.longValue())
                        .header("Authorization", bearer(clientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentPayload(serviceId, professionalId, appointmentDate.atTime(12, 0))))
                .andExpect(status().isConflict());

        MvcResult secondProfessional = mockMvc.perform(post("/api/funcionarios")
                        .header("Authorization", bearer(administratorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Profissional Alternativo","email":"professional.alt.appointment@salonhub.test","password":"senha123"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        Number secondProfessionalId = JsonPath.read(secondProfessional.getResponse().getContentAsString(), "$.id");
        MvcResult secondService = mockMvc.perform(post("/api/servicos")
                        .header("Authorization", bearer(administratorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Barba MVP","price":30.00,"durationMinutes":30}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        Number secondServiceId = JsonPath.read(secondService.getResponse().getContentAsString(), "$.id");
        mockMvc.perform(put("/api/agendamentos/{id}", appointmentId.longValue())
                        .header("Authorization", bearer(clientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentPayload(secondServiceId, secondProfessionalId, appointmentDate.atTime(11, 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceName").value("Barba MVP"))
                .andExpect(jsonPath("$.professionalName").value("Profissional Alternativo"))
                .andExpect(jsonPath("$.endsAt").value(appointmentDate + "T11:30:00"));
        mockMvc.perform(get("/api/estabelecimentos/{id}/agendamentos/disponibilidade", establishmentId.longValue())
                        .header("Authorization", bearer(clientToken))
                        .param("serviceId", secondServiceId.toString())
                        .param("professionalId", secondProfessionalId.toString())
                        .param("date", appointmentDate.toString())
                        .param("appointmentId", appointmentId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durationMinutes").value(30));

        Appointment current = appointmentRepository.findById(appointmentId.longValue()).orElseThrow();
        Appointment past = appointmentRepository.saveAndFlush(Appointment.schedule(
                current.getEstablishment(),
                current.getClient(),
                current.getProfessional(),
                current.getService(),
                LocalDateTime.now().minusDays(1)));
        mockMvc.perform(put("/api/agendamentos/{id}", past.getId())
                        .header("Authorization", bearer(clientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentPayload(secondServiceId, secondProfessionalId, appointmentDate.atTime(11, 0))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Apenas agendamentos futuros e confirmados podem ser editados."));
        mockMvc.perform(patch("/api/agendamentos/{id}/cancelamento", past.getId())
                        .header("Authorization", bearer(clientToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("O agendamento não pode ser cancelado."));
        mockMvc.perform(post("/api/estabelecimentos/{id}/agendamentos", establishmentId.longValue())
                        .header("Authorization", bearer(clientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentPayload(serviceId, professionalId, appointmentDate.atTime(10, 30))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Você já possui um agendamento nesse período."));

        mockMvc.perform(post("/api/estabelecimentos/{id}/agendamentos", establishmentId.longValue())
                        .header("Authorization", bearer(clientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentPayload(serviceId, professionalId, appointmentDate.atTime(17, 30))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        mockMvc.perform(get("/api/agendamentos/me")
                        .header("Authorization", bearer(clientToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].serviceName").value("Corte MVP"));

        String professionalToken = login("professional.appointment@salonhub.test");

        mockMvc.perform(get("/api/agendamentos/profissional/me")
                        .header("Authorization", bearer(professionalToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].serviceName").value("Corte MVP"))
                .andExpect(jsonPath("$[0].clientName").value("Cliente MVP"));

        mockMvc.perform(get("/api/agendamentos/profissional/me")
                        .header("Authorization", bearer(clientToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/agendamentos/{id}/cancelamento", appointmentId.longValue())
                        .header("Authorization", bearer(professionalToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/agendamentos/{id}/cancelamento", appointmentId.longValue())
                        .header("Authorization", bearer(clientToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(appointmentId))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(put("/api/agendamentos/{id}", appointmentId.longValue()).header("Authorization", bearer(clientToken)).contentType(MediaType.APPLICATION_JSON).content(appointmentPayload(secondServiceId, secondProfessionalId, appointmentDate.atTime(11, 0))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Apenas agendamentos futuros e confirmados podem ser editados."));

        mockMvc.perform(get("/api/agendamentos/me")
                        .header("Authorization", bearer(clientToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].id").value(appointmentId))
                .andExpect(jsonPath("$[1].status").value("CANCELLED"));

        mockMvc.perform(patch("/api/agendamentos/{id}/cancelamento", appointmentId.longValue())
                        .header("Authorization", bearer(clientToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("O agendamento não pode ser cancelado."));

        mockMvc.perform(post("/api/estabelecimentos/{id}/agendamentos", establishmentId.longValue())
                        .header("Authorization", bearer(clientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentPayload(serviceId, professionalId, appointmentDate.atTime(9, 0))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
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
                                  "cnpj": "99888777000100"
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
