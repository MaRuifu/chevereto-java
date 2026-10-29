package com.chevereto.java.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * chv_requests 请求日志（原版 RequestLog 的等价实现）。
 *
 * <p>用途：
 * <ul>
 *   <li>登录 / 注册失败计数 —— 达阈值升级为要求验证码，超硬阈值直接 403</li>
 *   <li>成功登录后清理该 IP + 该用户的失败记录（原版 Login::login 行为）</li>
 * </ul>
 *
 * <p>原版用 MySQL 的 {@code COUNT(IF(...))} + {@code DATE_SUB(UTC_TIMESTAMP(), INTERVAL 1 DAY)}
 * 一次性算 5 个滚动窗口；这里改为在 Java 侧算好 5 个时间点再绑定参数，
 * 因为 H2 不支持这两段 MySQL 方言 SQL。语义完全一致（滚动窗口，基准 UTC）。
 */
@Service
public class RequestLogService {

    private static final List<String> WINDOWS = List.of("minute", "hour", "day", "week", "month");

    private final JdbcTemplate jdbc;

    public RequestLogService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 记录一条请求日志；ip 为空时不写（避免脏数据） */
    public void insert(String type, String result, Long userId, Long contentId, String ip) {
        if (ip == null || ip.isBlank()) {
            return;
        }
        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
        jdbc.update("INSERT INTO chv_requests "
                        + "(request_type, request_user_id, request_content_id, request_ip, "
                        + "request_date, request_date_gmt, request_result) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                type, userId, contentId, ip, nowUtc, nowUtc, result);
    }

    /**
     * 统计某 IP 在 5 个滚动窗口内指定类型 + 结果的记录数。
     *
     * <p>别名用 {@code w_minute … w_month} 而不是直接 {@code minute}：
     * MINUTE / DAY / WEEK / MONTH 在 H2 里是时间单位保留字，做列别名会报语法错误；
     * 而且 H2 会把裸标识符转成大写返回，取值时要做大小写兼容。
     */
    public Map<String, Long> counts(List<String> types, String result, String ip) {
        Map<String, Long> out = new HashMap<>();
        if (ip == null || ip.isBlank()) {
            WINDOWS.forEach(w -> out.put(w, 0L));
            return out;
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String placeholders = String.join(",", types.stream().map(t -> "?").toList());

        StringBuilder sql = new StringBuilder("SELECT ");
        for (int i = 0; i < WINDOWS.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("SUM(CASE WHEN request_date_gmt >= ? THEN 1 ELSE 0 END) AS w_")
                    .append(WINDOWS.get(i));
        }
        sql.append(" FROM chv_requests WHERE request_type IN (").append(placeholders).append(")")
                .append(" AND request_result = ? AND request_ip = ?")
                .append(" AND request_date_gmt >= ?");

        Object[] args = new Object[WINDOWS.size() + types.size() + 3];
        for (int i = 0; i < WINDOWS.size(); i++) {
            String w = WINDOWS.get(i);
            args[i] = now.minus(windowAmount(w), windowUnit(w));
        }
        for (int i = 0; i < types.size(); i++) {
            args[WINDOWS.size() + i] = types.get(i);
        }
        args[WINDOWS.size() + types.size()] = result;
        args[WINDOWS.size() + types.size() + 1] = ip;
        args[WINDOWS.size() + types.size() + 2] = now.minusMonths(1);

        Map<String, Object> row = jdbc.queryForMap(sql.toString(), args);
        for (String w : WINDOWS) {
            out.put(w, ColumnValues.asLong(row, "w_" + w));
        }
        return out;
    }

    private long windowAmount(String w) {
        return switch (w) {
            case "minute" -> 1;
            case "hour" -> 1;
            case "day" -> 1;
            case "week" -> 7;
            default -> 1;
        };
    }

    private java.time.temporal.ChronoUnit windowUnit(String w) {
        return switch (w) {
            case "minute" -> java.time.temporal.ChronoUnit.MINUTES;
            case "hour" -> java.time.temporal.ChronoUnit.HOURS;
            case "day" -> java.time.temporal.ChronoUnit.DAYS;
            case "week" -> java.time.temporal.ChronoUnit.DAYS;
            default -> java.time.temporal.ChronoUnit.MONTHS;
        };
    }

    /** 登录成功后清掉「同 IP + 同用户」的失败记录（原版行为，账号枚举产生的 NULL 用户记录清不掉） */
    public int clearLoginFails(Long userId, String ip) {
        if (userId == null || ip == null || ip.isBlank()) {
            return 0;
        }
        return jdbc.update("DELETE FROM chv_requests WHERE request_type = 'login' "
                + "AND request_result = 'fail' AND request_user_id = ? AND request_ip = ?", userId, ip);
    }

    /** 后台展示用：最近 24 小时失败总数与来源 IP 数 */
    public long[] failStats24h() {
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT COUNT(*) AS c, COUNT(DISTINCT request_ip) AS ips FROM chv_requests "
                        + "WHERE request_type IN ('login','signup') AND request_result = 'fail' "
                        + "AND request_date_gmt >= ?",
                LocalDateTime.now(ZoneOffset.UTC).minusDays(1));
        return new long[]{((Number) row.get("c")).longValue(), ((Number) row.get("ips")).longValue()};
    }
}
