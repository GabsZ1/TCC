package br.com.salonhub.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD;
import static org.springframework.http.HttpHeaders.ORIGIN;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifica as origens e métodos permitidos pela configuração CORS. */
@SpringBootTest(properties = "app.cors.allowed-origins=https://app.salonhub.example")
@AutoConfigureMockMvc
class CorsConfigurationIntegrationTests {

    @Autowired MockMvc mvc;

    @Test
    void shouldAllowConfiguredFrontendOriginOnlyWithRequiredHeadersAndMethods() throws Exception {
        mvc.perform(options("/api/auth/login")
                        .header(ORIGIN, "https://app.salonhub.example")
                        .header(ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(ACCESS_CONTROL_ALLOW_ORIGIN, "https://app.salonhub.example"))
                .andExpect(header().string(ACCESS_CONTROL_ALLOW_METHODS, org.hamcrest.Matchers.containsString("POST")))
                .andExpect(header().string(ACCESS_CONTROL_ALLOW_HEADERS, org.hamcrest.Matchers.containsString("Authorization")));
    }
}
