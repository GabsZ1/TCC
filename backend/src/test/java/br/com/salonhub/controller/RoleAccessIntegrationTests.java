package br.com.salonhub.controller;

import br.com.salonhub.entity.Establishment;
import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.repository.EstablishmentRepository;
import br.com.salonhub.repository.UserRepository;
import br.com.salonhub.service.auth.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Primeira camada de autorização: o perfil que vem no token real (claim "role") é conferido
 * pelo @PreAuthorize antes de o endpoint executar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RoleAccessIntegrationTests {

    private static final String FORBIDDEN = "Seu perfil não tem permissão para acessar este recurso.";

    @Autowired private MockMvc mockMvc;
    @Autowired private EstablishmentRepository establishmentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TokenService tokenService;

    @Test
    void shouldLetEachRoleUseItsOwnEndpoints() throws Exception {
        Establishment establishment = establishment();

        perform(get("/api/servicos"), user(UserRole.ADMIN, establishment)).andExpect(status().isOk());
        perform(get("/api/agendamentos/profissional/me"), user(UserRole.PROFESSIONAL, establishment))
                .andExpect(status().isOk());
        perform(get("/api/agendamentos/me"), user(UserRole.CLIENT, null)).andExpect(status().isOk());
        perform(get("/api/admin-sistema/estabelecimentos"), user(UserRole.SYSTEM_ADMIN, null))
                .andExpect(status().isOk());
        // Rotas de leitura pública aceitam qualquer perfil logado.
        perform(get("/api/estabelecimentos"), user(UserRole.PROFESSIONAL, establishment))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRefuseEndpointsOfOtherRolesWithAFriendlyMessage() throws Exception {
        Establishment establishment = establishment();
        User administrator = user(UserRole.ADMIN, establishment);
        User professional = user(UserRole.PROFESSIONAL, establishment);
        User client = user(UserRole.CLIENT, null);
        User systemAdministrator = user(UserRole.SYSTEM_ADMIN, null);

        expectForbidden(perform(get("/api/servicos"), client));
        expectForbidden(perform(get("/api/estabelecimentos/me/relatorio-financeiro"), professional));
        expectForbidden(perform(get("/api/agendamentos/me"), administrator));
        expectForbidden(perform(get("/api/profissionais/me/agenda/horarios"), client));
        expectForbidden(perform(get("/api/admin-sistema/estabelecimentos"), administrator));
        expectForbidden(perform(get("/api/clientes/estabelecimento/me"), client));
        expectForbidden(perform(get("/api/servicos"), systemAdministrator));
    }

    @Test
    void shouldSplitCustomerActionsBetweenStaffAndAdministrator() throws Exception {
        Establishment establishment = establishment();
        User professional = user(UserRole.PROFESSIONAL, establishment);

        perform(get("/api/clientes/estabelecimento/me"), professional).andExpect(status().isOk());
        expectForbidden(perform(patch("/api/clientes/estabelecimento/me/1/mesclar/2"), professional));
    }

    @Test
    void shouldKeepPublicRoutesOpenAndProtectedRoutesClosedWithoutAToken() throws Exception {
        mockMvc.perform(post("/api/auth/recuperar-senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ninguem-" + UUID.randomUUID() + "@perfil.test\"}"))
                .andExpect(status().isAccepted());
        mockMvc.perform(get("/api/servicos")).andExpect(status().isUnauthorized());
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, User user) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + tokenService.generate(user).value()));
    }

    private static void expectForbidden(ResultActions result) throws Exception {
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(FORBIDDEN));
    }

    private Establishment establishment() {
        String cnpj = String.format("%014d", Math.floorMod(System.nanoTime(), 100_000_000_000_000L));
        return establishmentRepository.saveAndFlush(Establishment.builder()
                .name("Salão perfis " + UUID.randomUUID().toString().substring(0, 8)).cnpj(cnpj).build());
    }

    private User user(UserRole role, Establishment establishment) {
        String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return userRepository.saveAndFlush(User.builder()
                .name("Usuário " + role).email(role.name().toLowerCase() + "-" + unique + "@perfil.test")
                .password(passwordEncoder.encode("Senha@123")).role(role).establishment(establishment).build());
    }
}
