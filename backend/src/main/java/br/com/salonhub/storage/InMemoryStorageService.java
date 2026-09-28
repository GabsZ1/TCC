package br.com.salonhub.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Armazenamento em memória, para testes automatizados (app.storage.type=memory). */
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "memory")
public class InMemoryStorageService implements StorageService {

    private final Map<String, byte[]> files = new ConcurrentHashMap<>();

    @Override
    public void put(String key, byte[] content, String contentType) {
        files.put(key, content.clone());
    }

    @Override
    public byte[] get(String key) {
        byte[] content = files.get(key);
        if (content == null) {
            throw new StorageException("Arquivo não encontrado: " + key, null, true);
        }
        return content.clone();
    }

    @Override
    public void delete(String key) {
        files.remove(key);
    }

    public Set<String> keys() {
        return Set.copyOf(files.keySet());
    }
}
