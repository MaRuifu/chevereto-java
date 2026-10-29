package com.chevereto.java.service;

import com.chevereto.java.config.CheveretoCrypto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * 邮件发送：参数全部读 chv_settings 的 email_* 键（与原版同名同义）。
 *
 * <table>
 *   <tr><th>键</th><th>说明</th></tr>
 *   <tr><td>email_mode</td><td>smtp / mail（原版 PHP mail()，Java 侧无对应实现，回落 SMTP）</td></tr>
 *   <tr><td>email_smtp_server</td><td>SMTP 主机</td></tr>
 *   <tr><td>email_smtp_server_port</td><td>25 / 80 / 465 / 587</td></tr>
 *   <tr><td>email_smtp_server_security</td><td>tls / ssl / unsecured</td></tr>
 *   <tr><td>email_smtp_server_username</td><td>SMTP 账号</td></tr>
 *   <tr><td>email_smtp_server_password</td><td>SMTP 密码（原版可能加密，这里自动解密）</td></tr>
 *   <tr><td>email_from_email / email_from_name</td><td>发件人</td></tr>
 *   <tr><td>email_incoming_email</td><td>收件箱（洪水通知等寄给它）</td></tr>
 * </table>
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final SettingsService settingsService;

    public MailService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    /** 是否具备发送条件（主机 + 发件人） */
    public boolean isConfigured() {
        return !sv("email_smtp_server").isBlank() && !settingsService.get("email_from_email").isBlank();
    }

    /**
     * 读 email_smtp_server* 系列：原版把这些键列入 ENCRYPTED_NAMES，
     * 设了 CHEVERETO_ENCRYPTION_KEY 时库里是密文，这里统一解密。
     */
    private String sv(String key) {
        return CheveretoCrypto.decryptIfNeeded(settingsService.get(key));
    }

    public String fromEmail() {
        return settingsService.get("email_from_email");
    }

    public String incomingEmail() {
        return settingsService.get("email_incoming_email");
    }

    /** 同步发送 HTML 邮件；未配置时抛异常由调用方提示 */
    public void send(String to, String subject, String html) {
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("收件人为空");
        }
        if (!isConfigured()) {
            throw new IllegalStateException("邮件未配置：请先在「邮箱」页面填写 SMTP 主机与发件人地址");
        }
        JavaMailSenderImpl sender = buildSender();
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setTo(to);
            String fromName = settingsService.get("email_from_name");
            helper.setFrom(fromEmail(), fromName.isBlank() ? fromEmail() : fromName);
            helper.setSubject(subject);
            helper.setText(html, true);
            sender.send(message);
        } catch (Exception e) {
            // MessagingException / UnsupportedEncodingException / 运行时异常统一转成可展示的提示
            throw new IllegalStateException("发送邮件失败：" + e.getMessage(), e);
        }
    }

    /** 后台「测试发送」：把当前配置的完整过程暴露出来，便于排错 */
    public void sendTest(String to) {
        String siteName = settingsService.get("website_name");
        String html = "<p>这是一封来自 <strong>" + escape(siteName) + "</strong> 的测试邮件。</p>"
                + "<p>收到它说明 SMTP 配置可用。</p>"
                + "<p style=\"color:#888;font-size:12px\">主机：" + escape(sv("email_smtp_server"))
                + ":" + escape(sv("email_smtp_server_port"))
                + "，加密：" + escape(sv("email_smtp_server_security")) + "</p>";
        send(to, "[" + siteName + "] 邮件配置测试", html);
    }

    private JavaMailSenderImpl buildSender() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        String host = sv("email_smtp_server");
        sender.setHost(host);
        sender.setPort(parsePort(sv("email_smtp_server_port")));
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());

        String username = sv("email_smtp_server_username");
        String password = sv("email_smtp_server_password");
        if (!username.isBlank()) {
            sender.setUsername(username);
            sender.setPassword(password);
        }

        String security = sv("email_smtp_server_security");
        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.connectiontimeout", "8000");
        props.put("mail.smtp.timeout", "8000");
        props.put("mail.smtp.writetimeout", "8000");
        if ("ssl".equalsIgnoreCase(security)) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        } else if ("tls".equalsIgnoreCase(security)) {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        }
        if (!username.isBlank()) {
            props.put("mail.smtp.auth", "true");
        }
        log.debug("准备发信：host={} port={} security={} from={}", host,
                sender.getPort(), security, fromEmail());
        return sender;
    }

    private int parsePort(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 587;
        }
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
