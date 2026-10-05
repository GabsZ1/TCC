package br.com.salonhub.service.auth;

import br.com.salonhub.exception.TooManyRequestsException;
import br.com.salonhub.service.auth.AuthenticationRateLimiter.PublicForm;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Regras de tempo do bloqueio, com um relógio controlado pelo teste. */
class AuthenticationRateLimiterTests {

    private static final String EMAIL = "maria@limite.test";
    private static final String IP = "10.0.0.1";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-05T12:00:00Z"));
    private final AuthenticationRateLimiter limiter = new AuthenticationRateLimiter(
            clock, true, 5, 20, 15, 15, 10, 60);

    @Test
    void shouldReleaseTheEmailWhenTheBlockEnds() {
        failTimes(4);
        TooManyRequestsException blocked = null;
        try {
            limiter.registerLoginFailure(EMAIL, IP);
        } catch (TooManyRequestsException exception) {
            blocked = exception;
        }
        assertThat(blocked).isNotNull()
                .hasMessage("Muitas tentativas de login. Tente novamente em 15 minuto(s).");
        assertThat(blocked.getRetryAfter()).isEqualTo(Duration.ofMinutes(15));

        clock.advance(Duration.ofMinutes(10));
        assertThatThrownBy(() -> limiter.checkLoginAllowed(EMAIL, IP))
                .hasMessage("Muitas tentativas de login. Tente novamente em 5 minuto(s).");

        clock.advance(Duration.ofMinutes(5));
        assertThatCode(() -> limiter.checkLoginAllowed(EMAIL, IP)).doesNotThrowAnyException();
        // Depois do bloqueio, a contagem recomeça do zero.
        assertThatCode(() -> failTimes(4)).doesNotThrowAnyException();
    }

    @Test
    void shouldForgetOldFailuresWhenTheWindowEnds() {
        failTimes(4);
        clock.advance(Duration.ofMinutes(15));

        assertThatCode(() -> failTimes(4)).doesNotThrowAnyException();
        assertThatCode(() -> limiter.checkLoginAllowed(EMAIL, IP)).doesNotThrowAnyException();
    }

    @Test
    void shouldRoundTheRemainingTimeUpToWholeMinutes() {
        failTimes(4);
        assertThatThrownBy(() -> limiter.registerLoginFailure(EMAIL, IP));

        clock.advance(Duration.ofMinutes(14).plusSeconds(30));
        assertThatThrownBy(() -> limiter.checkLoginAllowed(EMAIL, IP))
                .hasMessage("Muitas tentativas de login. Tente novamente em 1 minuto(s).");
    }

    @Test
    void shouldCountEachPublicFormSeparately() {
        for (int request = 1; request <= 10; request++) {
            limiter.checkPublicForm(PublicForm.CLIENT_REGISTRATION, IP);
        }
        assertThatThrownBy(() -> limiter.checkPublicForm(PublicForm.CLIENT_REGISTRATION, IP))
                .hasMessage("Muitas solicitações feitas a partir desta rede. Tente novamente em 60 minuto(s).");
        assertThatCode(() -> limiter.checkPublicForm(PublicForm.PASSWORD_RECOVERY, IP))
                .doesNotThrowAnyException();

        clock.advance(Duration.ofMinutes(60));
        assertThatCode(() -> limiter.checkPublicForm(PublicForm.CLIENT_REGISTRATION, IP))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldDoNothingWhenDisabled() {
        AuthenticationRateLimiter disabled = new AuthenticationRateLimiter(clock, false, 1, 1, 15, 15, 1, 60);

        assertThatCode(() -> {
            for (int attempt = 0; attempt < 10; attempt++) {
                disabled.registerLoginFailure(EMAIL, IP);
                disabled.checkLoginAllowed(EMAIL, IP);
                disabled.checkPublicForm(PublicForm.CLIENT_REGISTRATION, IP);
            }
        }).doesNotThrowAnyException();
    }

    private void failTimes(int times) {
        for (int attempt = 0; attempt < times; attempt++) {
            limiter.registerLoginFailure(EMAIL, IP);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("America/Sao_Paulo");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
