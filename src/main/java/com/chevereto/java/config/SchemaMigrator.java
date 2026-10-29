package com.chevereto.java.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 表结构演进：给「已经存在的表」补上脚本里新增的列。
 *
 * <p>为什么需要它：建表脚本用的是 {@code CREATE TABLE IF NOT EXISTS}，对已存在的表
 * 不会做任何改动。于是两类库会停在旧结构上：
 * <ul>
 *   <li>早期版本建的 H2 库 —— 缺 image_chain / image_is_360 等后加的列</li>
 *   <li>原版 PHP 的 MySQL 库（无缝迁移场景）—— 缺本项目新增的扩展列</li>
 * </ul>
 * 缺列的表现是上传/写入时 {@code Column "XXX" not found}，且发生在运行期而非启动期。
 *
 * <p>这里只做「加列」这一件事：解析建表脚本的列定义，与 INFORMATION_SCHEMA 对比，
 * 缺的列 {@code ALTER TABLE ... ADD COLUMN ...} 补上。不改类型、不动索引、不删列，
 * 因此对原版 PHP 同时运行是安全的（新列都带默认值，PHP 不读写也不受影响）。
 */
@Component
public class SchemaMigrator {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigrator.class);

    /** 非列定义的行（主键/索引/约束等），解析时跳过 */
    private static final Pattern NON_COLUMN = Pattern.compile(
            "^(PRIMARY\\s+KEY|UNIQUE\\s+KEY|KEY|INDEX|FULLTEXT|CONSTRAINT|CHECK|FOREIGN\\s+KEY)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern TYPE = Pattern.compile("^([A-Za-z]+(?:\\s*\\([^)]*\\))?)");
    private static final Pattern DEFAULT_VAL = Pattern.compile("DEFAULT\\s+('[^']*'|\\S+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ENUM_FIRST = Pattern.compile("'([^']*)'");

    private final JdbcTemplate jdbc;

    public SchemaMigrator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 按方言补齐缺失列；返回补了几列 */
    public int ensureColumns(boolean h2) {
        return ensureColumns(jdbc.getDataSource(), h2);
    }

    /**
     * 对指定数据源补齐缺失列。安装向导切换 MySQL 时，新库还没挂到 JdbcTemplate 上，
     * 必须先对候选连接池跑一遍（切过去之前），所以这里额外接受一个 DataSource。
     */
    public int ensureColumns(javax.sql.DataSource target, boolean h2) {
        if (target == null) {
            return 0;
        }
        JdbcTemplate template = (target == jdbc.getDataSource()) ? jdbc : new JdbcTemplate(target);
        Map<String, Map<String, String>> wanted = parseScript(h2 ? "schema-h2.sql" : "schema-mysql.sql");
        Set<String> existing = existingColumns(template, h2);
        int added = 0;
        for (Map.Entry<String, Map<String, String>> table : wanted.entrySet()) {
            for (Map.Entry<String, String> col : table.getValue().entrySet()) {
                String key = table.getKey().toUpperCase(Locale.ROOT) + "." + col.getKey().toUpperCase(Locale.ROOT);
                if (existing.contains(key)) {
                    continue;
                }
                String ddl = "ALTER TABLE " + table.getKey() + " ADD COLUMN " + col.getKey() + " "
                        + addColumnDefinition(col.getKey(), col.getValue());
                try {
                    template.execute(ddl);
                    added++;
                    log.info("已补列：{}", ddl);
                } catch (Exception e) {
                    // 单列失败不该让整站起不来，记录后继续
                    log.warn("补列失败已跳过：{} —— {}", ddl, e.getMessage());
                }
            }
        }
        if (added > 0) {
            log.info("表结构演进完成：补齐 {} 个缺失列", added);
        }
        int relaxed = relaxLegacyColumns(template, wanted, h2);
        return added + relaxed;
    }

    /**
     * 兜底「脚本里已经没有、但旧库里还在」的遗留列。
     *
     * <p>这类列代码不再读写，INSERT 也不给值，一旦它是 {@code NOT NULL} 且没有默认值，
     * 每次插入都会被约束拦下（典型：早期版本留下的 chv_images.image_privacy）。
     * 这里只给它补一个默认值，不删列、不改类型，因此不会丢任何已有数据。
     */
    private int relaxLegacyColumns(JdbcTemplate template, Map<String, Map<String, String>> wanted, boolean h2) {
        List<Map<String, Object>> rows;
        try {
            String currentSchema = h2 ? "CURRENT_SCHEMA()" : "DATABASE()";
            rows = template.queryForList("SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, IS_NULLABLE, "
                    + "COLUMN_DEFAULT FROM INFORMATION_SCHEMA.COLUMNS "
                    + "WHERE TABLE_SCHEMA = " + currentSchema + " AND UPPER(TABLE_NAME) LIKE 'CHV%'");
        } catch (Exception e) {
            return 0;
        }
        int n = 0;
        for (Map<String, Object> r : rows) {
            Object to = firstValue(r, "TABLE_NAME", "table_name");
            Object co = firstValue(r, "COLUMN_NAME", "column_name");
            if (to == null || co == null) {
                continue;
            }
            String table = to.toString().toLowerCase(Locale.ROOT);
            String column = co.toString().toLowerCase(Locale.ROOT);
            Map<String, String> scriptCols = wanted.get(table);
            if (scriptCols != null && scriptCols.containsKey(column)) {
                continue; // 脚本里的列按脚本定义走，不动
            }
            String nullable = String.valueOf(firstValue(r, "IS_NULLABLE", "is_nullable"));
            Object def = firstValue(r, "COLUMN_DEFAULT", "column_default");
            if (!"NO".equalsIgnoreCase(nullable) || def != null) {
                continue; // 可空或已有默认值，不会挡住插入
            }
            String zero = zeroForDataType(String.valueOf(firstValue(r, "DATA_TYPE", "data_type")));
            String ddl = "ALTER TABLE " + table + " ALTER COLUMN " + column + " SET DEFAULT " + zero;
            try {
                template.execute(ddl);
                n++;
                log.info("遗留列 {} 缺少默认值，已设为 {}：{}", column, zero, ddl);
            } catch (Exception e) {
                log.warn("设置遗留列默认值失败已跳过：{} —— {}", ddl, e.getMessage());
            }
        }
        return n;
    }

    private String zeroForDataType(String dataType) {
        String t = dataType.toLowerCase(Locale.ROOT);
        if (t.contains("char") || t.contains("text") || t.contains("enum") || t.contains("set")) {
            return "''";
        }
        if (t.contains("int") || t.contains("decimal") || t.contains("numeric")
                || t.contains("float") || t.contains("double") || t.contains("real")) {
            return "0";
        }
        if (t.contains("date") || t.contains("time") || t.contains("year")) {
            return "'1970-01-01 00:00:00'";
        }
        return "''";
    }

    /**
     * 现有库里 chv_* 表的列集合，元素形如 "CHV_IMAGES.IMAGE_ID"。
     *
     * <p>必须限定当前 schema：INFORMATION_SCHEMA.COLUMNS 会跨库返回，不限定的话
     * 同一实例上的另一个 Chevereto 库（列齐全）会让这里误判成"列都存在"，补列被整个跳过。
     */
    private Set<String> existingColumns(JdbcTemplate template, boolean h2) {
        Set<String> out = new HashSet<>();
        List<Map<String, Object>> rows;
        try {
            // H2 与 MySQL 8 都有 INFORMATION_SCHEMA.COLUMNS；UPPER() 抹平两边大小写差异
            String currentSchema = h2 ? "CURRENT_SCHEMA()" : "DATABASE()";
            rows = template.queryForList("SELECT TABLE_NAME, COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS "
                    + "WHERE TABLE_SCHEMA = " + currentSchema + " AND UPPER(TABLE_NAME) LIKE 'CHV%'");
        } catch (Exception e) {
            log.warn("读取 INFORMATION_SCHEMA 失败，跳过列补齐：{}", e.getMessage());
            return out;
        }
        for (Map<String, Object> r : rows) {
            Object t = firstValue(r, "TABLE_NAME", "table_name");
            Object c = firstValue(r, "COLUMN_NAME", "column_name");
            if (t != null && c != null) {
                out.add(t.toString().toUpperCase(Locale.ROOT) + "." + c.toString().toUpperCase(Locale.ROOT));
            }
        }
        return out;
    }

    private static Object firstValue(Map<String, Object> row, String... keys) {
        for (String k : keys) {
            Object v = row.get(k);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    /** 从建表脚本解析出 表名(小写) → 列名(小写) → 列定义原文 */
    private Map<String, Map<String, String>> parseScript(String script) {
        Map<String, Map<String, String>> tables = new LinkedHashMap<>();
        String text;
        try (InputStream in = new ClassPathResource(script).getInputStream()) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("读取建表脚本失败，跳过列补齐：{}", e.getMessage());
            return tables;
        }
        String current = null;
        for (String rawLine : text.split("\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("--")) {
                continue;
            }
            Matcher create = Pattern.compile(
                    "CREATE TABLE IF NOT EXISTS [`\\[]?(\\w+)[`\\]]?\\s*\\(", Pattern.CASE_INSENSITIVE)
                    .matcher(line);
            if (create.find()) {
                current = create.group(1).toLowerCase(Locale.ROOT);
                tables.put(current, new LinkedHashMap<>());
                continue;
            }
            if (current != null && (line.startsWith(")") || line.equals(";"))) {
                current = null;
                continue;
            }
            if (current == null || NON_COLUMN.matcher(line).find()) {
                continue;
            }
            String def = line.replace("`", "").replaceAll(",+$", "");
            int sp = def.indexOf(' ');
            if (sp <= 0) {
                continue;
            }
            String colName = def.substring(0, sp).toLowerCase(Locale.ROOT);
            if (colName.isBlank()) {
                continue;
            }
            tables.get(current).put(colName, def);
        }
        return tables;
    }

    /**
     * 把建表脚本里的列定义改成可用于 ALTER TABLE ADD COLUMN 的形式：
     * 去掉 AUTO_INCREMENT / CHARACTER SET / COLLATE / COMMENT / ON UPDATE，
     * 并给「NOT NULL 且无默认值」的列补一个零值默认（否则往有数据的表上加列会被拒）。
     */
    private String addColumnDefinition(String column, String rawDef) {
        String def = rawDef.replace("`", "").replaceAll(",+$", "").trim();
        String rest = def.length() > column.length() ? def.substring(column.length()).trim() : def;

        rest = rest.replaceAll("(?i)\\s*AUTO_INCREMENT", "")
                .replaceAll("(?i)\\s*CHARACTER\\s+SET\\s+\\S+", "")
                .replaceAll("(?i)\\s*COLLATE\\s+\\S+", "")
                .replaceAll("(?i)\\s*ON\\s+UPDATE\\s+\\S+", "")
                .replaceAll("(?i)\\s*COMMENT\\s+'[^']*'", "")
                .replaceAll("(?i)\\s*COMMENT\\s+\\S+", "")
                .trim();

        String type = rest;
        Matcher tm = TYPE.matcher(rest);
        if (tm.find()) {
            type = tm.group(1).replaceAll("\\s*\\(", "(");
        }
        String upper = rest.toUpperCase(Locale.ROOT);
        boolean notNull = upper.contains("NOT NULL");
        String defaultValue = null;
        Matcher dm = DEFAULT_VAL.matcher(rest);
        if (dm.find()) {
            defaultValue = dm.group(1);
        }

        // TEXT/BLOB/JSON 在 MySQL 上不允许默认值；时间列给默认值容易踩 0000-00-00，都降级为可空
        boolean textLike = type.toLowerCase(Locale.ROOT).matches(".*(text|blob|json|geometry).*");
        boolean temporal = type.toLowerCase(Locale.ROOT).matches("(date|datetime|timestamp|time|year).*");

        StringBuilder sb = new StringBuilder(type);
        // DEFAULT NULL 没有任何约束作用，写出来反而挑数据库，直接省略
        if (defaultValue != null && !"NULL".equalsIgnoreCase(defaultValue)) {
            sb.append(" DEFAULT ").append(defaultValue);
        } else if (notNull && !textLike && !temporal) {
            sb.append(" DEFAULT ").append(zeroValue(type));
        }
        // 时间列没有默认值时不能强加 NOT NULL
        if (notNull && !textLike && !(temporal && defaultValue == null)) {
            sb.append(" NOT NULL");
        }
        return sb.toString();
    }

    /** 按类型给一个安全的零值：数值 0、字符串 ''、枚举取第一个枚举项 */
    private String zeroValue(String type) {
        String t = type.toLowerCase(Locale.ROOT);
        if (t.startsWith("enum")) {
            Matcher m = ENUM_FIRST.matcher(type);
            return m.find() ? "'" + m.group(1) + "'" : "''";
        }
        if (t.contains("char") || t.contains("text")) {
            return "''";
        }
        if (t.startsWith("date") || t.startsWith("time") || t.startsWith("year")) {
            return "'1970-01-01'";
        }
        return "0";
    }
}
