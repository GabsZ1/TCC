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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldRegisterLoginAndGetAuthenticatedUser() throws Exception {
        mockMvc.perform(post("/api/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ownerName": "Administrador Teste",
                                  "ownerEmail": "auth.integration@salonhub.test",
                                  "password": "senha123",
                                  "establishmentName": "Salão Integração",
                                  "cnpj": "11222333000181",
                                  "establishmentEmail": "contato@salonhub.test",
                                  "establishmentPhone": "44999999999"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").isNumber())
                .andExpect(jsonPath("$.establishmentId").isNumber());

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "auth.integration@salonhub.test",
                                  "password": "senha123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();

        String token = JsonPath.read(login.getResponse().getContentAsString(), "$.token");

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("auth.integration@salonhub.test"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.establishmentName").value("Salão Integração"));

        mockMvc.perform(get("/api/estabelecimentos/me/perfil")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("44999999999"));
    }

    @Test
    void shouldRejectCnpjWithInvalidCheckDigits() throws Exception {
        mockMvc.perform(post("/api/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ownerName": "Administrador Inválido",
                                  "ownerEmail": "invalid.cnpj@salonhub.test",
                                  "password": "senha123",
                                  "establishmentName": "Salão CNPJ Inválido",
                                  "cnpj": "11.222.333/0001-80"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.cnpj").value("CNPJ inválido"));

        mockMvc.perform(post("/api/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ownerName": "Administrador Inválido",
                                  "ownerEmail": "repeated.cnpj@salonhub.test",
                                  "password": "senha123",
                                  "establishmentName": "Salão CNPJ Repetido",
                                  "cnpj": "11.111.111/1111-11"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.cnpj").value("CNPJ inválido"));
    }
    @Test
    void shouldValidateOptionalEstablishmentPhoneOnRegistration() throws Exception {
        for (String phone : java.util.List.of("abc44999999999", "123", "449999999999", "---")) {
            String body = """
                    {"ownerName":"Administrador","ownerEmail":"%s","password":"senha123",
                     "establishmentName":"Salão Telefone","cnpj":"%s","establishmentPhone":"%s"}
                    """.formatted("invalid-" + phone.hashCode() + "@salonhub.test",
                    nextCnpj(), phone);
            mockMvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }

        String formatted = """
                {"ownerName":"Administrador Formatado","ownerEmail":"formatted.phone@salonhub.test","password":"senha123",
                 "establishmentName":"Salão Formatado","cnpj":"%s","establishmentPhone":"(44) 99999-9999"}
                """.formatted(nextCnpj());
        mockMvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content(formatted))
                .andExpect(status().isCreated());
        String blank = """
                {"ownerName":"Administrador Sem Telefone","ownerEmail":"blank.phone@salonhub.test","password":"senha123",
                 "establishmentName":"Salão Sem Telefone","cnpj":"%s","establishmentPhone":""}
                """.formatted(nextCnpj());
        mockMvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content(blank))
                .andExpect(status().isCreated());
        String nullPhone = """
                {"ownerName":"Administrador Telefone Nulo","ownerEmail":"null.phone@salonhub.test","password":"senha123",
                 "establishmentName":"Salão Telefone Nulo","cnpj":"%s","establishmentPhone":null}
                """.formatted(nextCnpj());
        mockMvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content(nullPhone))
                .andExpect(status().isCreated());
    }
    private static int cnpjSequence = 7000;

    private static String nextCnpj() {
        String base = "12345678" + String.format("%04d", cnpjSequence++);
        int first = cnpjDigit(base);
        return base + first + cnpjDigit(base + first);
    }

    private static int cnpjDigit(String digits) {
        int[] weights = digits.length() == 12
                ? new int[] {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2}
                : new int[] {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int sum = 0;
        for (int index = 0; index < digits.length(); index++) {
            sum += (digits.charAt(index) - '0') * weights[index];
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }}
