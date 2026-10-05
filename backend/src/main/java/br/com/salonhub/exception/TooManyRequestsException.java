package br.com.salonhub.exception;

import java.time.Duration;

public class TooManyRequestsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyRequestsException(String message) {
        this(message, null);
    }

    /** {@code retryAfter} vira o cabeçalho HTTP {@code Retry-After} da resposta 429. */
    public TooManyRequestsException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
