package br.com.salonhub.controller;

import br.com.salonhub.entity.Establishment;
import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.repository.EstablishmentRepository;
import br.com.salonhub.repository.UserRepository;
import br.com.salonhub.support.TestDocuments;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US 2.1 / 2.2 / 2.6 — dados cadastrais completos do funcionário e foto. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EmployeeDetailsIntegrationTests {

    private static final byte[] PNG = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52};

    @Autowired private MockMvc mockMvc;
    @Autowired private EstablishmentRepository establishmentRepository;
    @Autowired private UserRepository userRepository;

    @Test
    void shouldCreateEmployeeWithAllFieldsAndListThemAlphabetically() throws Exception {
        User admin = administrator();
        String cpf = TestDocuments.cpf();
        String formattedCpf = cpf.substring(0, 3) + "." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-" + cpf.substring(9);
        LocalDate hireDate = LocalDate.of(2025, 5, 12);

        create(admin, """
                {"name":"Zélia Souza","email":"%s","occupation":"Manicure","cpf":"%s",
                 "phone":"(44) 99999-1234","instagram":"@zelia.nails","address":"Rua das Flores, 100 - Centro",
                 "hireDate":"%s"}
                """.formatted(uniqueEmail(), formattedCpf, hireDate))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstAccessToken").isNotEmpty());
        create(admin, minimal("Ana Lima", uniqueEmail(), TestDocuments.cpf())).andExpect(status().isCreated());

        perform(admin, get("/api/funcionarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name").value(contains("Ana Lima", "Zélia Souza")))
                .andExpect(jsonPath("$[1].occupation").value("Manicure"))
                .andExpect(jsonPath("$[1].cpf").value(cpf))
                .andExpect(jsonPath("$[1].phone").value("44999991234"))
                .andExpect(jsonPath("$[1].instagram").value("zelia.nails"))
                .andExpect(jsonPath("$[1].address").value("Rua das Flores, 100 - Centro"))
                .andExpect(jsonPath("$[1].hireDate").value(hireDate.toString()))
                .andExpect(jsonPath("$[1].active").value(true))
                .andExpect(jsonPath("$[1].passwordInitialized").value(false))
                .andExpect(jsonPath("$[1].hasPhoto").value(false))
                .andExpect(jsonPath("$[0].phone").doesNotExist());
    }

    @Test
    void shouldRequireNameEmailOccupationAndCpf() throws Exception {
        User admin = administrator();

        create(admin, """
                {"name":" ","email":"","occupation":"","cpf":""}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.name").value("O nome é obrigatório"))
                .andExpect(jsonPath("$.fields.email").exists())
                .andExpect(jsonPath("$.fields.occupation").value("A ocupação é obrigatória"))
                .andExpect(jsonPath("$.fields.cpf").value("O CPF é obrigatório"));

        create(admin, minimal("Com CPF errado", uniqueEmail(), "12345678900"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.cpf").value("CPF inválido"));

        create(admin, minimal("Email errado", "emailsemarroba.com", TestDocuments.cpf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.email").value("Email inválido"));
    }

    @Test
    void shouldRejectDuplicatedCpfOnCreateAndUpdate() throws Exception {
        User admin = administrator();
        String cpf = TestDocuments.cpf();
        create(admin, minimal("Primeira", uniqueEmail(), cpf)).andExpect(status().isCreated());
        Long secondId = idOf(create(admin, minimal("Segunda", uniqueEmail(), TestDocuments.cpf())));

        create(admin, minimal("Repetida", uniqueEmail(), cpf))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Já existe um funcionário cadastrado com este CPF."));

        perform(admin, put("/api/funcionarios/{id}", secondId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minimal("Segunda", uniqueEmail(), cpf)))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldValidateHireDatePhoneAndInstagram() throws Exception {
        User admin = administrator();
        LocalDate tomorrow = LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(1);

        create(admin, withExtra("\"hireDate\":\"" + tomorrow + "\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A data de admissão não pode ser futura."));
        create(admin, withExtra("\"phone\":\"123\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Informe um telefone válido!"));
        create(admin, withExtra("\"instagram\":\"perfil com espaço\""))
                .andExpect(status().isBadRequest());
        create(admin, withExtra("\"hireDate\":\"" + tomorrow.minusDays(1) + "\""))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldUpdateAllFieldsKeepingTheSameEmployee() throws Exception {
        User admin = administrator();
        String cpf = TestDocuments.cpf();
        Long id = idOf(create(admin, minimal("Carla", uniqueEmail(), cpf)));
        String newEmail = uniqueEmail();

        perform(admin, put("/api/funcionarios/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Carla Mendes","email":"%s","occupation":"Esteticista","cpf":"%s",
                                 "phone":"4433221100","instagram":"carla.estetica","address":"Av. Brasil, 50","hireDate":"2024-01-10"}
                                """.formatted(newEmail, cpf)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Carla Mendes"))
                .andExpect(jsonPath("$.email").value(newEmail))
                .andExpect(jsonPath("$.occupation").value("Esteticista"))
                .andExpect(jsonPath("$.phone").value("4433221100"))
                .andExpect(jsonPath("$.hireDate").value("2024-01-10"));
    }

    @Test
    void shouldUploadShowAndRemoveThePhoto() throws Exception {
        User admin = administrator();
        Long id = idOf(create(admin, minimal("Foto", uniqueEmail(), TestDocuments.cpf())));

        perform(admin, multipart(HttpMethod.PUT, "/api/funcionarios/{id}/foto", id)
                        .file(new MockMultipartFile("file", "foto.png", "image/png", PNG)))
                .andExpect(status().isNoContent());

        perform(admin, get("/api/funcionarios"))
                .andExpect(jsonPath("$[0].hasPhoto").value(true));
        perform(admin, get("/api/funcionarios/{id}/foto", id))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(content().bytes(PNG));

        User employee = userRepository.findById(id).orElseThrow();
        perform(employee, get("/api/funcionarios/{id}/foto", id)).andExpect(status().isOk());

        perform(admin, delete("/api/funcionarios/{id}/foto", id)).andExpect(status().isNoContent());
        perform(admin, get("/api/funcionarios/{id}/foto", id)).andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectInvalidPhotosAndForeignAccess() throws Exception {
        User admin = administrator();
        User otherAdmin = administrator();
        Long id = idOf(create(admin, minimal("Foto", uniqueEmail(), TestDocuments.cpf())));
        Long colleagueId = idOf(create(admin, minimal("Colega", uniqueEmail(), TestDocuments.cpf())));

        perform(admin, multipart(HttpMethod.PUT, "/api/funcionarios/{id}/foto", id)
                        .file(new MockMultipartFile("file", "foto.png", "image/png", "não é imagem".getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Formato de imagem não suportado. Use JPG, PNG ou WEBP."));
        perform(admin, multipart(HttpMethod.PUT, "/api/funcionarios/{id}/foto", id))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Selecione uma imagem."));

        perform(otherAdmin, multipart(HttpMethod.PUT, "/api/funcionarios/{id}/foto", id)
                        .file(new MockMultipartFile("file", "foto.png", "image/png", PNG)))
                .andExpect(status().isNotFound());
        User colleague = userRepository.findById(colleagueId).orElseThrow();
        perform(colleague, get("/api/funcionarios/{id}/foto", id)).andExpect(status().isForbidden());
    }

    private ResultActions create(User admin, String body) throws Exception {
        return perform(admin, post("/api/funcionarios").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions perform(User user, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(jwt().jwt(token -> token.subject(user.getId().toString()))));
    }

    /** No Spring 7 o builder de multipart não herda de MockHttpServletRequestBuilder. */
    private ResultActions perform(User user, MockMultipartHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(jwt().jwt(token -> token.subject(user.getId().toString()))));
    }

    private Long idOf(ResultActions result) throws Exception {
        Number id = JsonPath.read(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private static String minimal(String name, String email, String cpf) {
        return """
                {"name":"%s","email":"%s","occupation":"Cabeleireiro(a)","cpf":"%s"}
                """.formatted(name, email, cpf);
    }

    private static String withExtra(String extraField) {
        return """
                {"name":"Extra","email":"%s","occupation":"Barbeiro(a)","cpf":"%s",%s}
                """.formatted(uniqueEmail(), TestDocuments.cpf(), extraField);
    }

    private static String uniqueEmail() {
        return "func-" + UUID.randomUUID().toString().substring(0, 8) + "@detalhes.test";
    }

    private User administrator() {
        String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String cnpj = String.format("%014d", Math.floorMod(System.nanoTime(), 100_000_000_000_000L));
        Establishment establishment = establishmentRepository.saveAndFlush(Establishment.builder()
                .name("Salão detalhes " + unique).cnpj(cnpj).build());
        return userRepository.saveAndFlush(User.builder()
                .name("Admin " + unique).email("admin-" + unique + "@detalhes.test")
                .password("{noop}x").role(UserRole.ADMIN).establishment(establishment).build());
    }
}
