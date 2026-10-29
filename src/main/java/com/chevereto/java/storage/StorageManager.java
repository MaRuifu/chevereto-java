package com.chevereto.java.storage;

import com.chevereto.java.entity.Storage;
import com.chevereto.java.repository.StorageApiRepository;
import com.chevereto.java.repository.StorageRepository;
import org.springframework.stereotype.Component;

/**
 * 存储统一入口：按 chv_storages 配置路由到具体驱动，并生成对外 URL。
 * 列结构与生产库完全一致（无扩展列）：
 *  - storage_service 可为 NULL：此时按 storage_api_id 在 chv_storage_apis 的 type 路由
 *    （生产库 COS 即此用法：api_id=9 s3compatible + COS 的 S3 兼容端点）；两者都取不到时视为 local
 *  - storage_url = 对外基础 URL（留空时 LOCAL 走站内 /images/**，OSS/COS 生成默认直链）
 *  - storage_server = LOCAL 磁盘根目录 / S3、OSS endpoint / FTP、SFTP 主机（host:端口）
 *  - storage_region = 区域，storage_bucket = 桶名
 */
@Component
public class StorageManager {

    private final LocalStorageDriver localStorageDriver;
    private final S3StorageDriver s3StorageDriver;
    private final OssStorageDriver ossStorageDriver;
    private final CosStorageDriver cosStorageDriver;
    private final FtpStorageDriver ftpStorageDriver;
    private final SftpStorageDriver sftpStorageDriver;
    private final StorageRepository storageRepository;
    private final StorageApiRepository storageApiRepository;

    public StorageManager(LocalStorageDriver localStorageDriver,
                          S3StorageDriver s3StorageDriver,
                          OssStorageDriver ossStorageDriver,
                          CosStorageDriver cosStorageDriver,
                          FtpStorageDriver ftpStorageDriver,
                          SftpStorageDriver sftpStorageDriver,
                          StorageRepository storageRepository,
                          StorageApiRepository storageApiRepository) {
        this.localStorageDriver = localStorageDriver;
        this.s3StorageDriver = s3StorageDriver;
        this.ossStorageDriver = ossStorageDriver;
        this.cosStorageDriver = cosStorageDriver;
        this.ftpStorageDriver = ftpStorageDriver;
        this.sftpStorageDriver = sftpStorageDriver;
        this.storageRepository = storageRepository;
        this.storageApiRepository = storageApiRepository;
    }

    public void store(Storage storage, String relativePath, byte[] data) {
        driver(storage).store(storage, relativePath, data);
    }

    public void delete(Storage storage, String relativePath) {
        try {
            driver(storage).delete(storage, relativePath);
        } catch (Exception ignored) {
            // 删除失败不阻断业务
        }
    }

    /** 对外访问 URL；LOCAL 且未配 storageUrl 时走站内 /images/**（与原版直链一致） */
    public String url(Storage storage, String relativePath) {
        String base = storage.getStorageUrl();
        String service = resolveService(storage);
        if (base != null && !base.isBlank()) {
            return base.replaceAll("/+$", "") + "/" + relativePath;
        }
        if ("local".equals(service)) {
            return "/images/" + relativePath;
        }
        return switch (service) {
            case "s3", "s3compatible" -> String.format("https://%s.%s/%s", storage.getStorageBucket(),
                    hostOrDefault(storage.getStorageServer(), "s3.amazonaws.com"), relativePath);
            case "oss" -> String.format("https://%s.%s/%s", storage.getStorageBucket(),
                    hostOrDefault(storage.getStorageServer(), "aliyuncs.com"), relativePath);
            case "cos" -> String.format("https://%s.cos.%s.myqcloud.com/%s", storage.getStorageBucket(),
                    storage.getStorageRegion(), relativePath);
            case "ftp" -> String.format("ftp://%s/%s",
                    hostPort(storage.getStorageServer(), 21), relativePath);
            case "sftp" -> String.format("sftp://%s/%s",
                    hostPort(storage.getStorageServer(), 22), relativePath);
            default -> "/images/" + relativePath;
        };
    }

    /**
     * 该存储行实际使用的驱动标识。
     * 原版兼容：storage_service 为 NULL（生产库常见，用 api 行的 type 表达驱动）时按 storage_api_id 解析。
     */
    public String resolveService(Storage storage) {
        if (storage.getStorageService() != null && !storage.getStorageService().isBlank()) {
            return storage.getStorageService().trim().toLowerCase();
        }
        if (storage.getStorageApiId() != null) {
            String type = storageApiRepository.findById(storage.getStorageApiId())
                    .map(StorageApi -> StorageApi.getStorageApiType())
                    .orElse(null);
            if (type != null && !type.isBlank()) {
                return type.trim().toLowerCase();
            }
        }
        return "local";
    }

    /**
     * 默认存储（新上传的目标）：取第一个启用的存储（storage_id 升序）。
     * 兼容：无任何启用存储时回退本地行；生产库可能连本地行都没有（只配了停用的 COS），
     * 此时构造「隐式本地存储」落回 app.local-storage-root，保证上传可用。
     */
    public Storage defaultStorage() {
        var all = storageRepository.findAll();
        return all.stream()
                .filter(Storage::isStorageIsActive)
                .findFirst()
                .or(() -> all.stream().filter(this::isLocalRow).findFirst())
                .orElseGet(StorageManager::implicitLocalStorage);
    }

    /**
     * 迁移老数据（image_storage_id 为 NULL）的渲染兜底：固定优先「本地存储」行，
     * 老图实际都在本地磁盘上，切换默认存储不影响老图直链；无本地行时同样用隐式本地存储。
     */
    public Storage migratedStorage() {
        var all = storageRepository.findAll();
        return all.stream()
                .filter(this::isLocalRow)
                .findFirst()
                .orElseGet(StorageManager::implicitLocalStorage);
    }

    /** 本地存储行：service 为 local，或 service 为 NULL 且 api type 为 local */
    private boolean isLocalRow(Storage s) {
        return "local".equals(resolveService(s));
    }

    /** 隐式本地存储：无 id，LocalStorageDriver 会把空 server 回退到 app.local-storage-root */
    public static Storage implicitLocalStorage() {
        Storage s = new Storage();
        s.setStorageName("本地存储（隐式）");
        s.setStorageService("local");
        s.setStorageUrl("");
        s.setStorageIsActive(true);
        return s;
    }

    private StorageDriver driver(Storage storage) {
        return switch (resolveService(storage)) {
            case "local" -> localStorageDriver;
            // 原版 chv_storage_apis 里的 s3compatible 同样走 S3 兼容协议
            case "s3", "s3compatible" -> s3StorageDriver;
            case "oss" -> ossStorageDriver;
            case "cos" -> cosStorageDriver;
            case "ftp" -> ftpStorageDriver;
            case "sftp" -> sftpStorageDriver;
            default -> throw new StorageException("不支持的存储驱动: " + storage.getStorageService());
        };
    }

    /** endpoint 转主机名，空则用驱动各自的默认 host */
    private String hostOrDefault(String endpoint, String defaultHost) {
        if (endpoint == null || endpoint.isBlank()) return defaultHost;
        String host = endpoint.replaceAll("^https?://", "").replaceAll("/+$", "");
        return host.isBlank() ? defaultHost : host;
    }

    /** host 或 host:port（去协议头），用于 ftp:// / sftp:// 直链 */
    private String hostPort(String server, int defaultPort) {
        String s = server == null ? "" : server.trim();
        s = s.replaceFirst("^[a-z]+://", "").replaceAll("/+$", "");
        int colon = s.lastIndexOf(':');
        if (colon > 0) return s;
        return s + ":" + defaultPort;
    }
}
