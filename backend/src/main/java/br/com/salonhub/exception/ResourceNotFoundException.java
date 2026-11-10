package br.com.salonhub.exception;

/** Sinaliza que o recurso não existe no escopo acessível ao usuário. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
