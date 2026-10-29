package com.chevereto.java.entity;


/**
 * 对应 Chevereto 原版表 chv_storages（列结构与生产库完全一致，无扩展列）。
 *  - storage_service 可为 NULL：此时按 storage_api_id 在 chv_storage_apis 的 type 路由（生产库即此用法）
 *  - storage_url：对外访问基础 URL；LOCAL 留空走站内 /images/**
 *  - storage_server：LOCAL = 磁盘根目录；S3/OSS = endpoint；FTP/SFTP = 主机
 */
public class Storage {

    private Long storageId;

    private Long storageApiId;

    private String storageName;

    private String storageService;

    private String storageUrl;

    private String storageBucket;

    private String storageRegion;

    private String storageServer;

    private String storageAccountId;

    private String storageAccountName;

    private String storageKey;

    private String storageSecret;

    private boolean storageIsHttps = true;

    private boolean storageIsActive = true;

    private Long storageCapacity;

    private Long storageSpaceUsed = 0L;

    public Long getStorageId() { return storageId; }
    public void setStorageId(Long storageId) { this.storageId = storageId; }
    public Long getStorageApiId() { return storageApiId; }
    public void setStorageApiId(Long storageApiId) { this.storageApiId = storageApiId; }
    public String getStorageName() { return storageName; }
    public void setStorageName(String storageName) { this.storageName = storageName; }
    public String getStorageService() { return storageService; }
    public void setStorageService(String storageService) { this.storageService = storageService; }
    public String getStorageUrl() { return storageUrl; }
    public void setStorageUrl(String storageUrl) { this.storageUrl = storageUrl; }
    public String getStorageBucket() { return storageBucket; }
    public void setStorageBucket(String storageBucket) { this.storageBucket = storageBucket; }
    public String getStorageRegion() { return storageRegion; }
    public void setStorageRegion(String storageRegion) { this.storageRegion = storageRegion; }
    public String getStorageServer() { return storageServer; }
    public void setStorageServer(String storageServer) { this.storageServer = storageServer; }
    public String getStorageAccountId() { return storageAccountId; }
    public void setStorageAccountId(String storageAccountId) { this.storageAccountId = storageAccountId; }
    public String getStorageAccountName() { return storageAccountName; }
    public void setStorageAccountName(String storageAccountName) { this.storageAccountName = storageAccountName; }
    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }
    public String getStorageSecret() { return storageSecret; }
    public void setStorageSecret(String storageSecret) { this.storageSecret = storageSecret; }
    public boolean isStorageIsHttps() { return storageIsHttps; }
    public void setStorageIsHttps(boolean storageIsHttps) { this.storageIsHttps = storageIsHttps; }
    public boolean isStorageIsActive() { return storageIsActive; }
    public void setStorageIsActive(boolean storageIsActive) { this.storageIsActive = storageIsActive; }
    public Long getStorageCapacity() { return storageCapacity; }
    public void setStorageCapacity(Long storageCapacity) { this.storageCapacity = storageCapacity; }
    public Long getStorageSpaceUsed() { return storageSpaceUsed; }
    public void setStorageSpaceUsed(Long storageSpaceUsed) { this.storageSpaceUsed = storageSpaceUsed; }
}
