package com.chevereto.java.entity;


import java.time.LocalDateTime;

/** 对应 Chevereto 原版表 chv_login_passwords（密码哈希单独存表，与原版一致） */
public class LoginPassword {

    private Long loginPasswordId;

    private Long loginPasswordUserId;

    private LocalDateTime loginPasswordDateGmt = LocalDateTime.now();

    private String loginPasswordHash;

    public Long getLoginPasswordId() { return loginPasswordId; }
    public void setLoginPasswordId(Long loginPasswordId) { this.loginPasswordId = loginPasswordId; }
    public Long getLoginPasswordUserId() { return loginPasswordUserId; }
    public void setLoginPasswordUserId(Long loginPasswordUserId) { this.loginPasswordUserId = loginPasswordUserId; }
    public LocalDateTime getLoginPasswordDateGmt() { return loginPasswordDateGmt; }
    public void setLoginPasswordDateGmt(LocalDateTime loginPasswordDateGmt) { this.loginPasswordDateGmt = loginPasswordDateGmt; }
    public String getLoginPasswordHash() { return loginPasswordHash; }
    public void setLoginPasswordHash(String loginPasswordHash) { this.loginPasswordHash = loginPasswordHash; }
}
