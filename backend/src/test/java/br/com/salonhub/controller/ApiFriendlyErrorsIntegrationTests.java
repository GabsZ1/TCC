package br.com.salonhub.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Erros técnicos do Spring nunca chegam ao usuário com a mensagem interna (ex.: "No static resource ..."). */
@SpringBootTest
@AutoConfigureMockMvc
class ApiFriendlyErrorsIntegrationTests {

    @Autowired private MockMvc mockMvc;

    @Test
    void shouldAnswerUnknownRoutesWithAFriendlyMessage() throws Exception {
        mockMvc.perform(get("/api/rota-que-nao-existe").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("O recurso solicitado não está disponível."));
    }

    @Test
    void shouldAnswerUnsupportedMethodWithAFriendlyMessage() throws Exception {
        mockMvc.perform(delete("/api/auth/me").with(jwt()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.message").value("Operação não permitida para este recurso."));
    }

    @Test
    void shouldAnswerMalformedParametersAndBodiesWithAFriendlyMessage() throws Exception {
        mockMvc.perform(get("/api/estabelecimentos/me/atendimentos-realizados")
                        .param("startDate", "31/03/2026").param("endDate", "2026-03-31")
                        .with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Requisição inválida. Verifique os dados informados."));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ json quebrado"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Requisição inválida. Verifique os dados informados."));
    }
}
