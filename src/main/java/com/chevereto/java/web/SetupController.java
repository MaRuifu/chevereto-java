package com.chevereto.java.web;

import com.chevereto.java.config.DataSourceConfig;
import com.chevereto.java.config.InstanceBootstrap;
import com.chevereto.java.config.InstanceConfig;
import com.chevereto.java.config.SchemaMigrator;
import com.chevereto.java.config.SwitchableDataSource;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.sql.Connection;

/**
 * 首次安装向导：让用户选择内置 H2 还是外部 MySQL。
 *
 * <p>规则（对齐需求）：
 * <ul>
 *   <li>已存在 instance.properties → 直接跳过向导</li>
 *   <li>选中的库如果已经有数据 → 只建缺失的表，不覆盖、不播种任何数据</li>
 *   <li>选中的库是空的 → 建表 + 播种默认设置/管理员/存储/分类/统计行</li>
 * </ul>
 */
@Controller
public class SetupController {

    private static final Logger log = LoggerFactory.getLogger(SetupController.class);

    private final InstanceConfig instanceConfig;
    private final SwitchableDataSource dataSource;
    private final InstanceBootstrap bootstrap;
    private final SchemaMigrator migrator;

    public SetupController(InstanceConfig instanceConfig,
                           SwitchableDataSource dataSource,
                           InstanceBootstrap bootstrap,
                           SchemaMigrator migrator) {
        this.instanceConfig = instanceConfig;
        this.dataSource = dataSource;
        this.bootstrap = bootstrap;
        this.migrator = migrator;
    }

    @GetMapping("/setup")
    public String page(Model model) {
        if (instanceConfig.isConfigured()) {
            return "redirect:/";
        }
        model.addAttribute("h2Path", InstanceConfig.H2_FILE);
        model.addAttribute("host", "127.0.0.1");
        model.addAttribute("port", "3306");
        model.addAttribute("database", "chevereto");
        model.addAttribute("username", "root");
        return "setup";
    }

    @PostMapping("/setup")
    public String submit(@RequestParam String type,
                         @RequestParam(required = false) String host,
                         @RequestParam(required = false) String port,
                         @RequestParam(required = false) String database,
                         @RequestParam(required = false) String username,
                         @RequestParam(required = false) String password,
                         HttpSession session,
                         Model model) {
        if (instanceConfig.isConfigured()) {
            return "redirect:/";
        }
        if ("h2".equalsIgnoreCase(type)) {
            return setupH2(session);
        }
        return setupMysql(host, port, database, username, password, session, model);
    }

    private String setupH2(HttpSession session) {
        instanceConfig.saveH2();
        boolean created = bootstrap.initializeIfNeeded();
        session.invalidate();
        log.info("首次安装：已选择内置 H2 数据库（{}），{}", InstanceConfig.H2_FILE,
                created ? "已初始化默认数据" : "检测到已有数据，跳过初始化");
        return "redirect:/login?setup=1";
    }

    private String setupMysql(String host, String port, String database, String username,
                              String password, HttpSession session, Model model) {
        host = trim(host, "127.0.0.1");
        port = trim(port, "3306");
        database = trim(database, "chevereto");
        username = trim(username, "root");
        String url = DataSourceConfig.mysqlUrl(host, port, database);

        HikariDataSource candidate = null;
        try {
            candidate = DataSourceConfig.buildMysql(url, username, password, 6);
            try (Connection ignored = candidate.getConnection()) {
                // 连通性测试：连不上会在这里抛 SQLException
            }
        } catch (Exception e) {
            if (candidate != null) {
                candidate.close();
            }
            model.addAttribute("error", "连接 MySQL 失败：" + rootMessage(e)
                    + "（请检查主机/端口/库名/账号密码，以及该库是否已创建）");
            return pageWithForm(model, host, port, database, username);
        }

        try {
            bootstrap.ensureSchema(candidate, false);
            // 原版 Chevereto 库缺本项目新增的列，切过去之前先把列补齐
            migrator.ensureColumns(candidate, false);
            dataSource.switchTo(candidate);
            boolean created = bootstrap.initializeIfNeeded();
            instanceConfig.saveMysql(host, port, database, username, password);
            dataSource.closePrevious();
            if (session != null) {
                session.invalidate();
            }
            log.info("首次安装：已切换到 MySQL {}/{}，{}", host + ":" + port, database,
                    created ? "已初始化默认数据" : "检测到已有数据，跳过初始化");
            return "redirect:/login?setup=1";
        } catch (Exception e) {
            // 切换失败：回滚到原 H2，避免半初始化状态
            try {
                dataSource.switchTo(DataSourceConfig.buildH2());
                dataSource.closePrevious();
            } catch (Exception ignored) {
                // 回滚失败也不再叠加异常
            }
            model.addAttribute("error", "初始化 MySQL 失败：" + rootMessage(e));
            return pageWithForm(model, host, port, database, username);
        }
    }

    private String pageWithForm(Model model, String host, String port, String database, String username) {
        model.addAttribute("h2Path", InstanceConfig.H2_FILE);
        model.addAttribute("host", host);
        model.addAttribute("port", port);
        model.addAttribute("database", database);
        model.addAttribute("username", username);
        return "setup";
    }

    private String trim(String v, String def) {
        return v == null || v.isBlank() ? def : v.trim();
    }

    private String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        String m = t.getMessage();
        return m == null ? t.getClass().getSimpleName() : m;
    }
}
