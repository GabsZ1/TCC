package br.com.salonhub.exception;

/** Indica falha temporária no serviço externo usado para localizar um CEP. */
public class CepLookupUnavailableException extends RuntimeException {
    public CepLookupUnavailableException(Throwable cause) {
        super(cause);
    }
}
