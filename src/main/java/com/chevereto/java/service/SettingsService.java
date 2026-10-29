package com.chevereto.java.service;

import com.chevereto.java.entity.Setting;
import com.chevereto.java.repository.SettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/** 站点设置，存储于 chv_settings（与原版同表结构） */
@Service
public class SettingsService {

    /** 默认站点设置（首次启动写入数据库；crypt_salt 在初始化时随机生成） */
    public static final Map<String, String> DEFAULTS = new LinkedHashMap<>() {{
        put("website_name", "Java 图床");
        put("website_description", "基于 Spring Boot 的 Chevereto 兼容图床系统");
        put("default_timezone", "Asia/Shanghai");
        put("default_language", "zh-CN");
        put("guest_uploads", "true");
        put("upload_max_filesize_mb", "20");
        put("thumbnail_size", "320");
        put("default_privacy", "public");
        put("upload_api_enabled", "true");
        put("id_padding", "5000");
        // ---- 水印（与原版同名同默认值，见原版安装器）----
        put("watermark_enable", "0");
        // 留空 = 用 jar 内置水印；填绝对路径 = 用自定义水印图
        put("watermark_image", "");
        put("watermark_position", "center center");
        put("watermark_margin", "10");
        put("watermark_opacity", "50");
        put("watermark_enable_guest", "1");
        put("watermark_enable_user", "1");
        put("watermark_enable_admin", "1");
        put("watermark_target_min_width", "100");
        put("watermark_target_min_height", "100");
        put("watermark_percentage", "4");
        put("watermark_enable_file_gif", "0");
        // ---- 邮箱（键名与默认值对齐原版 install，见 chv_settings email_*）----
        put("email_mode", "smtp");                       // smtp | mail（原版 PHP mail()）
        put("email_smtp_server", "");
        put("email_smtp_server_port", "587");            // 原版可选 25 / 80 / 465 / 587
        put("email_smtp_server_security", "tls");        // tls | ssl | unsecured
        put("email_smtp_server_username", "");
        put("email_smtp_server_password", "");
        put("email_from_email", "noreply@example.com");
        put("email_from_name", "Java 图床");
        put("email_incoming_email", "");                 // 洪水通知等寄给这个地址
        put("require_user_email_confirmation", "0");     // 注册需邮箱激活（默认关，避免配好邮件前锁死）
        put("notify_user_signups", "0");                 // 新用户注册通知管理员
        // ---- 洪水保护（原版 flood_uploads_*，默认值完全一致）----
        put("flood_uploads_protection", "1");
        put("flood_uploads_notify", "0");
        put("flood_uploads_minute", "50");
        put("flood_uploads_hour", "500");
        put("flood_uploads_day", "1000");
        put("flood_uploads_week", "5000");
        put("flood_uploads_month", "10000");
        // ---- reCAPTCHA（洪水保护配套：失败次数达阈值后要求验证码）----
        put("recaptcha", "0");
        put("recaptcha_threshold", "5");
        put("recaptcha_public_key", "");
        put("recaptcha_private_key", "");
        // ---- NSFW / 内容审核（原版键名与默认值完全一致）----
        put("show_nsfw_in_listings", "0");
        put("show_banners_in_nsfw", "0");
        put("show_nsfw_in_random_mode", "0");
        put("theme_nsfw_upload_checkbox", "1");
        put("theme_nsfw_blur", "0");
        put("image_lock_nsfw_editing", "0");
        put("moderatecontent", "0");
        put("moderatecontent_key", "");
        put("moderatecontent_block_rating", "a");        // '' | a | t
        put("moderatecontent_flag_nsfw", "a");           // 0 | a | t
        put("moderatecontent_auto_approve", "0");
        put("moderate_uploads", "");                     // '' | guest | all
        // ---- 内容开关 ----
        put("enable_uploads", "1");
        put("enable_signups", "1");
        // 允许重复上传：0 = 开启查重（同一 IP 24 小时内同内容拒绝上传，原版默认 0）
        put("enable_duplicate_uploads", "0");
    }};

    private final SettingRepository settingRepository;

    public SettingsService(SettingRepository settingRepository) {
        this.settingRepository = settingRepository;
    }

    @Transactional(readOnly = true)
    public String getRaw(String key) {
        return settingRepository.findBySettingName(key).map(Setting::getSettingValue).orElse(null);
    }

    @Transactional(readOnly = true)
    public String get(String key) {
        String raw = getRaw(key);
        return raw != null ? raw : DEFAULTS.getOrDefault(key, "");
    }

    /** 兼容原版布尔写法："1"/"true" 为真（chv_settings 存 1/0） */
    public boolean getBool(String key) {
        String v = get(key);
        return "1".equals(v) || "true".equalsIgnoreCase(v) || "on".equalsIgnoreCase(v);
    }

    public int getInt(String key, int def) {
        try {
            return Integer.parseInt(get(key));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public long getLong(String key, long def) {
        try {
            return Long.parseLong(get(key));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    @Transactional
    public void set(String key, String value) {
        Setting s = settingRepository.findBySettingName(key).orElseGet(Setting::new);
        s.setSettingName(key);
        s.setSettingValue(value);
        s.setSettingDefault(DEFAULTS.getOrDefault(key, ""));
        String dv = DEFAULTS.getOrDefault(key, "");
        s.setSettingTypeset("true".equalsIgnoreCase(dv) || "false".equalsIgnoreCase(dv)
                || "0".equals(dv) || "1".equals(dv) ? "bool" : "string");
        settingRepository.save(s);
    }

    @Transactional
    public void setAll(Map<String, String> values) {
        values.forEach(this::set);
    }

    @Transactional(readOnly = true)
    public Map<String, String> all() {
        Map<String, String> map = new LinkedHashMap<>(DEFAULTS);
        settingRepository.findAll().forEach(s -> map.put(s.getSettingName(), s.getSettingValue()));
        return map;
    }
}
