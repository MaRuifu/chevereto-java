package com.chevereto.java.entity;


import java.time.LocalDateTime;

/**
 * 对应 Chevereto 原版表 chv_api_keys。
 * 与原版一致：api_key_hash 保存 Key 的 MD5；原样 Key 只在生成时展示一次。
 */
public class ApiKey {

    private Long apiKeyId;

    private Long apiKeyUserId;

    private String apiKeyName;

    private LocalDateTime apiKeyDateGmt = LocalDateTime.now();

    private String apiKeyHash;

    public Long getApiKeyId() { return apiKeyId; }
    public void setApiKeyId(Long apiKeyId) { this.apiKeyId = apiKeyId; }
    public Long getApiKeyUserId() { return apiKeyUserId; }
    public void setApiKeyUserId(Long apiKeyUserId) { this.apiKeyUserId = apiKeyUserId; }
    public String getApiKeyName() { return apiKeyName; }
    public void setApiKeyName(String apiKeyName) { this.apiKeyName = apiKeyName; }
    public LocalDateTime getApiKeyDateGmt() { return apiKeyDateGmt; }
    public void setApiKeyDateGmt(LocalDateTime apiKeyDateGmt) { this.apiKeyDateGmt = apiKeyDateGmt; }
    public String getApiKeyHash() { return apiKeyHash; }
    public void setApiKeyHash(String apiKeyHash) { this.apiKeyHash = apiKeyHash; }
}
