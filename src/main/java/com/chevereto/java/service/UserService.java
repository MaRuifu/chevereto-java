package com.chevereto.java.service;

import com.chevereto.java.entity.LoginPassword;
import com.chevereto.java.entity.User;
import com.chevereto.java.repository.LoginPasswordRepository;
import com.chevereto.java.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** 用户服务（密码哈希按原版规则存 chv_login_passwords 表） */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final LoginPasswordRepository loginPasswordRepository;
    private final SettingsService settingsService;
    private final StatsService statsService;
    private final MailService mailService;
    private final FloodGuard floodGuard;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public UserService(UserRepository userRepository,
                       LoginPasswordRepository loginPasswordRepository,
                       SettingsService settingsService, StatsService statsService,
                       MailService mailService, FloodGuard floodGuard) {
        this.userRepository = userRepository;
        this.loginPasswordRepository = loginPasswordRepository;
        this.settingsService = settingsService;
        this.statsService = statsService;
        this.mailService = mailService;
        this.floodGuard = floodGuard;
    }

    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUserUsername(username);
    }

    public List<User> all() {
        return userRepository.findAll();
    }

    @Transactional
    public User register(String username, String email, String password, String registrationIp) {
        if (username == null || username.isBlank() || !username.matches("[A-Za-z0-9_\\-\\u4e00-\\u9fa5]{2,32}")) {
            throw new IllegalArgumentException("用户名只能包含中文、字母、数字、下划线和短横线，长度 2-32");
        }
        if (email == null || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("邮箱格式不正确");
        }
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("密码至少 6 位");
        }
        if (userRepository.existsByUserUsername(username)) {
            throw new IllegalArgumentException("用户名已被占用");
        }
        if (userRepository.existsByUserEmail(email)) {
            throw new IllegalArgumentException("邮箱已被注册");
        }
        // 注册洪水：同一 IP 2 天内未激活账号 > 5 则拒绝（原版 User::insert）
        floodGuard.assertSignupAllowed(registrationIp);

        User user = new User();
        user.setUserUsername(username);
        user.setUserEmail(email);
        // 原版这一步会依据 require_user_email_confirmation 置为 awaiting-confirmation；
        // 本系统还没实现激活邮件与激活链接，置为待激活会让用户永远登不上，因此一律 valid。
        // 设置键仍在 chv_settings 里（与原版同名），后续补上激活流程时再启用。
        user.setUserStatus("valid");
        user.setUserRegistrationIp(registrationIp == null ? "" : registrationIp);
        user = userRepository.save(user);

        savePassword(user.getUserId(), password);
        statsService.userInserted(user.getUserDateGmt());
        notifyNewUser(user);
        return user;
    }

    /** 新用户注册通知（notify_user_signups）；发信失败不影响注册，只记日志 */
    private void notifyNewUser(User user) {
        if (!settingsService.getBool("notify_user_signups")) {
            return;
        }
        String to = mailService.incomingEmail();
        if (to == null || to.isBlank()) {
            return;
        }
        try {
            String html = "<p>新用户注册：<strong>" + escape(user.getUserUsername()) + "</strong></p>"
                    + "<p>邮箱：" + escape(user.getUserEmail()) + "</p>"
                    + "<p>状态：" + escape(user.getUserStatus()) + "</p>";
            mailService.send(to, "[新用户] " + user.getUserUsername(), html);
        } catch (Exception e) {
            log.warn("注册通知邮件发送失败：{}", e.getMessage());
        }
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public Optional<User> login(String identity, String password) {
        Optional<User> user = userRepository.findByUserUsername(identity);
        if (user.isEmpty()) {
            user = userRepository.findByUserEmail(identity);
        }
        // banned 与尚未激活（awaiting-confirmation / awaiting-email）的账号都不允许登录，与原版一致
        if (user.isPresent() && "valid".equals(user.get().getUserStatus())) {
            String hash = loginPasswordRepository.findByLoginPasswordUserId(user.get().getUserId())
                    .map(LoginPassword::getLoginPasswordHash).orElse(null);
            if (hash != null && matchesHash(password, hash)) {
                return user;
            }
        }
        return Optional.empty();
    }

    /**
     * 兼容原版 PHP password_hash 的 $2y$ 前缀（与 $2a$ 算法等价），
     * 迁移现有 Chevereto 数据库后老用户可直接登录。
     */
    private boolean matchesHash(String raw, String hash) {
        if (encoder.matches(raw, hash)) {
            return true;
        }
        return hash.startsWith("$2y$") && encoder.matches(raw, "$2a$" + hash.substring(4));
    }

    @Transactional
    public void changePassword(User user, String oldPassword, String newPassword) {
        String hash = loginPasswordRepository.findByLoginPasswordUserId(user.getUserId())
                .map(LoginPassword::getLoginPasswordHash).orElse(null);
        if (hash == null || !matchesHash(oldPassword, hash)) {
            throw new IllegalArgumentException("旧密码不正确");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("新密码至少 6 位");
        }
        savePassword(user.getUserId(), newPassword);
    }

    @Transactional
    public void savePassword(Long userId, String rawPassword) {
        LoginPassword lp = loginPasswordRepository.findByLoginPasswordUserId(userId)
                .orElseGet(LoginPassword::new);
        lp.setLoginPasswordUserId(userId);
        lp.setLoginPasswordHash(encoder.encode(rawPassword));
        loginPasswordRepository.save(lp);
    }

    @Transactional
    public void toggleBanned(Long id) {
        User user = userRepository.findById(id).orElseThrow();
        user.setUserStatus("banned".equals(user.getUserStatus()) ? "valid" : "banned");
        userRepository.save(user);
    }

    @Transactional
    public void updateProfile(User user, String name, String bio, String website, boolean showNsfw) {
        user.setUserName(name);
        user.setUserBio(bio);
        user.setUserWebsite(website);
        user.setUserShowNsfwListings(showNsfw);
        userRepository.save(user);
    }
}
