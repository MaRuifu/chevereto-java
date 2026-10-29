package com.chevereto.java.storage;

import com.chevereto.java.entity.Storage;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

/**
 * S3 兼容存储驱动（AWS S3 / MinIO / R2 等）。
 * 映射：storage_bucket=桶，storage_server=endpoint（可空），storage_region=区域，
 * storage_key/storage_secret=凭证。
 */
@Component
public class S3StorageDriver implements StorageDriver {

    private S3Client buildClient(Storage storage) {
        String endpoint = storage.getStorageServer();
        return S3Client.builder()
                .region(Region.of(storage.getStorageRegion() == null || storage.getStorageRegion().isBlank()
                        ? "us-east-1" : storage.getStorageRegion()))
                .endpointOverride(endpoint == null || endpoint.isBlank() ? null : URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(storage.getStorageKey(), storage.getStorageSecret())))
                .serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    @Override
    public void store(Storage storage, String relativePath, byte[] data) {
        S3Client client = buildClient(storage);
        try {
            client.putObject(b -> b.bucket(storage.getStorageBucket()).key(fullKey(storage, relativePath)).build(),
                    RequestBody.fromBytes(data));
        } catch (Exception e) {
            throw new StorageException("S3 上传失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(Storage storage, String relativePath) {
        S3Client client = buildClient(storage);
        try {
            client.deleteObject(b -> b.bucket(storage.getStorageBucket()).key(fullKey(storage, relativePath)).build());
        } catch (Exception e) {
            throw new StorageException("S3 删除失败: " + e.getMessage(), e);
        }
    }

    private String fullKey(Storage storage, String relativePath) {
        return relativePath;
    }
}
