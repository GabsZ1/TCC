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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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

/**
 * US 2.1 / 2.2 / 2.6 e perfil do funcionário.
 * Regras: o administrador cadastra (nome, e-mail, ocupação, CPF e data de admissão) e
 * depois só altera o nome; CPF e data de admissão travam depois de gravados (podem ser
 * preenchidos uma única vez se estiverem vazios). E-mail, ocupação, contatos e foto
 * passam a ser do próprio funcionário.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EmployeeDetailsIntegrationTests {

    private static final String PASSWORD = "senha-atual-1";
    private static final byte[] PNG = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52};

    @Autowired private MockMvc mockMvc;
    @Autowired private EstablishmentRepository establishmentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    // ---------- cadastro pelo administrador ----------

    @Test
    void shouldCreateEmployeeAndListAlphabetically() throws Exception {
        User admin = administrator(establishment());
        String cpf = TestDocuments.cpf();
        String formattedCpf = cpf.substring(0, 3) + "." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-" + cpf.substring(9);

        create(admin, body("Zélia Souza", uniqueEmail(), "Manicure", formattedCpf, "2025-05-12"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstAccessToken").isNotEmpty());
        create(admin, body("Ana Lima", uniqueEmail(), "Barbeiro(a)", TestDocuments.cpf(), "2024-02-01"))
                .andExpect(status().isCreated());

        perform(admin, get("/api/funcionarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name").value(contains("Ana Lima", "Zélia Souza")))
                .andExpect(jsonPath("$[1].occupation").value("Manicure"))
                .andExpect(jsonPath("$[1].cpf").value(cpf))
                .andExpect(jsonPath("$[1].hireDate").value("2025-05-12"))
                .andExpect(jsonPath("$[1].passwordInitialized").value(false))
                .andExpect(jsonPath("$[1].hasPhoto").value(false))
                .andExpect(jsonPath("$[1].phone").doesNotExist());
    }

    @Test
    void shouldRequireNameEmailOccupationCpfAndHireDate() throws Exception {
        User admin = administrator(establishment());

        create(admin, """
                {"name":" ","email":"","occupation":"","cpf":""}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.name").value("O nome é obrigatório"))
                .andExpect(jsonPath("$.fields.email").exists())
                .andExpect(jsonPath("$.fields.occupation").value("A ocupação é obrigatória"))
                .andExpect(jsonPath("$.fields.cpf").value("O CPF é obrigatório"))
                .andExpect(jsonPath("$.fields.hireDate").value("A data de admissão é obrigatória"));

        create(admin, body("CPF errado", uniqueEmail(), "Manicure", "12345678900", "2025-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.cpf").value("CPF inválido"));
        create(admin, body("Email errado", "emailsemarroba.com", "Manicure", TestDocuments.cpf(), "2025-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.email").value("Email inválido"));
        LocalDate tomorrow = LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(1);
        create(admin, body("Futuro", uniqueEmail(), "Manicure", TestDocuments.cpf(), tomorrow.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A data de admissão não pode ser futura."));
    }

    @Test
    void shouldRejectDuplicatedCpf() throws Exception {
        User admin = administrator(establishment());
        String cpf = TestDocuments.cpf();
        create(admin, body("Primeira", uniqueEmail(), "Manicure", cpf, "2025-01-01")).andExpect(status().isCreated());

        create(admin, body("Repetida", uniqueEmail(), "Manicure", cpf, "2025-01-01"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Já existe um funcionário cadastrado com este CPF."));
    }

    // ---------- edição pelo administrador ----------

    @Test
    void shouldLetAdministratorOnlyRenameAndKeepCpfAndHireDateLocked() throws Exception {
        User admin = administrator(establishment());
        String cpf = TestDocuments.cpf();
        String email = uniqueEmail();
        Long id = idOf(create(admin, body("Carla", email, "Manicure", cpf, "2024-01-10")));

        perform(admin, put("/api/funcionarios/{id}", id).contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Carla Mendes","cpf":"%s","hireDate":"2024-01-10","email":"ignorado@x.test","occupation":"Ignorada"}
                """.formatted(cpf)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Carla Mendes"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.occupation").value("Manicure"));

        perform(admin, put("/api/funcionarios/{id}", id).contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Carla Mendes","cpf":"%s"}
                """.formatted(TestDocuments.cpf())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("O CPF não pode ser alterado depois de cadastrado."));
        perform(admin, put("/api/funcionarios/{id}", id).contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Carla Mendes","hireDate":"2023-05-05"}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A data de admissão não pode ser alterada depois de cadastrada."));
    }

    @Test
    void shouldLetAdministratorFillMissingCpfAndHireDateOnlyOnce() throws Exception {
        Establishment establishment = establishment();
        User admin = administrator(establishment);
        User legacy = professional(establishment, null, null);
        String taken = TestDocuments.cpf();
        create(admin, body("Outra", uniqueEmail(), "Manicure", taken, "2025-01-01")).andExpect(status().isCreated());

        perform(admin, put("/api/funcionarios/{id}", legacy.getId()).contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"%s","cpf":"%s"}
                """.formatted(legacy.getName(), taken)))
                .andExpect(status().isConflict());

        String cpf = TestDocuments.cpf();
        perform(admin, put("/api/funcionarios/{id}", legacy.getId()).contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"%s","cpf":"%s","hireDate":"2020-03-01"}
                """.formatted(legacy.getName(), cpf)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cpf").value(cpf))
                .andExpect(jsonPath("$.hireDate").value("2020-03-01"));

        perform(admin, put("/api/funcionarios/{id}", legacy.getId()).contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"%s","cpf":"%s"}
                """.formatted(legacy.getName(), TestDocuments.cpf())))
                .andExpect(status().isBadRequest());
    }

    // ---------- perfil do próprio funcionário ----------

    @Test
    void shouldShowOwnProfileOnlyToTheProfessional() throws Exception {
        Establishment establishment = establishment();
        User admin = administrator(establishment);
        User professional = professional(establishment, TestDocuments.cpf(), LocalDate.of(2023, 6, 1));

        perform(professional, get("/api/funcionarios/me/perfil"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(professional.getId()))
                .andExpect(jsonPath("$.cpf").value(professional.getCpf()))
                .andExpect(jsonPath("$.hireDate").value("2023-06-01"));
        perform(admin, get("/api/funcionarios/me/perfil")).andExpect(status().isForbidden());
    }

    @Test
    void shouldLetTheProfessionalUpdateOccupationAndContactsButNotAdministrativeData() throws Exception {
        Establishment establishment = establishment();
        User professional = professional(establishment, TestDocuments.cpf(), LocalDate.of(2023, 6, 1));
        String originalCpf = professional.getCpf();

        perform(professional, put("/api/funcionarios/me/perfil").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","occupation":"Barbeiro(a) e designer de cílios","phone":"(44) 99888-7766",
                 "instagram":"@barba.cilios","address":"Rua A, 10","name":"Outro Nome","cpf":"00000000000"}
                """.formatted(professional.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.occupation").value("Barbeiro(a) e designer de cílios"))
                .andExpect(jsonPath("$.phone").value("44998887766"))
                .andExpect(jsonPath("$.instagram").value("barba.cilios"))
                .andExpect(jsonPath("$.address").value("Rua A, 10"))
                .andExpect(jsonPath("$.name").value(professional.getName()))
                .andExpect(jsonPath("$.cpf").value(originalCpf));

        perform(professional, put("/api/funcionarios/me/perfil").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","occupation":" ","phone":"123"}
                """.formatted(professional.getEmail())))
                .andExpect(status().isBadRequest());
        perform(professional, put("/api/funcionarios/me/perfil").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","occupation":"Manicure","phone":"123"}
                """.formatted(professional.getEmail())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Informe um telefone válido!"));
    }

    @Test
    void shouldRequireTheCurrentPasswordToChangeTheLoginEmail() throws Exception {
        Establishment establishment = establishment();
        User professional = professional(establishment, TestDocuments.cpf(), LocalDate.of(2023, 6, 1));
        User other = professional(establishment, TestDocuments.cpf(), LocalDate.of(2023, 6, 1));
        String newEmail = uniqueEmail();

        perform(professional, put("/api/funcionarios/me/perfil").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","occupation":"Manicure"}
                """.formatted(newEmail)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Para alterar o e-mail de acesso, informe corretamente sua senha atual."));
        perform(professional, put("/api/funcionarios/me/perfil").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","occupation":"Manicure","currentPassword":"errada"}
                """.formatted(newEmail)))
                .andExpect(status().isBadRequest());
        perform(professional, put("/api/funcionarios/me/perfil").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","occupation":"Manicure","currentPassword":"%s"}
                """.formatted(other.getEmail(), PASSWORD)))
                .andExpect(status().isConflict());

        perform(professional, put("/api/funcionarios/me/perfil").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","occupation":"Manicure","currentPassword":"%s"}
                """.formatted(newEmail.toUpperCase(), PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(newEmail));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(newEmail, PASSWORD)))
                .andExpect(status().isOk());
    }

    // ---------- foto: só o próprio funcionário envia/remove ----------

    @Test
    void shouldLetOnlyTheProfessionalUploadAndRemoveTheirPhoto() throws Exception {
        Establishment establishment = establishment();
        User admin = administrator(establishment);
        User professional = professional(establishment, TestDocuments.cpf(), LocalDate.of(2023, 6, 1));

        perform(professional, multipart(HttpMethod.PUT, "/api/funcionarios/me/foto")
                        .file(new MockMultipartFile("file", "foto.png", "image/png", PNG)))
                .andExpect(status().isNoContent());

        perform(admin, get("/api/funcionarios")).andExpect(jsonPath("$[0].hasPhoto").value(true));
        perform(admin, get("/api/funcionarios/{id}/foto", professional.getId()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(content().bytes(PNG));
        perform(professional, get("/api/funcionarios/{id}/foto", professional.getId())).andExpect(status().isOk());

        perform(professional, delete("/api/funcionarios/me/foto")).andExpect(status().isNoContent());
        perform(admin, get("/api/funcionarios/{id}/foto", professional.getId())).andExpect(status().isNotFound());
    }

    @Test
    void shouldNotLetTheAdministratorOrColleaguesManageThePhoto() throws Exception {
        Establishment establishment = establishment();
        User admin = administrator(establishment);
        User professional = professional(establishment, TestDocuments.cpf(), LocalDate.of(2023, 6, 1));
        User colleague = professional(establishment, TestDocuments.cpf(), LocalDate.of(2023, 6, 1));

        perform(admin, multipart(HttpMethod.PUT, "/api/funcionarios/me/foto")
                        .file(new MockMultipartFile("file", "foto.png", "image/png", PNG)))
                .andExpect(status().isForbidden());
        perform(admin, multipart(HttpMethod.PUT, "/api/funcionarios/{id}/foto", professional.getId())
                        .file(new MockMultipartFile("file", "foto.png", "image/png", PNG)))
                .andExpect(status().isMethodNotAllowed());
        perform(colleague, get("/api/funcionarios/{id}/foto", professional.getId())).andExpect(status().isForbidden());

        perform(professional, multipart(HttpMethod.PUT, "/api/funcionarios/me/foto")
                        .file(new MockMultipartFile("file", "foto.png", "image/png", "não é imagem".getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Formato de imagem não suportado. Use JPG, PNG ou WEBP."));
        perform(professional, multipart(HttpMethod.PUT, "/api/funcionarios/me/foto"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Selecione uma imagem."));
        assertThat(userRepository.findById(professional.getId())).isPresent();
    }

    // ---------- apoio ----------

    private ResultActions create(User admin, String json) throws Exception {
        return perform(admin, post("/api/funcionarios").contentType(MediaType.APPLICATION_JSON).content(json));
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

    private static String body(String name, String email, String occupation, String cpf, String hireDate) {
        return """
                {"name":"%s","email":"%s","occupation":"%s","cpf":"%s","hireDate":"%s"}
                """.formatted(name, email, occupation, cpf, hireDate);
    }

    private static String uniqueEmail() {
        return "func-" + UUID.randomUUID().toString().substring(0, 8) + "@detalhes.test";
    }

    private Establishment establishment() {
        String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String cnpj = String.format("%014d", Math.floorMod(System.nanoTime(), 100_000_000_000_000L));
        return establishmentRepository.saveAndFlush(Establishment.builder()
                .name("Salão detalhes " + unique).cnpj(cnpj).build());
    }

    private User administrator(Establishment establishment) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.saveAndFlush(User.builder()
                .name("Admin " + unique).email("admin-" + unique + "@detalhes.test")
                .password(passwordEncoder.encode(PASSWORD)).role(UserRole.ADMIN).establishment(establishment).build());
    }

    private User professional(Establishment establishment, String cpf, LocalDate hireDate) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.saveAndFlush(User.builder()
                .name("Profissional " + unique).email("prof-" + unique + "@detalhes.test")
                .password(passwordEncoder.encode(PASSWORD)).role(UserRole.PROFESSIONAL).establishment(establishment)
                .occupation("Cabeleireiro(a)").cpf(cpf).hireDate(hireDate).build());
    }
}
