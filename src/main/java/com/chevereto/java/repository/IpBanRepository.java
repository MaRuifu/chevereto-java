package com.chevereto.java.repository;

import com.chevereto.java.entity.IpBan;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

/** chv_ip_bans —— MyBatis 注解 SQL（无 XML） */
@Mapper
public interface IpBanRepository {

    String COLS = "ip_ban_id, ip_ban_date, ip_ban_date_gmt, ip_ban_expires, ip_ban_ip, ip_ban_message";

    @Select("SELECT " + COLS + " FROM chv_ip_bans WHERE ip_ban_id = #{id}")
    Optional<IpBan> findById(Long id);

    @Select("SELECT " + COLS + " FROM chv_ip_bans ORDER BY ip_ban_id DESC")
    List<IpBan> findAll();

    @Select("SELECT COUNT(*) FROM chv_ip_bans WHERE ip_ban_ip = #{ip}")
    boolean existsByIpBanIp(String ip);

    @Insert("INSERT INTO chv_ip_bans "
            + "(ip_ban_date, ip_ban_date_gmt, ip_ban_expires, ip_ban_ip, ip_ban_message) "
            + "VALUES (#{ipBanDate}, #{ipBanDateGmt}, #{ipBanExpires}, #{ipBanIp}, #{ipBanMessage})")
    @Options(useGeneratedKeys = true, keyProperty = "ipBanId")
    int insert(IpBan ipBan);

    default IpBan save(IpBan ipBan) {
        if (ipBan.getIpBanId() == null) {
            insert(ipBan);
        }
        return ipBan;
    }

    @Delete("DELETE FROM chv_ip_bans WHERE ip_ban_id = #{id}")
    int deleteById(Long id);

    default void delete(IpBan ipBan) {
        deleteById(ipBan.getIpBanId());
    }
}
