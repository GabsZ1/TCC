package br.com.salonhub.controller;

import br.com.salonhub.entity.PasswordResetToken;
import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.repository.PasswordResetTokenRepository;
import br.com.salonhub.repository.UserRepository;
import br.com.salonhub.service.auth.SecureTokens;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Encerramento de sessões: trocar ou redefinir a senha invalida todos os tokens emitidos antes,
 * em qualquer dispositivo; a renovação mantém conectado quem está usando o sistema.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SessionInvalidationIntegrationTests {

    private static final String PASSWORD = "Senha@123";
    private static final String NEW_PASSWORD = "NovaSenha@456";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordResetTokenRepository resetTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtEncoder jwtEncoder;
    @Autowired private Clock clock;

    @Test
    void shouldEndEverySessionWhenThePasswordIsChangedAndKeepTheCurrentDeviceConnected() throws Exception {
        User client = client();
        String thisDevice = login(client.getEmail(), PASSWORD);
        String otherDevice = login(client.getEmail(), PASSWORD);

        String body = mockMvc.perform(patch("/api/auth/senha")
                        .header("Authorization", "Bearer " + thisDevice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"%s","newPassword":"%s","newPasswordConfirmation":"%s"}
                                """.formatted(PASSWORD, NEW_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andReturn().getResponse().getContentAsString();
        String newToken = JsonPath.read(body, "$.token");

        me(thisDevice).andExpect(status().isUnauthorized());
        me(otherDevice).andExpect(status().isUnauthorized());
        me(newToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(client.getEmail()));
    }

    @Test
    void shouldEndEverySessionWhenThePasswordIsReset() throws Exception {
        User client = client();
        String session = login(client.getEmail(), PASSWORD);
        String rawResetToken = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now(clock);
        resetTokenRepository.saveAndFlush(new PasswordResetToken(
                client, SecureTokens.sha256(rawResetToken), now, now.plusMinutes(15)));

        mockMvc.perform(post("/api/auth/redefinir-senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","newPassword":"%s","newPasswordConfirmation":"%s"}
                                """.formatted(rawResetToken, NEW_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isNoContent());

        me(session).andExpect(status().isUnauthorized());
        login(client.getEmail(), NEW_PASSWORD);
    }

    @Test
    void shouldRenewAValidSessionWithoutEndingIt() throws Exception {
        User client = client();
        String session = login(client.getEmail(), PASSWORD);

        String body = mockMvc.perform(post("/api/auth/renovar").header("Authorization", "Bearer " + session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.user.id").value(client.getId()))
                .andReturn().getResponse().getContentAsString();
        String renewed = JsonPath.read(body, "$.token");

        me(renewed).andExpect(status().isOk());
        me(session).andExpect(status().isOk());
    }

    @Test
    void shouldNotRenewWithoutAValidSession() throws Exception {
        mockMvc.perform(post("/api/auth/renovar")).andExpect(status().isUnauthorized());

        User client = client();
        String session = login(client.getEmail(), PASSWORD);
        client = userRepository.findById(client.getId()).orElseThrow();
        client.endAllSessions();
        userRepository.saveAndFlush(client);

        mockMvc.perform(post("/api/auth/renovar").header("Authorization", "Bearer " + session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAcceptTokensIssuedBeforeTheRuleUntilTheSessionIsEnded() throws Exception {
        User client = client();
        String legacyToken = tokenWithoutSessionVersion(client);

        me(legacyToken).andExpect(status().isOk());

        client = userRepository.findById(client.getId()).orElseThrow();
        client.endAllSessions();
        userRepository.saveAndFlush(client);

        me(legacyToken).andExpect(status().isUnauthorized());
    }

    private String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private ResultActions me(String token) throws Exception {
        return mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token));
    }

    private User client() {
        String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return userRepository.saveAndFlush(User.builder()
                .name("Cliente sessão").email("sessao-" + unique + "@sessao.test")
                .password(passwordEncoder.encode(PASSWORD)).role(UserRole.CLIENT).build());
    }

    /** Token no formato antigo, sem o claim sessionVersion. */
    private String tokenWithoutSessionVersion(User user) {
        Instant issuedAt = Instant.now(clock);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("salonhub")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(30, ChronoUnit.MINUTES))
                .subject(user.getId().toString())
                .claim("userId", user.getId())
                .claim("role", user.getRole().name())
                .claim("email", user.getEmail())
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
