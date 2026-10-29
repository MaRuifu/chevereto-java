package com.chevereto.java.web;

import com.chevereto.java.entity.User;
import com.chevereto.java.service.SettingsService;
import com.chevereto.java.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** 会话与当前用户、客户端 IP 的通用辅助 */
@Component
public class AuthSupport {

    public static final String SESSION_USER_ID = "userId";

    private final UserService userService;
    private final SettingsService settingsService;

    public AuthSupport(UserService userService, SettingsService settingsService) {
        this.userService = userService;
        this.settingsService = settingsService;
    }

    public Optional<User> currentUser(HttpSession session) {
        if (session == null) {
            return Optional.empty();
        }
        Object uid = session.getAttribute(SESSION_USER_ID);
        if (uid instanceof Long id) {
            return userService.findById(id);
        }
        return Optional.empty();
    }

    public User requireUser(HttpSession session) {
        return currentUser(session).orElseThrow(() -> new ApiException("请先登录", HttpStatus.UNAUTHORIZED));
    }

    public User requireAdmin(HttpSession session) {
        User user = requireUser(session);
        if (!user.isUserIsAdmin()) {
            throw new ApiException("需要管理员权限", HttpStatus.FORBIDDEN);
        }
        return user;
    }

    /** 客户端 IP（支持反向代理 X-Forwarded-For / X-Real-IP） */
    public String clientIp(HttpServletRequest request) {
        String[] headers = {"X-Forwarded-For", "X-Real-IP"};
        for (String h : headers) {
            String v = request.getHeader(h);
            if (v != null && !v.isBlank()) {
                return v.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    /** 页面公共 model：站点名/描述/当前用户/游客上传 */
    public void common(org.springframework.ui.Model model, HttpSession session) {
        common(model, session, currentUser(session).orElse(null));
    }

    public void common(org.springframework.ui.Model model, HttpSession session, User user) {
        model.addAttribute("siteName", settingsService.get("website_name"));
        model.addAttribute("siteDescription", settingsService.get("website_description"));
        model.addAttribute("me", user);
        model.addAttribute("guestAllowed", settingsService.getBool("guest_uploads"));
        model.addAttribute("maxUploadMb", settingsService.getInt("upload_max_filesize_mb", 20));
    }
}
