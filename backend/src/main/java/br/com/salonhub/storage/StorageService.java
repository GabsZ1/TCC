package br.com.salonhub.storage;

/**
 * Abstração do armazenamento físico dos arquivos. A aplicação só conhece a chave
 * (storage_key); onde e como o arquivo é guardado fica a cargo da implementação.
 * Hoje: {@link S3StorageService} (padrão) e {@link InMemoryStorageService} (testes).
 */
public interface StorageService {

    void put(String key, byte[] content, String contentType);

    /** @throws StorageException com {@code notFound = true} se a chave não existir. */
    byte[] get(String key);

    /** Remove o arquivo; não falha se ele já não existir. */
    void delete(String key);
}
