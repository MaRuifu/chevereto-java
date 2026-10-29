package com.chevereto.java.entity;


import java.time.LocalDateTime;

/** 对应 Chevereto 原版表 chv_ip_bans（仅 IP 封禁，与原版一致） */
public class IpBan {

    private Long ipBanId;

    private LocalDateTime ipBanDate = LocalDateTime.now();

    private LocalDateTime ipBanDateGmt = LocalDateTime.now();

    private LocalDateTime ipBanExpires;

    private String ipBanIp;

    private String ipBanMessage;

    public Long getIpBanId() { return ipBanId; }
    public void setIpBanId(Long ipBanId) { this.ipBanId = ipBanId; }
    public LocalDateTime getIpBanDate() { return ipBanDate; }
    public void setIpBanDate(LocalDateTime ipBanDate) { this.ipBanDate = ipBanDate; }
    public LocalDateTime getIpBanDateGmt() { return ipBanDateGmt; }
    public void setIpBanDateGmt(LocalDateTime ipBanDateGmt) { this.ipBanDateGmt = ipBanDateGmt; }
    public LocalDateTime getIpBanExpires() { return ipBanExpires; }
    public void setIpBanExpires(LocalDateTime ipBanExpires) { this.ipBanExpires = ipBanExpires; }
    public String getIpBanIp() { return ipBanIp; }
    public void setIpBanIp(String ipBanIp) { this.ipBanIp = ipBanIp; }
    public String getIpBanMessage() { return ipBanMessage; }
    public void setIpBanMessage(String ipBanMessage) { this.ipBanMessage = ipBanMessage; }
}
