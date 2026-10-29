package com.chevereto.java.entity;


/** 对应 Chevereto 原版表 chv_storage_apis（仅 3 列，与生产库结构一致） */
public class StorageApi {

    private Long storageApiId;

    private String storageApiName;

    private String storageApiType;

    public Long getStorageApiId() { return storageApiId; }
    public void setStorageApiId(Long storageApiId) { this.storageApiId = storageApiId; }
    public String getStorageApiName() { return storageApiName; }
    public void setStorageApiName(String storageApiName) { this.storageApiName = storageApiName; }
    public String getStorageApiType() { return storageApiType; }
    public void setStorageApiType(String storageApiType) { this.storageApiType = storageApiType; }
}
