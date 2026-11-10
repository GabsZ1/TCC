package br.com.salonhub.exception;

/** Sinaliza dados de entrada inválidos para a operação solicitada. */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
