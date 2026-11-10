package br.com.salonhub.exception;

/** Sinaliza uma operação incompatível com o estado atual do recurso. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
