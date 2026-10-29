package com.chevereto.java.config;

import javax.crypto.Cipher;
import javax.crypto.spec.ChaCha20ParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Chevereto 原版「设置值加密」兼容层。
 *
 * <p>原版对一批敏感设置（email_smtp_server / email_smtp_server_password /
 * recaptcha_private_key / moderatecontent_key / api_v1_key 等）做 ChaCha20 加密后入库，
 * 密钥来自环境变量 {@code CHEVERETO_ENCRYPTION_KEY}（base64）。
 * 未设置该环境变量时原版用 NullEncryption，库里就是明文。
 *
 * <p>因此这里做「能解就解，解不了就用原值」：
 * <ul>
 *   <li>未设置 CHEVERETO_ENCRYPTION_KEY → 直接当明文（与原版 NullEncryption 一致）</li>
 *   <li>设置了 → 尝试按 nonce(12B) + ciphertext 解密，失败则回退原值</li>
 * </ul>
 * 这样从原版迁移过来的库，无论开没开加密都能读到正确的 SMTP 密码。
 */
public final class CheveretoCrypto {

    private static final String ENV_KEY = "CHEVERETO_ENCRYPTION_KEY";
    private static final int NONCE_LENGTH = 12;

    private CheveretoCrypto() {
    }

    public static String decryptIfNeeded(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String base64Key = System.getenv(ENV_KEY);
        if (base64Key == null || base64Key.isBlank()) {
            return value;
        }
        try {
            byte[] key = Base64.getDecoder().decode(base64Key);
            byte[] payload = Base64.getDecoder().decode(value);
            if (payload.length <= NONCE_LENGTH) {
                return value;
            }
            byte[] nonce = new byte[NONCE_LENGTH];
            System.arraycopy(payload, 0, nonce, 0, NONCE_LENGTH);
            byte[] cipherText = new byte[payload.length - NONCE_LENGTH];
            System.arraycopy(payload, NONCE_LENGTH, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance("ChaCha20");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "ChaCha20"),
                    new ChaCha20ParameterSpec(nonce, 0));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 密钥不匹配或值本就是明文：按明文处理，避免把站点卡死
            return value;
        }
    }

    /**
     * 写入前的加密（原版 Settings::set 对 ENCRYPTED_NAMES 的处理）。
     * 未设置 {@code CHEVERETO_ENCRYPTION_KEY} 时原样返回，与 NullEncryption 一致。
     * 随机 nonce 前置于密文，格式与原版可互相读取。
     */
    public static String encryptIfNeeded(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String base64Key = System.getenv(ENV_KEY);
        if (base64Key == null || base64Key.isBlank()) {
            return value;
        }
        try {
            byte[] key = Base64.getDecoder().decode(base64Key);
            byte[] nonce = new byte[NONCE_LENGTH];
            new java.security.SecureRandom().nextBytes(nonce);

            Cipher cipher = Cipher.getInstance("ChaCha20");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "ChaCha20"),
                    new ChaCha20ParameterSpec(nonce, 0));
            byte[] cipherText = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));

            byte[] payload = new byte[NONCE_LENGTH + cipherText.length];
            System.arraycopy(nonce, 0, payload, 0, NONCE_LENGTH);
            System.arraycopy(cipherText, 0, payload, NONCE_LENGTH, cipherText.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (Exception e) {
            // 加密失败就存明文（与原版 NullEncryption 行为一致，不影响发送）
            return value;
        }
    }

    /** 该键的值是否属于原版 ENCRYPTED_NAMES（前缀 {@code email_smtp_server} 等） */
    public static boolean isEncryptedSetting(String key) {
        if (key == null) {
            return false;
        }
        return key.startsWith("email_smtp_server")
                || key.equals("recaptcha_private_key")
                || key.equals("moderatecontent_key")
                || key.equals("api_v1_key");
    }
}
