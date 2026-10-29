package com.chevereto.java.service;

import com.chevereto.java.entity.Image;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 站点统计 —— 对齐原版 {@code Stat::track} / {@code getTotals} / {@code getByDateCumulative}。
 *
 * <p>数据模型（chv_stats）：
 * <ul>
 *   <li>{@code stat_type='total'} 一行，stat_date_gmt 为 NULL，存全局累计</li>
 *   <li>{@code stat_type='date'} 每天一行，stat_date_gmt 唯一，存当天增量</li>
 * </ul>
 *
 * <p>写入时机完全对齐原版：
 * <table>
 *   <tr><th>事件</th><th>+1 的列</th><th>命中的日期行</th></tr>
 *   <tr><td>上传成功</td><td>stat_images、stat_disk_used(+原图+缩略图+中图)</td><td>image_date_gmt</td></tr>
 *   <tr><td>删除图片</td><td>stat_images、stat_disk_used、stat_image_likes 减</td><td>image_date_gmt</td></tr>
 *   <tr><td>浏览图片</td><td>stat_image_views、user_content_views</td><td>今天(UTC)</td></tr>
 *   <tr><td>新建/删除相册</td><td>stat_albums</td><td>album_date_gmt</td></tr>
 *   <tr><td>浏览相册</td><td>stat_album_views</td><td>今天(UTC)</td></tr>
 *   <tr><td>注册/删除用户</td><td>stat_users</td><td>user_date_gmt</td></tr>
 *   <tr><td>点赞/取消</td><td>stat_image_likes / stat_album_likes</td><td>点赞日期</td></tr>
 * </table>
 *
 * <p>原版用 MySQL 的 {@code INSERT ... ON DUPLICATE KEY UPDATE} 做按天 upsert；
 * 这里改成「先 UPDATE，影响 0 行再 INSERT」，H2/MySQL 都可执行，语义等价。
 */
@Service
public class StatsService {

    /** 允许写入的列，白名单避免拼接注入 */
    private static final Set<String> ALLOWED = Set.of(
            "stat_users", "stat_images", "stat_albums", "stat_image_views",
            "stat_album_views", "stat_image_likes", "stat_album_likes", "stat_disk_used");

    /** 读取顺序（仪表盘卡片按这个顺序渲染） */
    private static final List<String> ALL_COLUMNS = List.of(
            "stat_users", "stat_images", "stat_albums", "stat_image_views",
            "stat_album_views", "stat_image_likes", "stat_album_likes", "stat_disk_used");

    private final JdbcTemplate jdbc;

    public StatsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------------- 写入 ----------------

    public void imageInserted(Image image) {
        long disk = diskOf(image);
        LocalDate date = dateOf(image.getImageDateGmt());
        addTotal("stat_images", 1);
        addTotal("stat_disk_used", disk);
        addDate(date, Map.of("stat_images", 1L, "stat_disk_used", disk));
    }

    public void imageDeleted(Image image) {
        long disk = diskOf(image);
        LocalDate date = dateOf(image.getImageDateGmt());
        long likes = image.getImageLikes();
        addTotal("stat_images", -1);
        addTotal("stat_disk_used", -disk);
        addDate(date, Map.of("stat_images", -1L, "stat_disk_used", -disk));
        if (likes > 0) {
            addTotal("stat_image_likes", -likes);
            addDate(date, Map.of("stat_image_likes", -likes));
        }
    }

    public void imageViewed(Long ownerUserId) {
        LocalDate today = todayUtc();
        addTotal("stat_image_views", 1);
        addDate(today, Map.of("stat_image_views", 1L));
        if (ownerUserId != null) {
            jdbc.update("UPDATE chv_users SET user_content_views = user_content_views + 1 WHERE user_id = ?",
                    ownerUserId);
        }
    }

    public void albumInserted(LocalDateTime dateGmt) {
        LocalDate date = dateOf(dateGmt);
        addTotal("stat_albums", 1);
        addDate(date, Map.of("stat_albums", 1L));
    }

    public void albumDeleted(LocalDateTime dateGmt) {
        LocalDate date = dateOf(dateGmt);
        addTotal("stat_albums", -1);
        addDate(date, Map.of("stat_albums", -1L));
    }

    public void albumViewed(Long ownerUserId) {
        LocalDate today = todayUtc();
        addTotal("stat_album_views", 1);
        addDate(today, Map.of("stat_album_views", 1L));
        if (ownerUserId != null) {
            jdbc.update("UPDATE chv_users SET user_content_views = user_content_views + 1 WHERE user_id = ?",
                    ownerUserId);
        }
    }

    public void userInserted(LocalDateTime dateGmt) {
        LocalDate date = dateOf(dateGmt);
        addTotal("stat_users", 1);
        addDate(date, Map.of("stat_users", 1L));
    }

    public void userDeleted(LocalDateTime dateGmt) {
        LocalDate date = dateOf(dateGmt);
        addTotal("stat_users", -1);
        addDate(date, Map.of("stat_users", -1L));
    }

    public void likeChanged(boolean image, boolean add, LocalDateTime dateGmt) {
        String col = image ? "stat_image_likes" : "stat_album_likes";
        LocalDate date = dateOf(dateGmt);
        int delta = add ? 1 : -1;
        addTotal(col, delta);
        addDate(date, Map.of(col, (long) delta));
    }

    // ---------------- 读取 ----------------

    public Map<String, Long> totals() {
        Map<String, Long> out = new LinkedHashMap<>();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM chv_stats WHERE stat_type = 'total' LIMIT 1");
        Map<String, Object> row = rows.isEmpty() ? null : rows.get(0);
        for (String col : ALL_COLUMNS) {
            // H2 返回大写列名，MySQL 保持小写 —— 统一走大小写兼容取值
            out.put(col, ColumnValues.asLong(row, col));
        }
        return out;
    }

    /** 最近 limit 条按日记录，升序返回（原版 ORDER BY DESC LIMIT 365 后 array_reverse） */
    public List<DailyRow> daily(int limit) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM chv_stats WHERE stat_type = 'date' AND stat_date_gmt IS NOT NULL "
                        + "ORDER BY stat_date_gmt DESC LIMIT ?", limit);
        List<DailyRow> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            out.add(new DailyRow(
                    toDate(ColumnValues.get(r, "stat_date_gmt")),
                    ColumnValues.asLong(r, "stat_users"),
                    ColumnValues.asLong(r, "stat_images"),
                    ColumnValues.asLong(r, "stat_albums"),
                    ColumnValues.asLong(r, "stat_image_views"),
                    ColumnValues.asLong(r, "stat_album_views"),
                    ColumnValues.asLong(r, "stat_image_likes"),
                    ColumnValues.asLong(r, "stat_album_likes"),
                    ColumnValues.asLong(r, "stat_disk_used")));
        }
        java.util.Collections.reverse(out);
        return out;
    }

    /**
     * 逐日累加（原版 getByDateCumulative）：返回每天一条，附带 *_acc 累加值。
     * 供仪表盘趋势图直接使用。
     */
    public List<CumulativeRow> cumulative(int limit) {
        long users = 0, images = 0, albums = 0, imageViews = 0, albumViews = 0, imageLikes = 0, disk = 0;
        List<CumulativeRow> out = new ArrayList<>();
        for (DailyRow d : daily(limit)) {
            users += d.users();
            images += d.images();
            albums += d.albums();
            imageViews += d.imageViews();
            albumViews += d.albumViews();
            imageLikes += d.imageLikes();
            disk += d.diskUsed();
            out.add(new CumulativeRow(d.date(), images, albums, users, disk, imageViews, albumViews, imageLikes));
        }
        return out;
    }

    // ---------------- 重算 ----------------

    /**
     * 全量重算（原版 rebuildTotals）。与原版的两处差异：
     * <ul>
     *   <li>用 DELETE 而不是 TRUNCATE：TRUNCATE 是 DDL，会隐式提交并让仪表盘瞬间读到空表</li>
     *   <li>补齐原版遗漏的 stat_album_views / stat_image_likes / stat_album_likes 重算</li>
     * </ul>
     */
    public void rebuild() {
        jdbc.update("DELETE FROM chv_stats");
        jdbc.update("INSERT INTO chv_stats (stat_type, stat_date_gmt) VALUES ('total', NULL)");
        jdbc.update("UPDATE chv_stats SET "
                + "stat_images = (SELECT COUNT(*) FROM chv_images), "
                + "stat_albums = (SELECT COUNT(*) FROM chv_albums), "
                + "stat_users = (SELECT COUNT(*) FROM chv_users), "
                + "stat_image_views = (SELECT COALESCE(SUM(image_views),0) FROM chv_images), "
                + "stat_album_views = (SELECT COALESCE(SUM(album_views),0) FROM chv_albums), "
                + "stat_image_likes = (SELECT COALESCE(SUM(image_likes),0) FROM chv_images), "
                + "stat_album_likes = (SELECT COALESCE(SUM(album_likes),0) FROM chv_albums), "
                + "stat_disk_used = (SELECT COALESCE(SUM(image_size),0) FROM chv_images) "
                + "WHERE stat_type = 'total'");

        // 按天：先由图片建出日期键，再补用户/相册，最后汇总浏览量
        jdbc.update("INSERT INTO chv_stats (stat_type, stat_date_gmt, stat_images, stat_image_views, stat_disk_used) "
                + "SELECT 'date', CAST(image_date_gmt AS DATE), COUNT(*), "
                + "COALESCE(SUM(image_views),0), COALESCE(SUM(image_size),0) "
                + "FROM chv_images GROUP BY CAST(image_date_gmt AS DATE)");
        mergeDateRows("stat_users", "SELECT CAST(user_date_gmt AS DATE) AS d, COUNT(*) AS c FROM chv_users GROUP BY CAST(user_date_gmt AS DATE)");
        mergeDateRows("stat_albums", "SELECT CAST(album_date_gmt AS DATE) AS d, COUNT(*) AS c FROM chv_albums GROUP BY CAST(album_date_gmt AS DATE)");
        jdbc.update("UPDATE chv_users SET user_content_views = COALESCE("
                + "(SELECT SUM(i.image_views) FROM chv_images i WHERE i.image_user_id = chv_users.user_id), 0)");
    }

    /** 把「日期 + 计数」聚合结果合并进按天行（不存在则插入） */
    private void mergeDateRows(String column, String aggregateSql) {
        if (!ALLOWED.contains(column)) {
            throw new IllegalArgumentException("非法统计列：" + column);
        }
        List<Map<String, Object>> rows = jdbc.queryForList(aggregateSql);
        for (Map<String, Object> r : rows) {
            // 别名 d / c 在 H2 里会变成 D / C，取值要做大小写兼容
            LocalDate d = toDate(ColumnValues.get(r, "d"));
            if (d == null) {
                continue;
            }
            long c = ColumnValues.asLong(r, "c");
            int updated = jdbc.update("UPDATE chv_stats SET " + column + " = ? "
                    + "WHERE stat_type = 'date' AND stat_date_gmt = ?", c, d);
            if (updated == 0) {
                jdbc.update("INSERT INTO chv_stats (stat_type, stat_date_gmt, " + column + ") VALUES ('date', ?, ?)",
                        d, c);
            }
        }
    }

    // ---------------- 内部 ----------------

    private void addTotal(String column, long delta) {
        if (delta == 0) {
            return;
        }
        assertColumn(column);
        // GREATEST(x + delta, 0)：与原版一致，避免并发删除把计数打成负数
        jdbc.update("UPDATE chv_stats SET " + column + " = GREATEST(" + column + " + ?, 0) "
                + "WHERE stat_type = 'total'", delta);
    }

    private void addDate(LocalDate date, Map<String, Long> deltas) {
        if (date == null || deltas.isEmpty()) {
            return;
        }
        deltas.keySet().forEach(this::assertColumn);
        // 1) 先尝试更新当天行
        StringBuilder set = new StringBuilder();
        List<Object> args = new ArrayList<>();
        deltas.forEach((col, delta) -> {
            if (set.length() > 0) {
                set.append(", ");
            }
            set.append(col).append(" = GREATEST(").append(col).append(" + ?, 0)");
            args.add(delta);
        });
        args.add(date);
        int updated = jdbc.update("UPDATE chv_stats SET " + set
                + " WHERE stat_type = 'date' AND stat_date_gmt = ?", args.toArray());
        if (updated > 0) {
            return;
        }
        // 2) 当天还没有行 → 插入；并发下可能已被其它请求插入，捕获后回退为更新
        StringBuilder cols = new StringBuilder();
        StringBuilder marks = new StringBuilder();
        List<Object> insertArgs = new ArrayList<>();
        for (Map.Entry<String, Long> e : deltas.entrySet()) {
            cols.append(", ").append(e.getKey());
            marks.append(", ?");
            insertArgs.add(e.getValue());
        }
        insertArgs.add(0, date);
        try {
            jdbc.update("INSERT INTO chv_stats (stat_type, stat_date_gmt" + cols + ") VALUES ('date', ?" + marks + ")",
                    insertArgs.toArray());
        } catch (org.springframework.dao.DataAccessException e) {
            jdbc.update("UPDATE chv_stats SET " + set + " WHERE stat_type = 'date' AND stat_date_gmt = ?",
                    args.toArray());
        }
    }

    private void assertColumn(String column) {
        if (!ALLOWED.contains(column)) {
            throw new IllegalArgumentException("非法统计列：" + column);
        }
    }

    /**
     * 原版 disk 统计口径：原图 + 缩略图 + 中图（medium 未生成时为 0）。
     * 实体里这三个字段都是 long 基本类型，不存在 null。
     */
    private long diskOf(Image image) {
        return image.getImageSize() + image.getImageThumbSize() + image.getImageMediumSize();
    }

    private LocalDate todayUtc() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    private LocalDate dateOf(LocalDateTime dt) {
        return dt == null ? todayUtc() : dt.toLocalDate();
    }

    private LocalDate toDate(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof LocalDate d) {
            return d;
        }
        if (v instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        if (v instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime().toLocalDate();
        }
        return LocalDate.parse(v.toString().substring(0, 10));
    }

    private long num(Object v) {
        return v == null ? 0 : ((Number) v).longValue();
    }

    // ---------------- DTO ----------------

    public record DailyRow(LocalDate date, long users, long images, long albums,
                           long imageViews, long albumViews, long imageLikes, long albumLikes,
                           long diskUsed) {
    }

    public record CumulativeRow(LocalDate date, long images, long albums, long users, long diskUsed,
                                long imageViews, long albumViews, long imageLikes) {

        /** 图表用的 UTC 毫秒时间戳（原版 new DateTime(date, UTC)->getTimestamp()*1000） */
        public long epochMilli() {
            return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        }

        /** 原版 bytes_to_mb = round(bytes / 1e6) */
        public long diskMb() {
            return Math.round(diskUsed / 1_000_000d);
        }
    }
}
