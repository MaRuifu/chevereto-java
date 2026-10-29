package com.chevereto.java.service;

import com.chevereto.java.config.CheveretoCrypto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * reCAPTCHA v2 校验（原版 must_use_recaptcha 之后的 captcha 关卡）。
 *
 * <p>键名与原版一致：{@code recaptcha}（开关）、{@code recaptcha_public_key}、
 * {@code recaptcha_private_key}（原版列入 ENCRYPTED_NAMES，这里解密后使用）、
 * {@code recaptcha_threshold}（失败多少次后开始要求验证）。
 *
 * <p>设计取舍：开关打开但密钥没配全时<b>放行</b>并打警告。
 * 原版此时也不会渲染验证码组件，跟着放行才不会把整个登录入口锁死。
 */
@Service
public class CaptchaService {

    private static final Logger log = LoggerFactory.getLogger(CaptchaService.class);
    private static final String VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";

    private final SettingsService settingsService;

    public CaptchaService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    /** 前后端都就绪才算启用 */
    public boolean isEnabled() {
        return settingsService.getBool("recaptcha")
                && !siteKey().isBlank() && !privateKey().isBlank();
    }

    public String siteKey() {
        return settingsService.get("recaptcha_public_key");
    }

    private String privateKey() {
        return CheveretoCrypto.decryptIfNeeded(settingsService.get("recaptcha_private_key"));
    }

    /**
     * @param response 前端提交的 g-recaptcha-response
     * @return 是否通过；未启用时恒为 true
     */
    public boolean verify(String response, String ip) {
        if (!isEnabled()) {
            return true;
        }
        if (response == null || response.isBlank()) {
            return false;
        }
        try {
            String body = "secret=" + URLEncoder.encode(privateKey(), StandardCharsets.UTF_8)
                    + "&response=" + URLEncoder.encode(response, StandardCharsets.UTF_8)
                    + (ip == null || ip.isBlank() ? ""
                    : "&remoteip=" + URLEncoder.encode(ip, StandardCharsets.UTF_8));
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            HttpResponse<String> resp = client.send(
                    HttpRequest.newBuilder(URI.create(VERIFY_URL))
                            .timeout(Duration.ofSeconds(8))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            // 只做"成功与否"判断，不引入 JSON 解析依赖
            return resp.statusCode() == 200
                    && resp.body() != null
                    && java.util.regex.Pattern.compile("\"success\"\\s*:\\s*true").matcher(resp.body()).find();
        } catch (Exception e) {
            // 网络异常时不放行会让人完全登不上；这里按"未通过"处理并记录，由用户重试
            log.warn("reCAPTCHA 校验请求失败：{}", e.getMessage());
            return false;
        }
    }
}
