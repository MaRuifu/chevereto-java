package com.chevereto.java.storage;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.region.Region;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.chevereto.java.entity.Storage;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;

/** 腾讯云 COS 存储驱动（storage_region=地域，storage_key/secret=SecretId/SecretKey） */
@Component
public class CosStorageDriver implements StorageDriver {

    @Override
    public void store(Storage storage, String relativePath, byte[] data) {
        COSClient client = buildClient(storage);
        try {
            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentLength(data.length);
            client.putObject(new PutObjectRequest(storage.getStorageBucket(), fullKey(storage, relativePath),
                    new ByteArrayInputStream(data), meta));
        } catch (CosClientException e) {
            throw new StorageException("腾讯云 COS 上传失败: " + e.getMessage(), e);
        } finally {
            client.shutdown();
        }
    }

    @Override
    public void delete(Storage storage, String relativePath) {
        COSClient client = buildClient(storage);
        try {
            client.deleteObject(storage.getStorageBucket(), fullKey(storage, relativePath));
        } catch (CosClientException e) {
            throw new StorageException("腾讯云 COS 删除失败: " + e.getMessage(), e);
        } finally {
            client.shutdown();
        }
    }

    private COSClient buildClient(Storage storage) {
        if (storage.getStorageRegion() == null || storage.getStorageRegion().isBlank()) {
            throw new StorageException("COS 存储缺少 region（storage_region）配置（如 ap-guangzhou）");
        }
        COSCredentials cred = new BasicCOSCredentials(storage.getStorageKey(), storage.getStorageSecret());
        ClientConfig config = new ClientConfig(new Region(storage.getStorageRegion()));
        config.setHttpProtocol(HttpProtocol.https);
        return new COSClient(cred, config);
    }

    private String fullKey(Storage storage, String relativePath) {
        return relativePath;
    }
}
