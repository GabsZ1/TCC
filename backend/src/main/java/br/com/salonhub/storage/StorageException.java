package br.com.salonhub.storage;

/** Falha ao acessar o armazenamento de arquivos (ex.: servidor S3 fora do ar). */
public class StorageException extends RuntimeException {

    private final boolean notFound;

    public StorageException(String message, Throwable cause) {
        this(message, cause, false);
    }

    public StorageException(String message, Throwable cause, boolean notFound) {
        super(message, cause);
        this.notFound = notFound;
    }

    public boolean isNotFound() {
        return notFound;
    }
}
