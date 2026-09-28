package br.com.salonhub.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;

/**
 * Armazenamento no MinIO (API S3). O cliente não abre conexão na inicialização:
 * a aplicação sobe mesmo com o MinIO fora do ar, e só as operações de imagem falham.
 * O bucket é criado pelo container "minio-setup" do docker-compose; se não existir,
 * é criado aqui no primeiro envio.
 */
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "minio", matchIfMissing = true)
public class MinioStorageService implements StorageService {

    private final MinioClient client;
    private final String bucket;
    private volatile boolean bucketChecked;

    public MinioStorageService(
            @Value("${app.storage.minio.endpoint}") String endpoint,
            @Value("${app.storage.minio.access-key}") String accessKey,
            @Value("${app.storage.minio.secret-key}") String secretKey,
            @Value("${app.storage.minio.bucket}") String bucket) {
        this.client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
        this.bucket = bucket;
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        try {
            ensureBucket();
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(new ByteArrayInputStream(content), content.length, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception exception) {
            throw new StorageException("Falha ao gravar o arquivo " + key + " no MinIO", exception);
        }
    }

    @Override
    public byte[] get(String key) {
        try (GetObjectResponse response = client.getObject(
                GetObjectArgs.builder().bucket(bucket).object(key).build())) {
            return response.readAllBytes();
        } catch (ErrorResponseException exception) {
            boolean notFound = "NoSuchKey".equals(exception.errorResponse().code());
            throw new StorageException("Falha ao ler o arquivo " + key + " no MinIO", exception, notFound);
        } catch (Exception exception) {
            throw new StorageException("Falha ao ler o arquivo " + key + " no MinIO", exception);
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception exception) {
            throw new StorageException("Falha ao remover o arquivo " + key + " no MinIO", exception);
        }
    }

    private void ensureBucket() throws Exception {
        if (bucketChecked) {
            return;
        }
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
        bucketChecked = true;
    }
}
