package br.com.salonhub.exception;

/** Sinaliza falha de autenticação ou sessão inválida. */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
