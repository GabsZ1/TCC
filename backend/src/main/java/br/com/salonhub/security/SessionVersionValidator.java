package br.com.salonhub.security;

import br.com.salonhub.repository.UserRepository;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;

/**
 * Recusa tokens de sessões encerradas. O JWT leva a {@code sessionVersion} do usuário no momento
 * do login; trocar ou redefinir a senha e excluir a conta incrementam a versão no banco, e todo
 * token emitido antes passa a ser recusado com 401, em qualquer dispositivo.
 * <p>
 * Tokens emitidos antes desta regra não têm o claim: são aceitos enquanto a versão do usuário
 * ainda for 0 e expiram normalmente.
 */
public class SessionVersionValidator implements OAuth2TokenValidator<Jwt> {

    public static final String CLAIM = "sessionVersion";

    private static final OAuth2TokenValidatorResult SESSION_ENDED = OAuth2TokenValidatorResult.failure(
            new OAuth2Error("invalid_token", "Sessão encerrada. Entre novamente.", null));

    private final UserRepository userRepository;

    public SessionVersionValidator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Long userId = parseUserId(jwt.getSubject());
        if (userId == null) {
            return SESSION_ENDED;
        }
        Optional<Integer> currentVersion = userRepository.findSessionVersionById(userId);
        if (currentVersion.isEmpty()) {
            return SESSION_ENDED;
        }
        Object claim = jwt.getClaims().get(CLAIM);
        int tokenVersion = claim instanceof Number number ? number.intValue() : 0;
        return tokenVersion == currentVersion.get() ? OAuth2TokenValidatorResult.success() : SESSION_ENDED;
    }

    private static Long parseUserId(String subject) {
        try {
            return subject == null ? null : Long.valueOf(subject);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
