package com.chevereto.java.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.chevereto.java.entity.Storage;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;

/** 阿里云 OSS 存储驱动（storage_server=endpoint） */
@Component
public class OssStorageDriver implements StorageDriver {

    @Override
    public void store(Storage storage, String relativePath, byte[] data) {
        OSS client = buildClient(storage);
        try {
            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentLength(data.length);
            client.putObject(storage.getStorageBucket(), fullKey(storage, relativePath),
                    new ByteArrayInputStream(data), meta);
        } catch (Exception e) {
            throw new StorageException("阿里云 OSS 上传失败: " + e.getMessage(), e);
        } finally {
            client.shutdown();
        }
    }

    @Override
    public void delete(Storage storage, String relativePath) {
        OSS client = buildClient(storage);
        try {
            client.deleteObject(storage.getStorageBucket(), fullKey(storage, relativePath));
        } catch (Exception e) {
            throw new StorageException("阿里云 OSS 删除失败: " + e.getMessage(), e);
        } finally {
            client.shutdown();
        }
    }

    private OSS buildClient(Storage storage) {
        String endpoint = storage.getStorageServer();
        if (endpoint == null || endpoint.isBlank()) {
            throw new StorageException("OSS 存储缺少 endpoint（storage_server）配置");
        }
        return new OSSClientBuilder().build(endpoint, storage.getStorageKey(), storage.getStorageSecret());
    }

    private String fullKey(Storage storage, String relativePath) {
        return relativePath;
    }
}
