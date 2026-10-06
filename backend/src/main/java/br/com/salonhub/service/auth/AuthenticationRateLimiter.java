package br.com.salonhub.service.auth;

import br.com.salonhub.exception.TooManyRequestsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Proteção contra força bruta no login e contra abuso dos formulários públicos.
 * <ul>
 *   <li><b>Login por e-mail:</b> {@code app.security.login.max-failures-per-email} senhas erradas
 *       (padrão 5) dentro de {@code window-minutes} bloqueiam o e-mail por {@code block-minutes}.
 *       Vale também para e-mails sem cadastro, para que o bloqueio não revele quais contas existem.</li>
 *   <li><b>Login por IP:</b> o mesmo, com um limite maior ({@code max-failures-per-ip}), contra quem
 *       testa muitos e-mails diferentes a partir da mesma rede.</li>
 *   <li><b>Formulários públicos</b> (cadastro de estabelecimento, cadastro de cliente e recuperação de
 *       senha): até {@code app.security.public-forms.max-requests-per-ip} pedidos por IP e por formulário
 *       a cada {@code window-minutes}.</li>
 * </ul>
 * Os contadores ficam em memória: são zerados quando a API reinicia e valem para uma única instância.
 * Atrás de um proxy reverso, configure {@code server.forward-headers-strategy} para que o IP real do
 * cliente seja usado.
 */
@Component
public class AuthenticationRateLimiter {

    static final String LOGIN_BLOCKED = "Muitas tentativas de login. Tente novamente em %d minuto(s).";
    static final String PUBLIC_FORM_BLOCKED =
            "Muitas solicitações feitas a partir desta rede. Tente novamente em %d minuto(s).";

    private static final Logger log = LoggerFactory.getLogger(AuthenticationRateLimiter.class);
    private static final int MAX_TRACKED_KEYS = 50_000;

    public enum PublicForm { ESTABLISHMENT_REGISTRATION, CLIENT_REGISTRATION, PASSWORD_RECOVERY }

    private record Counter(int count, Instant windowEndsAt, Instant blockedUntil) {
        boolean isBlockedAt(Instant now) {
            return blockedUntil != null && now.isBefore(blockedUntil);
        }

        boolean isExpiredAt(Instant now) {
            return !now.isBefore(blockedUntil != null ? blockedUntil : windowEndsAt);
        }
    }

    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();
    private final Clock clock;
    private final boolean enabled;
    private final int maxLoginFailuresPerEmail;
    private final int maxLoginFailuresPerIp;
    private final Duration loginWindow;
    private final Duration loginBlock;
    private final int maxPublicFormRequestsPerIp;
    private final Duration publicFormWindow;

    public AuthenticationRateLimiter(
            Clock clock,
            @Value("${app.security.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.security.login.max-failures-per-email:5}") int maxLoginFailuresPerEmail,
            @Value("${app.security.login.max-failures-per-ip:20}") int maxLoginFailuresPerIp,
            @Value("${app.security.login.window-minutes:15}") long loginWindowMinutes,
            @Value("${app.security.login.block-minutes:15}") long loginBlockMinutes,
            @Value("${app.security.public-forms.max-requests-per-ip:10}") int maxPublicFormRequestsPerIp,
            @Value("${app.security.public-forms.window-minutes:60}") long publicFormWindowMinutes) {
        this.clock = clock;
        this.enabled = enabled;
        this.maxLoginFailuresPerEmail = maxLoginFailuresPerEmail;
        this.maxLoginFailuresPerIp = maxLoginFailuresPerIp;
        this.loginWindow = Duration.ofMinutes(loginWindowMinutes);
        this.loginBlock = Duration.ofMinutes(loginBlockMinutes);
        this.maxPublicFormRequestsPerIp = maxPublicFormRequestsPerIp;
        this.publicFormWindow = Duration.ofMinutes(publicFormWindowMinutes);
    }

    /** Recusa o login (429) enquanto o e-mail ou o IP estiverem bloqueados, mesmo com a senha certa. */
    public void checkLoginAllowed(String email, String clientIp) {
        if (!enabled) return;
        Instant now = clock.instant();
        Duration remaining = longest(
                remainingBlock(emailKey(email), now),
                remainingBlock(ipKey(clientIp), now));
        if (!remaining.isZero()) {
            throw new TooManyRequestsException(LOGIN_BLOCKED.formatted(minutes(remaining)), remaining);
        }
    }

    /**
     * Conta uma senha errada. Se esta falha atingir o limite, o bloqueio começa agora e a própria
     * tentativa já recebe 429, para o usuário saber que precisa esperar.
     */
    public void registerLoginFailure(String email, String clientIp) {
        if (!enabled) return;
        Instant now = clock.instant();
        boolean emailBlocked = hit(emailKey(email), maxLoginFailuresPerEmail, loginWindow, loginBlock, now);
        boolean ipBlocked = hit(ipKey(clientIp), maxLoginFailuresPerIp, loginWindow, loginBlock, now);
        if (emailBlocked || ipBlocked) {
            log.warn("Login bloqueado temporariamente após tentativas repetidas (e-mail bloqueado: {}, IP {} bloqueado: {}).",
                    emailBlocked, clientIp, ipBlocked);
            throw new TooManyRequestsException(LOGIN_BLOCKED.formatted(minutes(loginBlock)), loginBlock);
        }
    }

    /**
     * Login certo zera o contador do e-mail. O do IP continua, para que quem tem uma conta
     * válida não consiga "limpar" o IP entre tentativas em contas de outras pessoas.
     */
    public void registerLoginSuccess(String email) {
        counters.remove(emailKey(email));
    }

    /** Conta um envio de formulário público; o pedido acima do limite recebe 429. */
    public void checkPublicForm(PublicForm form, String clientIp) {
        if (!enabled) return;
        Instant now = clock.instant();
        String key = "form:" + form + ":" + normalizeIp(clientIp);
        Duration remaining = remainingBlock(key, now);
        if (!remaining.isZero()) {
            throw new TooManyRequestsException(PUBLIC_FORM_BLOCKED.formatted(minutes(remaining)), remaining);
        }
        // O pedido que atinge o limite ainda passa; o próximo é recusado até a janela terminar.
        hit(key, maxPublicFormRequestsPerIp, publicFormWindow, publicFormWindow, now);
    }

    private boolean hit(String key, int threshold, Duration window, Duration block, Instant now) {
        Counter updated = counters.compute(key, (ignored, current) -> {
            boolean restart = current == null || current.isExpiredAt(now);
            int count = restart ? 1 : current.count() + 1;
            Instant windowEndsAt = restart ? now.plus(window) : current.windowEndsAt();
            Instant blockedUntil = count >= threshold ? now.plus(block) : null;
            return new Counter(count, windowEndsAt, blockedUntil);
        });
        discardExpiredCountersIfTooMany(now);
        return updated.blockedUntil() != null;
    }

    private Duration remainingBlock(String key, Instant now) {
        Counter counter = counters.get(key);
        return counter != null && counter.isBlockedAt(now)
                ? Duration.between(now, counter.blockedUntil())
                : Duration.ZERO;
    }

    /** Evita que muitos e-mails ou IPs diferentes façam o mapa crescer sem limite. */
    private void discardExpiredCountersIfTooMany(Instant now) {
        if (counters.size() <= MAX_TRACKED_KEYS) return;
        counters.entrySet().removeIf(entry -> entry.getValue().isExpiredAt(now));
        if (counters.size() > MAX_TRACKED_KEYS) {
            log.warn("Limite de contadores de tentativas atingido; contadores descartados.");
            counters.clear();
        }
    }

    private static String emailKey(String email) {
        return "login:email:" + email;
    }

    private static String ipKey(String clientIp) {
        return "login:ip:" + normalizeIp(clientIp);
    }

    private static String normalizeIp(String clientIp) {
        return clientIp == null || clientIp.isBlank() ? "desconhecido" : clientIp;
    }

    private static Duration longest(Duration first, Duration second) {
        return first.compareTo(second) >= 0 ? first : second;
    }

    private static long minutes(Duration duration) {
        return Math.max(1, (duration.getSeconds() + 59) / 60);
    }
}
