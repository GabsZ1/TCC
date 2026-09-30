package br.com.salonhub.controller;

import br.com.salonhub.support.TestDocuments;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Remarcação e troca de profissional pelo administrador. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AppointmentRescheduleIntegrationTests {
    @Autowired MockMvc mvc;

    @Test
    void administratorReschedulesAndReassignsRespectingConflictsAndPermissions() throws Exception {
        mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content("""
                {"ownerName":"Admin remarca","ownerEmail":"admin.remarca@salonhub.test",
                 "password":"senha123","establishmentName":"Salão Remarca","cnpj":"%s"}
                """.formatted("71727374000151"))).andExpect(status().isCreated());
        String admin = "Bearer " + login("admin.remarca@salonhub.test");

        String anaBody = createEmployee(admin, "Ana remarca", "ana.remarca@salonhub.test");
        long ana = ((Number) JsonPath.read(anaBody, "$.id")).longValue();
        mvc.perform(post("/api/auth/primeiro-acesso").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\",\"password\":\"senha123\",\"passwordConfirmation\":\"senha123\"}"
                        .formatted((String) JsonPath.read(anaBody, "$.firstAccessToken"))))
                .andExpect(status().isNoContent());
        long bruno = ((Number) JsonPath.read(createEmployee(admin, "Bruno remarca", "bruno.remarca@salonhub.test"), "$.id")).longValue();
        long service = ((Number) JsonPath.read(mvc.perform(post("/api/servicos").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Corte remarca\",\"price\":50.00,\"durationMinutes\":60}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id")).longValue();
        long customer = ((Number) JsonPath.read(mvc.perform(post("/api/clientes/estabelecimento/me")
                .header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Cliente remarca\",\"phone\":\"44999991111\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id")).longValue();
        long other = ((Number) JsonPath.read(mvc.perform(post("/api/clientes/estabelecimento/me")
                .header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Outra cliente\",\"phone\":\"44999992222\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id")).longValue();

        LocalDate day = LocalDate.now().plusDays(3);
        long appointment = book(admin, customer, service, ana, day + "T10:00:00");
        long otherAppointment = book(admin, other, service, bruno, day + "T15:00:00");
        mvc.perform(put("/api/servicos/{id}", service).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Corte reajustado\",\"price\":70,\"commissionPercentage\":20,\"durationMinutes\":90}"))
                .andExpect(status().isOk());

        // Troca de profissional e de horário, mantendo serviço e preço combinados.
        mvc.perform(put("/api/agendamentos/{id}/remarcacao", appointment).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(null, bruno, day + "T13:00:00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.professionalId").value(bruno))
                .andExpect(jsonPath("$.startsAt", startsWith(day + "T13:00")))
                .andExpect(jsonPath("$.serviceId").value(service))
                .andExpect(jsonPath("$.serviceName").value("Corte remarca"))
                .andExpect(jsonPath("$.agreedPrice").value(50.0))
                .andExpect(jsonPath("$.commissionPercentage").value(0.0))
                .andExpect(jsonPath("$.commissionAmount").value(0.0))
                .andExpect(jsonPath("$.durationMinutes").value(60));

        mvc.perform(put("/api/agendamentos/{id}/remarcacao", appointment).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(null, bruno, day + "T13:15:00")))
                .andExpect(status().isOk());
        mvc.perform(put("/api/agendamentos/{id}/remarcacao", appointment).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(null, bruno, day + "T13:07:00")))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/agendamentos/estabelecimento/me/clientes/{id}/disponibilidade", customer)
                        .header("Authorization", admin).param("serviceId", String.valueOf(service))
                        .param("professionalId", String.valueOf(bruno)).param("date", day.toString())
                        .param("appointmentId", String.valueOf(appointment)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableTimes[?(@ == '13:15:00')]").isNotEmpty())
                .andExpect(jsonPath("$.availableTimes[?(@ == '15:00:00')]").isEmpty());
        mvc.perform(get("/api/agendamentos/estabelecimento/me/clientes/{id}/disponibilidade", customer)
                        .header("Authorization", admin).param("serviceId", String.valueOf(service))
                        .param("professionalId", String.valueOf(bruno)).param("date", day.toString())
                        .param("appointmentId", String.valueOf(otherAppointment)))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content("""
                {"ownerName":"Outro admin","ownerEmail":"outro.admin.remarca@salonhub.test",
                 "password":"senha123","establishmentName":"Outro salão","cnpj":"11444777000161"}
                """)).andExpect(status().isCreated());
        String otherAdmin = "Bearer " + login("outro.admin.remarca@salonhub.test");
        long foreignProfessional = ((Number) JsonPath.read(createEmployee(otherAdmin,
                "Profissional externo", "prof.externo.remarca@salonhub.test"), "$.id")).longValue();
        long foreignService = ((Number) JsonPath.read(mvc.perform(post("/api/servicos").header("Authorization", otherAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Serviço externo\",\"price\":50,\"durationMinutes\":60}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id")).longValue();
        long foreignCustomer = ((Number) JsonPath.read(mvc.perform(post("/api/clientes/estabelecimento/me")
                .header("Authorization", otherAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Cliente externo\",\"phone\":\"44999993333\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id")).longValue();
        long foreignAppointment = book(otherAdmin, foreignCustomer, foreignService,
                foreignProfessional, day + "T11:00:00");
        mvc.perform(get("/api/agendamentos/estabelecimento/me/clientes/{id}/disponibilidade", customer)
                        .header("Authorization", admin).param("serviceId", String.valueOf(service))
                        .param("professionalId", String.valueOf(bruno)).param("date", day.toString())
                        .param("appointmentId", String.valueOf(foreignAppointment)))
                .andExpect(status().isNotFound());

        // Conflito com outro agendamento do Bruno.
        mvc.perform(put("/api/agendamentos/{id}/remarcacao", appointment).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(service, bruno, day + "T15:00:00")))
                .andExpect(status().isConflict());

        // Profissional não remarca.
        mvc.perform(put("/api/agendamentos/{id}/remarcacao", appointment)
                        .header("Authorization", "Bearer " + login("ana.remarca@salonhub.test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(null, ana, day + "T16:00:00")))
                .andExpect(status().isForbidden());

        long otherService = ((Number) JsonPath.read(mvc.perform(post("/api/servicos")
                .header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Outro serviço\",\"price\":40,\"commissionPercentage\":25,\"durationMinutes\":30}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id")).longValue();
        mvc.perform(put("/api/funcionarios/{id}/servicos", ana).header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"allServices\":false,\"serviceIds\":[%d]}".formatted(otherService)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/clientes/estabelecimento/me/opcoes-agendamento")
                .header("Authorization", admin).param("serviceId", String.valueOf(service)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.professionals.length()").value(1))
                .andExpect(jsonPath("$.professionals[0].id").value(bruno));

        mvc.perform(put("/api/agendamentos/{id}/remarcacao", appointment).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(otherService, bruno, day + "T16:00:00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceName").value("Outro serviço"))
                .andExpect(jsonPath("$.agreedPrice").value(40.0))
                .andExpect(jsonPath("$.commissionPercentage").value(25.0))
                .andExpect(jsonPath("$.commissionAmount").value(10.0))
                .andExpect(jsonPath("$.durationMinutes").value(30));

        // Cancelado não pode ser remarcado.
        mvc.perform(patch("/api/agendamentos/{id}/cancelamento-administrativo", appointment).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Cliente desistiu\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/agendamentos/{id}/remarcacao", appointment).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(null, ana, day + "T16:00:00")))
                .andExpect(status().isConflict());
    }

    private String login(String email) throws Exception {
        return JsonPath.read(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"senha123\"}".formatted(email)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.token");
    }

    private String createEmployee(String admin, String name, String email) throws Exception {
        return mvc.perform(post("/api/funcionarios").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"email\":\"%s\",\"occupation\":\"Cabeleireiro(a)\",\"cpf\":\"%s\",\"hireDate\":\"2025-01-02\"}"
                                .formatted(name, email, TestDocuments.cpf())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    private long book(String admin, long customer, long service, long professional, String startsAt) throws Exception {
        return ((Number) JsonPath.read(mvc.perform(post("/api/agendamentos/estabelecimento/me/clientes/{id}", customer)
                        .header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\":%d,\"professionalId\":%d,\"startsAt\":\"%s\"}"
                                .formatted(service, professional, startsAt)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private static String body(Long serviceId, long professionalId, String startsAt) {
        return "{\"serviceId\":%s,\"professionalId\":%d,\"startsAt\":\"%s\"}"
                .formatted(serviceId == null ? "null" : serviceId.toString(), professionalId, startsAt);
    }
}
