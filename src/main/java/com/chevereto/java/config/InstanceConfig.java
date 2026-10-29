package com.chevereto.java.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * 实例数据库配置（首次启动向导的落盘位置）。
 *
 * <p>配置文件默认 {@code ./data/instance.properties}，内容示例：
 * <pre>
 * db.type=mysql
 * db.url=jdbc:mysql://127.0.0.1:3306/chevereto?useUnicode=true&amp;characterEncoding=utf8&amp;serverTimezone=Asia/Shanghai
 * db.username=chevereto
 * db.password=******
 * </pre>
 *
 * <p>优先级从高到低：
 * <ol>
 *   <li>启动时显式指定的 {@code spring.datasource.*}（命令行 {@code --spring.datasource.url=...}
 *       或环境变量 {@code SPRING_DATASOURCE_URL}）—— 无缝迁移现有 Chevereto 库走这条路，
 *       指定后会被采纳并落盘，之后不带参数启动也保持同一个库</li>
 *   <li>配置文件存在 → 直接用配置里的库启动（已初始化则跳过建表/建管理员）</li>
 *   <li>配置文件不存在，但检测到历史 H2 数据文件 → 自动补写 H2 配置，不打断老用户</li>
 *   <li>都没有 → 视为全新安装，进入 {@code /setup} 向导选择 H2 或 MySQL</li>
 * </ol>
 */
@Component
public class InstanceConfig {

    private static final Logger log = LoggerFactory.getLogger(InstanceConfig.class);

    /** H2 内置库文件（与原 application.yml 完全一致的路径与参数，老数据可直接沿用） */
    public static final String H2_FILE = "./data/db/chevereto";
    public static final String H2_URL = "jdbc:h2:file:" + H2_FILE + ";MODE=MySQL;AUTO_SERVER=TRUE";

    private final Path file;
    private final Properties props = new Properties();
    private volatile boolean configured;
    private final String externalUrl;
    private final String externalUsername;
    private final String externalPassword;

    public InstanceConfig(@Value("${app.instance-config:./data/instance.properties}") String configPath,
                          @Value("${spring.datasource.url:}") String externalUrl,
                          @Value("${spring.datasource.username:}") String externalUsername,
                          @Value("${spring.datasource.password:}") String externalPassword) {
        this.file = Paths.get(configPath);
        this.externalUrl = externalUrl == null ? "" : externalUrl.trim();
        this.externalUsername = externalUsername == null ? "" : externalUsername.trim();
        this.externalPassword = externalPassword == null ? "" : externalPassword;
    }

    @PostConstruct
    public void load() {
        // 1) 启动时显式指定的数据源优先：命令行 --spring.datasource.url=... 或环境变量 SPRING_DATASOURCE_URL
        if (!externalUrl.isBlank()) {
            adoptExternal();
            return;
        }
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                props.load(in);
            } catch (IOException e) {
                log.error("读取实例配置失败：{}", file, e);
            }
            configured = !dbUrl().isBlank();
            if (configured) {
                log.info("已加载实例配置：{}（{}）", file, dbType());
                return;
            }
        }
        // 历史版本没有配置文件，只有 H2 数据文件：自动补写，避免老用户被拉到向导
        if (Files.exists(Paths.get(H2_FILE + ".mv.db"))) {
            saveH2();
            log.info("检测到已有 H2 数据文件，自动沿用内置数据库：{}", H2_FILE);
        } else {
            log.info("未检测到数据库配置与数据文件，等待首次安装向导选择数据库类型");
        }
    }

    public boolean isConfigured() {
        return configured;
    }

    public String dbType() {
        return props.getProperty("db.type", "h2");
    }

    public boolean isH2() {
        return "h2".equalsIgnoreCase(dbType());
    }

    public String dbUrl() {
        String url = props.getProperty("db.url", "");
        return url.isBlank() ? H2_URL : url;
    }

    public String dbUsername() {
        return props.getProperty("db.username", "sa");
    }

    public String dbPassword() {
        return props.getProperty("db.password", "");
    }

    /** 表单回显用的 MySQL 连接片段 */
    public String mysqlHost() {
        return props.getProperty("db.host", "127.0.0.1");
    }

    public String mysqlPort() {
        return props.getProperty("db.port", "3306");
    }

    public String mysqlDatabase() {
        return props.getProperty("db.name", "chevereto");
    }

    /**
     * 采纳启动时显式指定的数据源（spring.datasource.*）。
     * H2 URL 直接沿用（用户名/密码以传入值为准）；其余一律按 MySQL 处理。
     */
    private synchronized void adoptExternal() {
        String url = externalUrl;
        if (url.startsWith("jdbc:h2:")) {
            props.setProperty("db.type", "h2");
            props.setProperty("db.url", url);
            props.setProperty("db.username", externalUsername.isBlank() ? "sa" : externalUsername);
            props.setProperty("db.password", externalPassword);
            log.info("使用启动时指定的内置 H2 数据库：{}", url);
        } else {
            if (!url.startsWith("jdbc:")) {
                // 允许直接写 host:port/db 的简写
                url = "jdbc:mysql://" + url;
            }
            if (!url.contains("?")) {
                url += "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                        + "&useSSL=false&allowPublicKeyRetrieval=true";
            }
            String host = "127.0.0.1";
            String port = "3306";
            String name = "chevereto";
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("jdbc:mysql://([^:/]+)(?::(\\d+))?/([^?]+)").matcher(url);
            if (m.find()) {
                host = m.group(1);
                if (m.group(2) != null) {
                    port = m.group(2);
                }
                name = m.group(3);
            }
            props.setProperty("db.type", "mysql");
            props.setProperty("db.host", host);
            props.setProperty("db.port", port);
            props.setProperty("db.name", name);
            props.setProperty("db.url", url);
            props.setProperty("db.username", externalUsername);
            props.setProperty("db.password", externalPassword);
            log.info("使用启动时指定的 MySQL 数据库：{}@{}:{}/{}", externalUsername, host, port, name);
        }
        write();
        configured = true;
    }

    public synchronized void saveH2() {
        props.setProperty("db.type", "h2");
        props.setProperty("db.url", H2_URL);
        props.setProperty("db.username", "sa");
        props.setProperty("db.password", "");
        write();
        configured = true;
    }

    public synchronized void saveMysql(String host, String port, String database,
                                       String username, String password) {
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                + "&useSSL=false&allowPublicKeyRetrieval=true";
        props.setProperty("db.type", "mysql");
        props.setProperty("db.host", host);
        props.setProperty("db.port", port);
        props.setProperty("db.name", database);
        props.setProperty("db.url", url);
        props.setProperty("db.username", username);
        props.setProperty("db.password", password == null ? "" : password);
        write();
        configured = true;
    }

    private void write() {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream out = Files.newOutputStream(file)) {
                props.store(out, "chevereto-java 实例数据库配置（由首次安装向导生成）");
            }
        } catch (IOException e) {
            throw new IllegalStateException("写入实例配置失败：" + file, e);
        }
    }
}
