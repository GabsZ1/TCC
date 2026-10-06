package br.com.salonhub.support;

import br.com.salonhub.entity.User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Autentica uma requisição do MockMvc como o usuário informado, com o mesmo perfil (ROLE_*)
 * que o token real teria. Sem o perfil, o @PreAuthorize dos controllers recusa a requisição.
 */
public final class TestJwt {

    private TestJwt() {
    }

    public static JwtRequestPostProcessor as(User user) {
        String role = user.getRole().name();
        return jwt()
                .jwt(token -> token.subject(user.getId().toString()).claim("role", role))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
