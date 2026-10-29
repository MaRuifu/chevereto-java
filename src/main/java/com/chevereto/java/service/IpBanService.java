package com.chevereto.java.service;

import com.chevereto.java.entity.IpBan;
import com.chevereto.java.repository.IpBanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** IP 封禁（对应原版 chv_ip_bans，仅 IP 维度，与原版行为一致） */
@Service
public class IpBanService {

    private final IpBanRepository ipBanRepository;

    public IpBanService(IpBanRepository ipBanRepository) {
        this.ipBanRepository = ipBanRepository;
    }

    /** 是否被封禁（含过期时间判断） */
    public boolean isBanned(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        return ipBanRepository.findAll().stream().anyMatch(b ->
                b.getIpBanIp().equals(ip) && (b.getIpBanExpires() == null || b.getIpBanExpires().isAfter(LocalDateTime.now())));
    }

    public List<IpBan> all() {
        return ipBanRepository.findAll();
    }

    @Transactional
    public void add(String ip, String message, LocalDateTime expires) {
        if (ip == null || ip.isBlank()) {
            throw new IllegalArgumentException("IP 不能为空");
        }
        if (ipBanRepository.existsByIpBanIp(ip.trim())) {
            throw new IllegalArgumentException("该 IP 已在封禁列表中");
        }
        IpBan ban = new IpBan();
        ban.setIpBanIp(ip.trim());
        ban.setIpBanMessage(message);
        ban.setIpBanExpires(expires);
        ipBanRepository.save(ban);
    }

    @Transactional
    public void remove(Long id) {
        ipBanRepository.deleteById(id);
    }
}
