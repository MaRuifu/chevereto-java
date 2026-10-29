package com.chevereto.java.web;

import com.chevereto.java.config.CheveretoCrypto;
import com.chevereto.java.entity.Category;
import com.chevereto.java.entity.Image;
import com.chevereto.java.entity.Storage;
import com.chevereto.java.entity.StorageApi;
import com.chevereto.java.entity.User;
import com.chevereto.java.repository.CategoryRepository;
import com.chevereto.java.repository.ImageRepository;
import com.chevereto.java.repository.UserRepository;
import com.chevereto.java.service.ImageService;
import com.chevereto.java.service.IpBanService;
import com.chevereto.java.service.MailService;
import com.chevereto.java.service.NsfwPolicy;
import com.chevereto.java.service.RequestLogService;
import com.chevereto.java.service.SettingsService;
import com.chevereto.java.service.StatsService;
import com.chevereto.java.service.UserService;
import com.chevereto.java.repository.StorageApiRepository;
import com.chevereto.java.repository.StorageRepository;
import com.chevereto.java.storage.StorageManager;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** 管理后台（对齐原版 /dashboard：左侧菜单 + 状态/设置/用户/外部存储/分类/IP封禁/邮箱/内容/洪水） */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final ImageRepository imageRepository;
    private final CategoryRepository categoryRepository;
    private final StorageRepository storageRepository;
    private final StorageApiRepository storageApiRepository;
    private final ImageService imageService;
    private final SettingsService settingsService;
    private final IpBanService ipBanService;
    private final UserService userService;
    private final StorageManager storageManager;
    private final StatsService statsService;
    private final MailService mailService;
    private final RequestLogService requestLogService;
    private final NsfwPolicy nsfwPolicy;
    private final AuthSupport auth;

    public AdminController(UserRepository userRepository,
                           ImageRepository imageRepository,
                           CategoryRepository categoryRepository,
                           StorageRepository storageRepository,
                           StorageApiRepository storageApiRepository,
                           ImageService imageService, SettingsService settingsService,
                           IpBanService ipBanService, UserService userService,
                           StorageManager storageManager, StatsService statsService,
                           MailService mailService, RequestLogService requestLogService,
                           NsfwPolicy nsfwPolicy, AuthSupport auth) {
        this.userRepository = userRepository;
        this.imageRepository = imageRepository;
        this.categoryRepository = categoryRepository;
        this.storageRepository = storageRepository;
        this.storageApiRepository = storageApiRepository;
        this.imageService = imageService;
        this.settingsService = settingsService;
        this.ipBanService = ipBanService;
        this.userService = userService;
        this.storageManager = storageManager;
        this.statsService = statsService;
        this.mailService = mailService;
        this.requestLogService = requestLogService;
        this.nsfwPolicy = nsfwPolicy;
        this.auth = auth;
    }

    private User requireAdmin(HttpSession session) {
        return auth.requireAdmin(session);
    }

    // ---------------- 状态 ----------------

    /**
     * 仪表盘：累计数据取自 chv_stats 的 total 行（与原版一致，不是实时 COUNT），
     * 趋势图取自按日累计序列。因此需要时可用「重建统计」把历史数据回填。
     */
    @GetMapping
    public String dashboard(Model model, HttpSession session) {
        requireAdmin(session);
        auth.common(model, session);
        Map<String, Long> totals = statsService.totals();
        model.addAttribute("totals", totals);
        // 最近 30 天的按日增量，用于柱状图；maxDailyImages 让模板能算柱高百分比
        List<StatsService.DailyRow> daily = statsService.daily(30);
        model.addAttribute("daily", daily);
        model.addAttribute("maxDailyImages",
                daily.stream().mapToLong(StatsService.DailyRow::images).max().orElse(0));
        model.addAttribute("trend", statsService.cumulative(365));

        // 实时值仅用于「与统计值是否一致」的提示，不替代统计
        model.addAttribute("liveUserCount", userRepository.count());
        model.addAttribute("liveImageCount", imageRepository.count());

        var recent = imageRepository.findAll(PageRequest.of(0, 12)).getContent();
        imageService.populateAll(recent);
        model.addAttribute("recentImages", recent);
        model.addAttribute("nsfwBlur", nsfwPolicy.blurInListings());
        return "admin/dashboard";
    }

    /** 全量重算 chv_stats（原版 rebuildTotals，用于迁移历史库后回填） */
    @PostMapping("/stats/rebuild")
    public String rebuildStats(HttpSession session, RedirectAttributes ra) {
        requireAdmin(session);
        try {
            statsService.rebuild();
            ra.addFlashAttribute("success", "统计已按现有数据重建（累计 + 按日）");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "重建失败：" + e.getMessage());
        }
        return "redirect:/admin";
    }

    // ---------------- 设置 ----------------

    @GetMapping("/settings")
    public String settings(Model model, HttpSession session) {
        requireAdmin(session);
        auth.common(model, session);
        model.addAttribute("settings", settingsService.all());
        return "admin/settings";
    }

    @PostMapping("/settings")
    public String saveSettings(@RequestParam Map<String, String> form, HttpSession session, RedirectAttributes ra) {
        requireAdmin(session);
        // 布尔键：未出现在表单 = 未勾选 = 关闭（存 1/0，与原版一致）
        java.util.Set<String> boolKeys = java.util.Set.of(
                "guest_uploads", "upload_api_enabled", "enable_duplicate_uploads",
                "watermark_enable", "watermark_enable_guest", "watermark_enable_user",
                "watermark_enable_admin", "watermark_enable_file_gif");
        SettingsService.DEFAULTS.keySet().forEach(k -> {
            if (form.containsKey(k)) {
                settingsService.set(k, form.get(k));
            } else if (boolKeys.contains(k)) {
                settingsService.set(k, "0");
            }
        });
        ra.addFlashAttribute("success", "设置已保存");
        return "redirect:/admin/settings";
    }

    // ---------------- 邮箱 ----------------

    @GetMapping("/email")
    public String email(Model model, HttpSession session) {
        requireAdmin(session);
        auth.common(model, session);
        Map<String, String> settings = settingsService.all();
        // 敏感值不回显：账号解密后明文显示（方便核对），密码只回显占位符
        String username = settings.get("email_smtp_server_username");
        settings.put("email_smtp_server_username",
                username == null ? "" : CheveretoCrypto.decryptIfNeeded(username));
        boolean passwordSet = settings.get("email_smtp_server_password") != null
                && !settings.get("email_smtp_server_password").isBlank();
        settings.put("email_smtp_server_password", "");
        model.addAttribute("settings", settings);
        model.addAttribute("passwordSet", passwordSet);
        model.addAttribute("mailReady", mailService.isConfigured());
        return "admin/email";
    }

    /**
     * 保存邮箱配置。键名与原版完全一致，写入 chv_settings。
     * 密码留空 = 保持原值（避免后台把密文回显后又被原样存回）。
     */
    @PostMapping("/email")
    public String saveEmail(@RequestParam Map<String, String> form, HttpSession session,
                            RedirectAttributes ra) {
        requireAdmin(session);
        List<String> plainKeys = List.of(
                "email_mode", "email_smtp_server", "email_smtp_server_port",
                "email_smtp_server_security", "email_from_email", "email_from_name",
                "email_incoming_email");
        for (String k : plainKeys) {
            if (form.containsKey(k)) {
                settingsService.set(k, trim(form.get(k)));
            }
        }
        // 账号 / 密码：原版列入 ENCRYPTED_NAMES，写库前按需加密
        saveSecret(form, "email_smtp_server_username", false);
        saveSecret(form, "email_smtp_server_password", true);

        // 两个复选框：未勾选即 0（与原版一致）
        // require_user_email_confirmation 不在表单里：激活邮件/激活链接尚未实现，
        // 开了会让注册用户永远登不上，因此只读取不写入（见模板说明）
        settingsService.set("notify_user_signups",
                form.containsKey("notify_user_signups") ? "1" : "0");

        ra.addFlashAttribute("success", "邮箱配置已保存");
        return "redirect:/admin/email";
    }

    /** 敏感键保存：空值跳过（= 不修改），否则加密后写入 */
    private void saveSecret(Map<String, String> form, String key, boolean masked) {
        String raw = form.get(key);
        if (raw == null || raw.isBlank()) {
            return;
        }
        if (masked && raw.chars().allMatch(c -> c == '*')) {
            return; // 前端回显的掩码，视为未修改
        }
        settingsService.set(key, CheveretoCrypto.encryptIfNeeded(raw.trim()));
    }

    @PostMapping("/email/test")
    public String testEmail(@RequestParam String to, HttpSession session, RedirectAttributes ra) {
        requireAdmin(session);
        try {
            mailService.sendTest(to);
            ra.addFlashAttribute("success", "测试邮件已发送到 " + to);
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/email";
    }

    // ---------------- 内容 / NSFW ----------------

    @GetMapping("/content")
    public String content(Model model, HttpSession session) {
        requireAdmin(session);
        auth.common(model, session);
        Map<String, String> settings = settingsService.all();
        boolean keySet = settings.get("moderatecontent_key") != null
                && !settings.get("moderatecontent_key").isBlank();
        settings.put("moderatecontent_key", "");
        model.addAttribute("settings", settings);
        model.addAttribute("moderateKeySet", keySet);
        return "admin/content";
    }

    @PostMapping("/content")
    public String saveContent(@RequestParam Map<String, String> form, HttpSession session,
                              RedirectAttributes ra) {
        requireAdmin(session);
        // 全部是 select，值原样保存（空字符串是有意义的：moderatecontent_block_rating / moderate_uploads）
        for (String k : List.of(
                "show_nsfw_in_listings", "show_banners_in_nsfw", "show_nsfw_in_random_mode",
                "theme_nsfw_blur", "theme_nsfw_upload_checkbox", "image_lock_nsfw_editing",
                "moderate_uploads", "moderatecontent", "moderatecontent_flag_nsfw",
                "moderatecontent_block_rating", "moderatecontent_auto_approve")) {
            if (form.containsKey(k)) {
                settingsService.set(k, trim(form.get(k)));
            }
        }
        // API Key 走加密，留空不修改
        saveSecret(form, "moderatecontent_key", false);
        ra.addFlashAttribute("success", "内容设置已保存");
        return "redirect:/admin/content";
    }

    // ---------------- 洪水保护 ----------------

    @GetMapping("/flood")
    public String flood(Model model, HttpSession session) {
        requireAdmin(session);
        auth.common(model, session);
        model.addAttribute("settings", settingsService.all());
        long[] fail = requestLogService.failStats24h();
        model.addAttribute("failCount", fail[0]);
        model.addAttribute("failIps", fail[1]);
        return "admin/flood";
    }

    @PostMapping("/flood")
    public String saveFlood(@RequestParam Map<String, String> form, HttpSession session,
                            RedirectAttributes ra) {
        requireAdmin(session);
        for (String k : List.of("flood_uploads_protection", "flood_uploads_notify",
                "flood_uploads_minute", "flood_uploads_hour", "flood_uploads_day",
                "flood_uploads_week", "flood_uploads_month",
                "recaptcha", "recaptcha_threshold")) {
            if (form.containsKey(k)) {
                settingsService.set(k, trim(form.get(k)));
            }
        }
        ra.addFlashAttribute("success", "洪水保护设置已保存");
        return "redirect:/admin/flood";
    }

    private String trim(String s) {
        return s == null ? "" : s.trim();
    }

    // ---------------- 用户 ----------------

    @GetMapping("/users")
    public String users(@RequestParam(defaultValue = "1") int page, Model model, HttpSession session) {
        requireAdmin(session);
        auth.common(model, session);
        model.addAttribute("users", userRepository.findAll(PageRequest.of(page - 1, 30)));
        model.addAttribute("currentPage", page);
        return "admin/users";
    }

    @PostMapping("/users/{id}/toggle")
    public String toggleUser(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        User me = requireAdmin(session);
        if (me.getUserId().equals(id)) {
            ra.addFlashAttribute("error", "不能封禁自己");
            return "redirect:/admin/users";
        }
        User target = userRepository.findById(id).orElse(null);
        if (target == null) return "redirect:/admin/users";
        if (target.isUserIsAdmin()) {
            ra.addFlashAttribute("error", "请先取消该用户的管理员身份，再封禁");
            return "redirect:/admin/users";
        }
        userService.toggleBanned(id);
        ra.addFlashAttribute("success", "用户状态已更新");
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/role")
    public String changeRole(@PathVariable Long id, @RequestParam boolean admin,
                             HttpSession session, RedirectAttributes ra) {
        requireAdmin(session);
        User target = userRepository.findById(id).orElse(null);
        if (target == null) return "redirect:/admin/users";
        target.setUserIsAdmin(admin);
        userRepository.save(target);
        ra.addFlashAttribute("success", "角色已更新");
        return "redirect:/admin/users";
    }

    // ---------------- 外部存储 ----------------

    @GetMapping("/storages")
    public String storages(Model model, HttpSession session) {
        requireAdmin(session);
        auth.common(model, session);
        List<Storage> storages = storageRepository.findAllByOrderByStorageIdAsc();
        model.addAttribute("storages", storages);
        model.addAttribute("apis", storageApiRepository.findAll());
        // 每个存储的用量统计（张数 / 容量）；本地存储行合并 image_storage_id 为 NULL 的迁移老图
        java.util.HashMap<Long, long[]> stats = new java.util.HashMap<>();
        java.util.HashMap<Long, String> services = new java.util.HashMap<>();
        for (Storage s : storages) {
            boolean local = "local".equals(storageManager.resolveService(s));
            long count = imageRepository.countByStorageId(s.getStorageId());
            long bytes = imageRepository.sumSizeByStorageId(s.getStorageId());
            if (local) {
                count += imageRepository.countByStorageIdNull();
                bytes += imageRepository.sumSizeByStorageIdNull();
            }
            stats.put(s.getStorageId(), new long[]{count, bytes});
            services.put(s.getStorageId(), storageManager.resolveService(s));
        }
        // 本地隐式存储（库中无本地行但有 NULL 老图）
        long[] implicitStats = null;
        if (storages.stream().noneMatch(s -> "local".equals(services.get(s.getStorageId())))) {
            implicitStats = new long[]{imageRepository.countByStorageIdNull(), imageRepository.sumSizeByStorageIdNull()};
        }
        model.addAttribute("stats", stats);
        model.addAttribute("services", services);
        model.addAttribute("implicitStats", implicitStats);
        return "admin/storages";
    }

    /** 某个存储下的全部图片（对齐原版存储详情列表） */
    @GetMapping("/storages/{id}/images")
    public String storageImages(@PathVariable Long id, @RequestParam(defaultValue = "1") int page,
                                Model model, HttpSession session) {
        requireAdmin(session);
        auth.common(model, session);
        Storage storage = storageRepository.findById(id).orElse(null);
        if (storage == null) return "redirect:/admin/storages";
        boolean includeNull = "local".equals(storageManager.resolveService(storage));
        Page<Image> images = imageRepository.findByStorage(id, includeNull,
                PageRequest.of(Math.max(page - 1, 0), 24));
        imageService.populateAll(images.getContent());
        model.addAttribute("storage", storage);
        model.addAttribute("service", storageManager.resolveService(storage));
        model.addAttribute("images", images.getContent());
        model.addAttribute("currentPage", images.getNumber() + 1);
        model.addAttribute("totalPages", images.getTotalPages());
        return "admin/storage-images";
    }

    /** 编辑存储参数 */
    @GetMapping("/storages/{id}/edit")
    public String editStorageForm(@PathVariable Long id, Model model, HttpSession session) {
        requireAdmin(session);
        auth.common(model, session);
        Storage storage = storageRepository.findById(id).orElse(null);
        if (storage == null) return "redirect:/admin/storages";
        model.addAttribute("storage", storage);
        model.addAttribute("service", storageManager.resolveService(storage));
        return "admin/storage-edit";
    }

    @PostMapping("/storages/{id}/update")
    public String updateStorage(@PathVariable Long id, @RequestParam Map<String, String> form,
                                HttpSession session, RedirectAttributes ra) {
        requireAdmin(session);
        Storage storage = storageRepository.findById(id).orElse(null);
        if (storage == null) return "redirect:/admin/storages";
        if (form.containsKey("name") && !form.get("name").isBlank()) {
            storage.setStorageName(form.get("name").trim());
        }
        storage.setStorageUrl(form.getOrDefault("url", "") == null ? "" : form.getOrDefault("url", ""));
        storage.setStorageBucket(blankToNull(form.get("bucket")));
        storage.setStorageRegion(blankToNull(form.get("region")));
        storage.setStorageServer(blankToNull(form.get("server")));
        storage.setStorageAccountId(blankToNull(form.get("accountId")));
        // 凭证留空 = 保持不变（避免后台回显明文）
        if (form.get("key") != null && !form.get("key").isBlank()) storage.setStorageKey(form.get("key"));
        if (form.get("secret") != null && !form.get("secret").isBlank()) storage.setStorageSecret(form.get("secret"));
        if (form.containsKey("isActive")) {
            storage.setStorageIsActive("true".equalsIgnoreCase(form.get("isActive")) || "on".equalsIgnoreCase(form.get("isActive")));
        }
        storageRepository.save(storage);
        ra.addFlashAttribute("success", "存储「" + storage.getStorageName() + "」已更新");
        return "redirect:/admin/storages";
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    @PostMapping("/storages")
    public String createStorage(@RequestParam Map<String, String> form, HttpSession session, RedirectAttributes ra) {
        requireAdmin(session);
        StorageApi api = storageApiRepository.findById(Long.valueOf(form.getOrDefault("apiId", "1")))
                .orElseThrow();
        Storage storage = new Storage();
        storage.setStorageApiId(api.getStorageApiId());
        storage.setStorageName(form.getOrDefault("name", "未命名存储"));
        storage.setStorageService(api.getStorageApiType());
        storage.setStorageUrl(form.getOrDefault("url", "") == null ? "" : form.getOrDefault("url", ""));
        storage.setStorageBucket(blankToNull(form.get("bucket")));
        storage.setStorageRegion(blankToNull(form.get("region")));
        storage.setStorageServer(blankToNull(form.get("server")));
        storage.setStorageKey(blankToNull(form.get("key")));
        storage.setStorageSecret(blankToNull(form.get("secret")));
        storage.setStorageAccountId(blankToNull(form.get("accountId")));
        storage.setStorageIsHttps(true);
        storage.setStorageIsActive(true);
        storageRepository.save(storage);
        ra.addFlashAttribute("success", "存储「" + storage.getStorageName() + "」已添加");
        return "redirect:/admin/storages";
    }

    /** 启用/停用：停用只是不再接收新上传，已有图片照常展示（原版行为） */
    @PostMapping("/storages/{id}/toggle")
    public String toggleStorage(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        requireAdmin(session);
        Storage storage = storageRepository.findById(id).orElse(null);
        if (storage == null) return "redirect:/admin/storages";
        storage.setStorageIsActive(!storage.isStorageIsActive());
        storageRepository.save(storage);
        ra.addFlashAttribute("success", storage.isStorageIsActive()
                ? "存储已启用（将作为新上传目标）" : "存储已停用（已有图片不受影响，新上传改走其它启用存储）");
        return "redirect:/admin/storages";
    }

    @PostMapping("/storages/{id}/delete")
    public String deleteStorage(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        requireAdmin(session);
        Storage storage = storageRepository.findById(id).orElse(null);
        if (storage == null) return "redirect:/admin/storages";
        long inUse = imageRepository.countByStorageId(id);
        if (inUse > 0) {
            ra.addFlashAttribute("error", "该存储仍有 " + inUse + " 张图片使用，不能删除");
            return "redirect:/admin/storages";
        }
        storageRepository.delete(storage);
        ra.addFlashAttribute("success", "存储已删除");
        return "redirect:/admin/storages";
    }

    // ---------------- 分类 ----------------

    @GetMapping("/categories")
    public String categories(Model model, HttpSession session) {
        requireAdmin(session);
        auth.common(model, session);
        model.addAttribute("categories", categoryRepository.findAll());
        return "admin/categories";
    }

    @PostMapping("/categories")
    public String addCategory(@RequestParam String name, @RequestParam String urlKey,
                              HttpSession session, RedirectAttributes ra) {
        requireAdmin(session);
        if (name == null || name.isBlank() || urlKey == null || !urlKey.matches("[a-z0-9\\-]{1,32}")) {
            ra.addFlashAttribute("error", "名称不能为空，URL Key 只能是小写字母/数字/短横线（≤32）");
            return "redirect:/admin/categories";
        }
        Category category = new Category();
        category.setCategoryName(name.trim());
        category.setCategoryUrlKey(urlKey.trim());
        categoryRepository.save(category);
        ra.addFlashAttribute("success", "分类已添加");
        return "redirect:/admin/categories";
    }

    @PostMapping("/categories/{id}/delete")
    public String deleteCategory(@PathVariable Long id, HttpSession session) {
        requireAdmin(session);
        categoryRepository.deleteById(id);
        return "redirect:/admin/categories";
    }

    // ---------------- IP 封禁 ----------------

    @GetMapping("/bans")
    public String bans(Model model, HttpSession session) {
        requireAdmin(session);
        auth.common(model, session);
        model.addAttribute("bans", ipBanService.all());
        return "admin/bans";
    }

    @PostMapping("/bans")
    public String addBan(@RequestParam String ip,
                         @RequestParam(required = false) String message,
                         @RequestParam(required = false) String expires,
                         HttpSession session, RedirectAttributes ra) {
        requireAdmin(session);
        try {
            LocalDateTime expiresAt = expires == null || expires.isBlank()
                    ? null : LocalDateTime.parse(expires, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            ipBanService.add(ip, message, expiresAt);
            ra.addFlashAttribute("success", "封禁已添加");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            ra.addFlashAttribute("error", "过期时间格式应为 yyyy-MM-dd HH:mm");
        }
        return "redirect:/admin/bans";
    }

    @PostMapping("/bans/{id}/delete")
    public String removeBan(@PathVariable Long id, HttpSession session) {
        requireAdmin(session);
        ipBanService.remove(id);
        return "redirect:/admin/bans";
    }
}
