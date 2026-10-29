package com.chevereto.java.service;

import com.chevereto.java.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 洪水保护（原版 Upload::handleFlood / web.php 登录锁定 的等价实现）。
 *
 * <p>三套独立机制，与原版一一对应：
 * <ol>
 *   <li><b>上传洪水</b>：同一 IP 在 5 个滚动窗口内的上传数达到阈值即拦截；管理员豁免；
 *       阈值读 chv_settings 的 flood_uploads_*，填 0 表示该窗口不限制</li>
 *   <li><b>登录/注册失败限流</b>：同一 IP 最近 24 小时「登录+注册」失败数
 *       &gt; 25（原版 LimitConfig 硬编码）直接 403；成功登录后清理该 IP+用户的失败记录</li>
 *   <li><b>注册洪水</b>：同一注册 IP 2 天内未激活账号 &gt; 5 时拒绝</li>
 * </ol>
 *
 * <p>与 PHP 版的差异（有意为之）：
 * <ul>
 *   <li>IP 一律参数化绑定，原版是字符串拼接（存在注入面）</li>
 *   <li>通知去重用内存缓存 + 窗口 TTL，原版用 session（多实例下会重复发信）</li>
 *   <li>业务码 130 映射为 HTTP 400（原版内部端点返回 500，属实现缺陷）</li>
 * </ul>
 */
@Service
public class FloodGuard {

    private static final Logger log = LoggerFactory.getLogger(FloodGuard.class);

    /** 原版 LimitConfig::invalidRequestsPerDay —— 硬编码，不在设置里 */
    public static final int INVALID_REQUESTS_PER_DAY = 25;
    /** 原版 User::insert 注册洪水：2 天 / 5 个未激活账号 */
    public static final int SIGNUP_LIMIT = 5;

    private static final List<String> WINDOWS = List.of("minute", "hour", "day", "week", "month");
    private static final Map<String, String> WINDOW_CN = Map.of(
            "minute", "分钟", "hour", "小时", "day", "天", "week", "周", "month", "月");

    private final SettingsService settingsService;
    private final JdbcTemplate jdbc;
    private final MailService mailService;
    private final RequestLogService requestLogService;

    /** 窗口名 -> 下次可再次通知的时间戳（毫秒） */
    private final Map<String, Long> notifiedAt = new ConcurrentHashMap<>();

    public FloodGuard(SettingsService settingsService, JdbcTemplate jdbc,
                      MailService mailService, RequestLogService requestLogService) {
        this.settingsService = settingsService;
        this.jdbc = jdbc;
        this.mailService = mailService;
        this.requestLogService = requestLogService;
    }

    // ---------------- 机制 1：上传洪水 ----------------

    /**
     * 上传前检查；命中则抛出带友好文案的异常，由调用方转成 400。
     */
    public void assertUploadAllowed(User uploader, String ip) {
        if (!settingsService.getBool("flood_uploads_protection")) {
            return;
        }
        if (uploader != null && uploader.isUserIsAdmin()) {
            return;
        }
        Map<String, Long> counts = countUploadsByIp(ip);
        for (String w : WINDOWS) {
            int limit = settingsService.getInt("flood_uploads_" + w, 0);
            if (limit > 0 && counts.getOrDefault(w, 0L) >= limit) {
                notifyOnce(w, ip, counts);
                throw new IllegalArgumentException(
                        "上传过于频繁：每" + WINDOW_CN.get(w) + "最多上传 " + limit + " 张图片（当前 "
                                + counts.get(w) + " 张），请稍后再试");
            }
        }
    }

    private Map<String, Long> countUploadsByIp(String ip) {
        Map<String, Long> out = new HashMap<>();
        WINDOWS.forEach(w -> out.put(w, 0L));
        if (ip == null || ip.isBlank()) {
            return out;
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        StringBuilder sql = new StringBuilder("SELECT ");
        for (int i = 0; i < WINDOWS.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            // 别名加 w_ 前缀：MINUTE/DAY/WEEK/MONTH 是 H2 的时间单位保留字
            sql.append("SUM(CASE WHEN image_date_gmt >= ? THEN 1 ELSE 0 END) AS w_")
                    .append(WINDOWS.get(i));
        }
        sql.append(" FROM chv_images WHERE image_uploader_ip = ? AND image_date_gmt >= ?");

        Object[] args = new Object[WINDOWS.size() + 2];
        for (int i = 0; i < WINDOWS.size(); i++) {
            args[i] = now.minus(windowAmount(WINDOWS.get(i)), windowUnit(WINDOWS.get(i)));
        }
        args[WINDOWS.size()] = ip;
        args[WINDOWS.size() + 1] = now.minusMonths(1);

        Map<String, Object> row = jdbc.queryForMap(sql.toString(), args);
        for (String w : WINDOWS) {
            out.put(w, ColumnValues.asLong(row, "w_" + w));
        }
        return out;
    }

    private void notifyOnce(String window, String ip, Map<String, Long> counts) {
        if (!settingsService.getBool("flood_uploads_notify")) {
            return;
        }
        long now = System.currentTimeMillis();
        Long until = notifiedAt.get(window);
        if (until != null && until > now) {
            return;
        }
        notifiedAt.put(window, now + windowMillis(window));
        String to = mailService.incomingEmail();
        if (to == null || to.isBlank()) {
            return;
        }
        try {
            StringBuilder html = new StringBuilder();
            html.append("<p>检测到上传洪水，IP：<strong>").append(escape(ip)).append("</strong></p>");
            html.append("<p>触发窗口：").append(WINDOW_CN.get(window)).append("</p><ul>");
            for (String w : WINDOWS) {
                html.append("<li>").append(WINDOW_CN.get(w)).append("：")
                        .append(counts.getOrDefault(w, 0L)).append(" 张</li>");
            }
            html.append("</ul>");
            mailService.send(to, "[洪水] 上传频繁 IP " + ip, html.toString());
        } catch (Exception e) {
            log.warn("洪水通知邮件发送失败：{}", e.getMessage());
        }
    }

    private long windowMillis(String w) {
        return switch (w) {
            case "minute" -> 60_000L;
            case "hour" -> 3_600_000L;
            case "day" -> 86_400_000L;
            case "week" -> 7 * 86_400_000L;
            default -> 30L * 86_400_000L;
        };
    }

    // ---------------- 机制 2：登录 / 注册失败限流 ----------------

    /** 是否已因失败过多被完全封禁（原版 is_max_invalid_request：严格大于，第 26 次起） */
    public boolean isBlocked(String ip) {
        Map<String, Long> c = requestLogService.counts(List.of("login", "signup"), "fail", ip);
        return c.getOrDefault("day", 0L) > INVALID_REQUESTS_PER_DAY;
    }

    /** 是否需要人机验证（原版 must_use_recaptcha：大于等于阈值） */
    public boolean needCaptcha(String ip) {
        if (!settingsService.getBool("recaptcha")) {
            return false;
        }
        int threshold = settingsService.getInt("recaptcha_threshold", 5);
        Map<String, Long> c = requestLogService.counts(List.of("login", "signup"), "fail", ip);
        return c.getOrDefault("day", 0L) >= threshold;
    }

    public long failCount24h(String ip) {
        return requestLogService.counts(List.of("login", "signup"), "fail", ip).getOrDefault("day", 0L);
    }

    // ---------------- 机制 3：注册洪水 ----------------

    public void assertSignupAllowed(String ip) {
        if (ip == null || ip.isBlank()) {
            return;
        }
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM chv_users WHERE user_registration_ip = ? "
                        + "AND user_status <> 'valid' AND user_date_gmt >= ?",
                Long.class, ip, LocalDateTime.now(ZoneOffset.UTC).minusDays(2));
        if (count != null && count > SIGNUP_LIMIT) {
            throw new IllegalArgumentException("检测到注册洪水：同一 IP 短期内创建的未激活账号过多，请稍后再试");
        }
    }

    // ---------------- helpers ----------------

    private long windowAmount(String w) {
        return switch (w) {
            case "minute", "hour", "day", "month" -> 1;
            default -> 7;
        };
    }

    private ChronoUnit windowUnit(String w) {
        return switch (w) {
            case "minute" -> ChronoUnit.MINUTES;
            case "hour" -> ChronoUnit.HOURS;
            case "day" -> ChronoUnit.DAYS;
            case "week" -> ChronoUnit.DAYS;
            default -> ChronoUnit.MONTHS;
        };
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** 后台状态展示 */
    public Map<String, Object> status(String ip) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("uploads", countUploadsByIp(ip));
        m.put("failToday", failCount24h(ip));
        m.put("blocked", isBlocked(ip));
        m.put("protectionEnabled", settingsService.getBool("flood_uploads_protection"));
        return m;
    }

    public LocalDate todayUtc() {
        return LocalDate.now(ZoneOffset.UTC);
    }
}
