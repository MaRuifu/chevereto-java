package com.chevereto.java.storage;

import com.chevereto.java.entity.Storage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** 本地磁盘存储驱动（chv_storages.storage_server = 磁盘根目录） */
@Component
public class LocalStorageDriver implements StorageDriver {

    @Value("${app.local-storage-root:./data/uploads}")
    private String defaultRoot;

    private Path resolveRoot(Storage storage) {
        String root = (storage.getStorageServer() == null || storage.getStorageServer().isBlank())
                ? defaultRoot : storage.getStorageServer();
        Path p = Paths.get(root).toAbsolutePath().normalize();
        // 迁移兼容：存储行里配的目录在磁盘上不存在时，回退到 app.local-storage-root
        // （如 Java 初始化器写入的 ./data/uploads，而真实图片在迁移指定的目录）
        if (!Files.isDirectory(p) && Files.isDirectory(Paths.get(defaultRoot).toAbsolutePath().normalize())) {
            return Paths.get(defaultRoot).toAbsolutePath().normalize();
        }
        return p;
    }

    @Override
    public void store(Storage storage, String relativePath, byte[] data) {
        try {
            Path root = resolveRoot(storage);
            Path target = root.resolve(relativePath).normalize();
            if (!target.startsWith(root)) {
                throw new IllegalArgumentException("非法存储路径: " + relativePath);
            }
            Files.createDirectories(target.getParent());
            Files.write(target, data);
        } catch (IOException e) {
            throw new StorageException("写入本地文件失败: " + relativePath, e);
        }
    }

    @Override
    public void delete(Storage storage, String relativePath) {
        try {
            Files.deleteIfExists(resolveRoot(storage).resolve(relativePath).normalize());
        } catch (IOException e) {
            throw new StorageException("删除本地文件失败: " + relativePath, e);
        }
    }

    public Path resolve(Storage storage, String relativePath) {
        return resolveRoot(storage).resolve(relativePath).normalize();
    }
}
