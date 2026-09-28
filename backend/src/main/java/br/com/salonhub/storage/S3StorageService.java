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
 * Armazenamento em um servidor compatível com S3 (no desenvolvimento, o SeaweedFS do
 * docker-compose; em produção pode ser AWS S3, Cloudflare R2 etc., só mudando a configuração).
 * Usa o cliente Java do MinIO, que é um cliente S3 genérico.
 * O cliente não abre conexão na inicialização: a aplicação sobe mesmo com o armazenamento
 * fora do ar, e só as operações de imagem falham. Se o bucket não existir, é criado no
 * primeiro envio.
 */
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3", matchIfMissing = true)
public class S3StorageService implements StorageService {

    /** Região fixa: evita a consulta de localização do bucket, que nem todo servidor S3 implementa. */
    private static final String REGION = "us-east-1";

    private final MinioClient client;
    private final String bucket;
    private volatile boolean bucketChecked;

    public S3StorageService(
            @Value("${app.storage.s3.endpoint}") String endpoint,
            @Value("${app.storage.s3.access-key}") String accessKey,
            @Value("${app.storage.s3.secret-key}") String secretKey,
            @Value("${app.storage.s3.bucket}") String bucket) {
        this.client = MinioClient.builder().endpoint(endpoint).region(REGION).credentials(accessKey, secretKey).build();
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
            throw new StorageException("Falha ao gravar o arquivo " + key + " no armazenamento", exception);
        }
    }

    @Override
    public byte[] get(String key) {
        try (GetObjectResponse response = client.getObject(
                GetObjectArgs.builder().bucket(bucket).object(key).build())) {
            return response.readAllBytes();
        } catch (ErrorResponseException exception) {
            boolean notFound = "NoSuchKey".equals(exception.errorResponse().code());
            throw new StorageException("Falha ao ler o arquivo " + key + " no armazenamento", exception, notFound);
        } catch (Exception exception) {
            throw new StorageException("Falha ao ler o arquivo " + key + " no armazenamento", exception);
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception exception) {
            throw new StorageException("Falha ao remover o arquivo " + key + " no armazenamento", exception);
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
