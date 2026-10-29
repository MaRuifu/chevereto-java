package com.chevereto.java.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动初始化：
 * <ol>
 *   <li>始终建表（幂等）—— 保证向导页/登录页能正常渲染</li>
 *   <li>只有「实例已配置」时才播种默认数据；
 *       未配置（全新安装）留给 {@code /setup} 向导，用户选完库再播种，
 *       避免在用户还没选数据库前就往 H2 里写管理员和设置</li>
 * </ol>
 */
@Component
@Order(1)
public class StartupInitializer implements ApplicationRunner {

    private final InstanceConfig instanceConfig;
    private final SwitchableDataSource dataSource;
    private final InstanceBootstrap bootstrap;
    private final SchemaMigrator migrator;

    public StartupInitializer(InstanceConfig instanceConfig,
                              SwitchableDataSource dataSource,
                              InstanceBootstrap bootstrap,
                              SchemaMigrator migrator) {
        this.instanceConfig = instanceConfig;
        this.dataSource = dataSource;
        this.bootstrap = bootstrap;
        this.migrator = migrator;
    }

    @Override
    public void run(ApplicationArguments args) {
        dataSource.getClass(); // 确保代理已装配
        boolean h2 = instanceConfig.isH2();
        bootstrap.ensureSchema(dataSource, h2);
        // CREATE TABLE IF NOT EXISTS 不会动已存在的表，旧库/原版库的新增列靠这里补
        migrator.ensureColumns(h2);
        if (instanceConfig.isConfigured()) {
            bootstrap.initializeIfNeeded();
        } else {
            org.slf4j.LoggerFactory.getLogger(StartupInitializer.class)
                    .warn("实例尚未配置数据库，请访问 /setup 完成首次安装");
        }
    }
}
