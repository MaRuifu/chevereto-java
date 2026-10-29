package com.chevereto.java.web;

import com.chevereto.java.entity.User;
import com.chevereto.java.service.CaptchaService;
import com.chevereto.java.service.FloodGuard;
import com.chevereto.java.service.IpBanService;
import com.chevereto.java.service.RequestLogService;
import com.chevereto.java.service.SettingsService;
import com.chevereto.java.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

/**
 * 登录 / 注册 / 退出（对齐原版 /login 双 tab 形式）。
 *
 * <p>洪水保护三件套在这里落地，顺序与原版 web.php 一致：
 * <ol>
 *   <li>IP 封禁（chv_ip_bans）→ 直接拒绝</li>
 *   <li>失败次数硬阈值（24h &gt; 25）→ 直接拒绝，不再校验密码</li>
 *   <li>失败次数软阈值（≥ recaptcha_threshold）→ 要求人机验证</li>
 * </ol>
 * 每次尝试都写一条 chv_requests，成功登录后清掉「同 IP + 同用户」的失败记录。
 */
@Controller
public class AuthController {

    private final UserService userService;
    private final IpBanService ipBanService;
    private final FloodGuard floodGuard;
    private final RequestLogService requestLogService;
    private final CaptchaService captchaService;
    private final SettingsService settingsService;
    private final AuthSupport auth;

    public AuthController(UserService userService, IpBanService ipBanService,
                          FloodGuard floodGuard, RequestLogService requestLogService,
                          CaptchaService captchaService, SettingsService settingsService,
                          AuthSupport auth) {
        this.userService = userService;
        this.ipBanService = ipBanService;
        this.floodGuard = floodGuard;
        this.requestLogService = requestLogService;
        this.captchaService = captchaService;
        this.settingsService = settingsService;
        this.auth = auth;
    }

    @GetMapping("/login")
    public String loginPage(Model model, HttpSession session, HttpServletRequest request) {
        if (auth.currentUser(session).isPresent()) {
            return "redirect:/settings";
        }
        auth.common(model, session);
        applyFloodState(model, auth.clientIp(request));
        return "login";
    }

    /** 把当前 IP 是否被限流、是否需要验证码、站点 key 传给页面 */
    private void applyFloodState(Model model, String ip) {
        boolean blocked = floodGuard.isBlocked(ip);
        boolean captcha = !blocked && floodGuard.needCaptcha(ip);
        model.addAttribute("floodBlocked", blocked);
        model.addAttribute("captchaNeeded", captcha);
        model.addAttribute("captchaSiteKey", captcha ? captchaService.siteKey() : "");
        model.addAttribute("signupsEnabled", settingsService.getBool("enable_signups"));
    }

    @PostMapping("/login")
    public String login(@RequestParam String identity,
                        @RequestParam String password,
                        @RequestParam(value = "g-recaptcha-response", required = false) String captcha,
                        HttpServletRequest request,
                        HttpSession session,
                        RedirectAttributes ra) {
        String ip = auth.clientIp(request);
        if (ipBanService.isBanned(ip)) {
            ra.addFlashAttribute("error", "你的 IP 已被封禁");
            return "redirect:/login";
        }
        // 硬阈值：24h 内登录+注册失败超过 25 次，连密码都不校验
        if (floodGuard.isBlocked(ip)) {
            ra.addFlashAttribute("error", "尝试次数过多（24 小时内失败超过 "
                    + FloodGuard.INVALID_REQUESTS_PER_DAY + " 次），请稍后再试");
            return "redirect:/login";
        }
        if (floodGuard.needCaptcha(ip)) {
            if (!captchaService.verify(captcha, ip)) {
                requestLogService.insert("login", "fail", null, null, ip);
                ra.addFlashAttribute("error", "人机验证未通过，请重试");
                return "redirect:/login";
            }
        }

        Optional<User> user = userService.login(identity, password);
        if (user.isEmpty()) {
            requestLogService.insert("login", "fail", null, null, ip);
            ra.addFlashAttribute("error", "用户名/邮箱或密码不正确，或账号未激活/已被封禁");
            return "redirect:/login";
        }
        requestLogService.insert("login", "success", user.get().getUserId(), null, ip);
        // 成功即清掉该 IP + 该用户的失败记录（原版行为：正常用户不会因为别人猜错被牵连）
        requestLogService.clearLoginFails(user.get().getUserId(), ip);
        session.setAttribute(AuthSupport.SESSION_USER_ID, user.get().getUserId());
        return "redirect:/";
    }

    @PostMapping("/register")
    public String register(@RequestParam String username,
                           @RequestParam String email,
                           @RequestParam String password,
                           @RequestParam String confirm,
                           @RequestParam(value = "g-recaptcha-response", required = false) String captcha,
                           HttpServletRequest request,
                           HttpSession session,
                           RedirectAttributes ra) {
        String ip = auth.clientIp(request);
        if (ipBanService.isBanned(ip)) {
            ra.addFlashAttribute("error", "你的 IP 已被封禁");
            return "redirect:/login";
        }
        if (!settingsService.getBool("enable_signups")) {
            ra.addFlashAttribute("error", "本站暂未开放注册");
            return "redirect:/login";
        }
        if (floodGuard.isBlocked(ip)) {
            ra.addFlashAttribute("error", "尝试次数过多（24 小时内失败超过 "
                    + FloodGuard.INVALID_REQUESTS_PER_DAY + " 次），请稍后再试");
            return "redirect:/login";
        }
        if (floodGuard.needCaptcha(ip) && !captchaService.verify(captcha, ip)) {
            requestLogService.insert("signup", "fail", null, null, ip);
            ra.addFlashAttribute("error", "人机验证未通过，请重试");
            return "redirect:/login";
        }
        if (password != null && !password.equals(confirm)) {
            requestLogService.insert("signup", "fail", null, null, ip);
            ra.addFlashAttribute("error", "两次输入的密码不一致");
            ra.addFlashAttribute("username", username);
            ra.addFlashAttribute("email", email);
            return "redirect:/login";
        }
        try {
            User user = userService.register(username, email, password, ip);
            requestLogService.insert("signup", "success", user.getUserId(), null, ip);
            session.setAttribute(AuthSupport.SESSION_USER_ID, user.getUserId());
            ra.addFlashAttribute("success", "注册成功，欢迎加入！");
            return "redirect:/";
        } catch (IllegalArgumentException e) {
            requestLogService.insert("signup", "fail", null, null, ip);
            ra.addFlashAttribute("error", e.getMessage());
            ra.addFlashAttribute("username", username);
            ra.addFlashAttribute("email", email);
            return "redirect:/login";
        }
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/";
    }
}
