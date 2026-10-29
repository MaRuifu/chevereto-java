package com.chevereto.java.config;

import com.chevereto.java.entity.Category;
import com.chevereto.java.entity.Storage;
import com.chevereto.java.entity.StorageApi;
import com.chevereto.java.entity.User;
import com.chevereto.java.repository.CategoryRepository;
import com.chevereto.java.repository.StorageApiRepository;
import com.chevereto.java.repository.StorageRepository;
import com.chevereto.java.repository.UserRepository;
import com.chevereto.java.service.SettingsService;
import com.chevereto.java.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;

/**
 * 实例初始化：建表 + 首次数据播种。
 *
 * <p>两个独立动作，分别对应不同场景：
 * <ul>
 *   <li>{@link #ensureSchema} —— 建表（幂等，H2 与 MySQL 各用一份方言脚本）</li>
 *   <li>{@link #initializeIfNeeded} —— 播种默认设置/管理员/存储驱动/分类/统计行，
 *       仅在「库是空的」时执行；连上已有 Chevereto 库时整段跳过，保证零写入</li>
 * </ul>
 */
@Component
public class InstanceBootstrap {

    private static final Logger log = LoggerFactory.getLogger(InstanceBootstrap.class);

    private final UserRepository userRepository;
    private final StorageRepository storageRepository;
    private final StorageApiRepository storageApiRepository;
    private final CategoryRepository categoryRepository;
    private final SettingsService settingsService;
    private final UserService userService;
    private final JdbcTemplate jdbcTemplate;

    public InstanceBootstrap(UserRepository userRepository,
                             StorageRepository storageRepository,
                             StorageApiRepository storageApiRepository,
                             CategoryRepository categoryRepository,
                             SettingsService settingsService,
                             UserService userService,
                             JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.storageRepository = storageRepository;
        this.storageApiRepository = storageApiRepository;
        this.categoryRepository = categoryRepository;
        this.settingsService = settingsService;
        this.userService = userService;
        this.jdbcTemplate = jdbcTemplate;
    }

    // ---------------- 建表 ----------------

    /** 按方言执行建表脚本（CREATE TABLE IF NOT EXISTS，对已有库零改动） */
    public void ensureSchema(DataSource dataSource, boolean h2) {
        String script = h2 ? "schema-h2.sql" : "schema-mysql.sql";
        try (Connection conn = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(conn,
                    new EncodedResource(new ClassPathResource(script), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("执行建表脚本失败（" + script + "）：" + e.getMessage(), e);
        }
        log.info("表结构已就绪（{}）", script);
    }

    // ---------------- 数据播种 ----------------

    /** 库中已有业务数据则不写入任何内容（无缝迁移 / 向导里选了带数据的库） */
    @Transactional
    public boolean initializeIfNeeded() {
        long users = userRepository.count();
        long storages = storageRepository.count();
        String installed = settingsService.getRaw("chevereto_version_installed");
        boolean existing = users > 0 || storages > 0 || installed != null;
        if (existing) {
            log.info("检测到已有数据（用户 {} / 存储 {}），跳过初始化，不写入任何数据", users, storages);
            ensureStatTotalRow();
            return false;
        }
        initSettings();
        // 必须先建 stats 的 total 行：initAdmin 走 UserService.register 会写站点统计，
        // 那时若没有 total 行，累加会静默丢失（UPDATE 影响 0 行）
        ensureStatTotalRow();
        initAdmin();
        initStorageApis();
        initDefaultStorage();
        initCategories();
        return true;
    }

    private void initSettings() {
        SettingsService.DEFAULTS.forEach((k, v) -> {
            if (settingsService.getRaw(k) == null) {
                settingsService.set(k, v);
            }
        });
        // 原版安装器：crypt_salt 为随机 8 位字符串
        if (settingsService.getRaw("crypt_salt") == null) {
            settingsService.set("crypt_salt", com.chevereto.java.service.ApiKeyService.random(8));
        }
        // 与原版 install a:1:{...} 对齐的版本标记，供后续升级判断
        if (settingsService.getRaw("chevereto_version_installed") == null) {
            settingsService.set("chevereto_version_installed", "4.0.7");
        }
    }

    private void initAdmin() {
        if (userRepository.count() == 0) {
            User admin = userService.register("admin", "admin@example.com", "admin123", "");
            admin.setUserIsAdmin(true);
            userRepository.save(admin);
            log.warn("已创建默认管理员账号：admin / admin123 （请登录后立即修改密码！）");
        }
    }

    private void initStorageApis() {
        if (storageApiRepository.count() == 0) {
            String[][] apis = {
                    {"local", "本地存储"},
                    {"s3", "S3 兼容 (AWS/MinIO)"},
                    {"oss", "阿里云 OSS"},
                    {"cos", "腾讯云 COS"},
                    {"ftp", "FTP"},
                    {"sftp", "SFTP"},
            };
            for (String[] api : apis) {
                StorageApi sa = new StorageApi();
                sa.setStorageApiType(api[0]);
                sa.setStorageApiName(api[1]);
                storageApiRepository.save(sa);
            }
        } else {
            ensureCosApi();
        }
    }

    /** 幂等补种腾讯云 COS 驱动：原版表里没有该行，但本项目后端支持 cos 驱动 */
    public void ensureCosApi() {
        boolean hasCos = storageApiRepository.findAll().stream()
                .anyMatch(a -> "cos".equalsIgnoreCase(a.getStorageApiType()));
        if (!hasCos) {
            StorageApi cos = new StorageApi();
            cos.setStorageApiType("cos");
            cos.setStorageApiName("腾讯云 COS");
            storageApiRepository.save(cos);
            log.info("已注册存储驱动：腾讯云 COS（chv_storage_apis 新增一行）");
        }
    }

    private void initDefaultStorage() {
        if (storageRepository.count() == 0) {
            Storage local = new Storage();
            local.setStorageApiId(1L);
            local.setStorageName("本地存储");
            local.setStorageService("local");
            local.setStorageUrl("");
            local.setStorageServer("./data/uploads");
            local.setStorageIsActive(true);
            storageRepository.save(local);
            log.info("已创建默认本地存储：{}", local.getStorageServer());
        }
    }

    private void initCategories() {
        if (categoryRepository.count() == 0) {
            String[][] cats = {
                    {"动物", "animals"},
                    {"风景", "nature"},
                    {"人物", "people"},
                    {"科技", "technology"},
                    {"其他", "other"},
            };
            for (String[] c : cats) {
                Category category = new Category();
                category.setCategoryName(c[0]);
                category.setCategoryUrlKey(c[1]);
                categoryRepository.save(category);
            }
        }
    }

    /**
     * chv_stats 的 total 行是统计的基准行（原版安装时插一行 stat_id=1, stat_type='total'）。
     * 已有库可能因历史原因缺失，这里幂等补齐。
     */
    public void ensureStatTotalRow() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chv_stats WHERE stat_type = 'total'", Integer.class);
        if (count == null || count == 0) {
            jdbcTemplate.update("INSERT INTO chv_stats (stat_type, stat_date_gmt) VALUES ('total', NULL)");
        }
    }
}
