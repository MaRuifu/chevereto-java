package com.chevereto.java.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * 数据源装配：默认按 {@link InstanceConfig} 建池，并包一层 {@link SwitchableDataSource}，
 * 让首次安装向导可以在运行时从 H2 切到 MySQL 而不重启。
 *
 * <p>说明：本类显式声明 DataSource Bean 后，Spring Boot 的 DataSourceAutoConfiguration
 * 不再生效，application.yml 里的 spring.datasource.* 仅作历史参考，真正生效的是
 * ./data/instance.properties。
 */
@Configuration
public class DataSourceConfig {

    private static final String MYSQL_DRIVER = "com.mysql.cj.jdbc.Driver";
    private static final String H2_DRIVER = "org.h2.Driver";

    @Bean
    public SwitchableDataSource dataSource(InstanceConfig instanceConfig) {
        return new SwitchableDataSource(build(instanceConfig));
    }

    public static HikariDataSource build(InstanceConfig cfg) {
        return cfg.isH2()
                ? buildH2()
                : buildMysql(cfg.dbUrl(), cfg.dbUsername(), cfg.dbPassword(), 10);
    }

    public static HikariDataSource buildH2() {
        try {
            Files.createDirectories(Paths.get(InstanceConfig.H2_FILE).toAbsolutePath().getParent());
        } catch (Exception ignored) {
            // 目录已存在或不可创建时由驱动自己报错，信息更准确
        }
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(InstanceConfig.H2_URL);
        hc.setDriverClassName(H2_DRIVER);
        hc.setUsername("sa");
        hc.setPassword("");
        // H2 是单文件库，连接数不宜大；AUTO_SERVER 需要至少保留一个常驻连接
        hc.setMaximumPoolSize(4);
        hc.setMinimumIdle(1);
        hc.setPoolName("chv-h2");
        return new HikariDataSource(hc);
    }

    public static HikariDataSource buildMysql(String url, String username, String password, int poolSize) {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(url);
        hc.setDriverClassName(MYSQL_DRIVER);
        hc.setUsername(username);
        hc.setPassword(password == null ? "" : password);
        hc.setMaximumPoolSize(poolSize);
        hc.setMinimumIdle(1);
        hc.setPoolName("chv-mysql");
        hc.setConnectionTimeout(8000);
        hc.setInitializationFailTimeout(8000);
        return new HikariDataSource(hc);
    }

    /** 按表单参数拼 MySQL 连接串（与 InstanceConfig 内保持一致） */
    public static String mysqlUrl(String host, String port, String database) {
        return "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                + "&useSSL=false&allowPublicKeyRetrieval=true";
    }
}
