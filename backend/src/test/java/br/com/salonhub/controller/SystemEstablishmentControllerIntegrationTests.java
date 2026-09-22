package br.com.salonhub.controller;

import br.com.salonhub.entity.Establishment;
import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.repository.EstablishmentRepository;
import br.com.salonhub.repository.UserRepository;
import br.com.salonhub.service.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SystemEstablishmentControllerIntegrationTests {

    private static final String ENDPOINT = "/api/admin-sistema/estabelecimentos";

    @Autowired private MockMvc mockMvc;
    @Autowired private EstablishmentRepository establishmentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TokenService tokenService;

    @Test
    void systemAdministratorShouldListAllEstablishmentsAndDeactivateOne() throws Exception {
        Establishment active = establishmentRepository.saveAndFlush(Establishment.builder()
                .name("Ativo para sistema").cnpj("12345678000195").build());
        Establishment inactive = establishmentRepository.saveAndFlush(Establishment.builder()
                .name("Inativo para sistema").cnpj("22345678000149").active(false).build());
        User systemAdministrator = userRepository.saveAndFlush(user(
                "Administrador Sistema", "system-admin@salonhub.test", UserRole.SYSTEM_ADMIN, null));

        mockMvc.perform(get(ENDPOINT).header("Authorization", bearer(systemAdministrator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)].active".formatted(active.getId()))
                        .value(contains(true)))
                .andExpect(jsonPath("$[?(@.id == %d)].active".formatted(inactive.getId()))
                        .value(contains(false)));

        mockMvc.perform(delete(ENDPOINT + "/{id}", active.getId())
                        .header("Authorization", bearer(systemAdministrator)))
                .andExpect(status().isNoContent());

        assertThat(establishmentRepository.findById(active.getId()).orElseThrow().isActive()).isFalse();
        assertThat(establishmentRepository.findById(inactive.getId()).orElseThrow().isActive()).isFalse();
    }

    @Test
    void establishmentAdministratorMustNotAccessSystemAdministration() throws Exception {
        Establishment establishment = establishmentRepository.saveAndFlush(Establishment.builder()
                .name("Sem acesso ao sistema").cnpj("32345678000102").build());
        User administrator = userRepository.saveAndFlush(user(
                "Administrador Local", "local-admin@salonhub.test", UserRole.ADMIN, establishment));

        mockMvc.perform(get(ENDPOINT).header("Authorization", bearer(administrator)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(ENDPOINT + "/{id}", establishment.getId())
                        .header("Authorization", bearer(administrator)))
                .andExpect(status().isForbidden());

        assertThat(establishmentRepository.findById(establishment.getId()).orElseThrow().isActive()).isTrue();
    }

    private User user(String name, String email, UserRole role, Establishment establishment) {
        return User.builder().name(name).email(email).password("senha-de-teste")
                .role(role).establishment(establishment).active(true).build();
    }

    private String bearer(User user) {
        return "Bearer " + tokenService.generate(user).value();
    }
}
