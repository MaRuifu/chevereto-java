package com.chevereto.java.service;

import com.chevereto.java.entity.ApiKey;
import com.chevereto.java.entity.User;
import com.chevereto.java.repository.ApiKeyRepository;
import com.chevereto.java.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;

/**
 * API Key 服务（对齐原版 app/src/Legacy/Classes/ApiKey.php）。
 * <p>
 * 原版规则：
 * <ul>
 *   <li>Key 格式 = {@code chv_{encodeID(id)}_{随机串}}，id 是 api_key_id</li>
 *   <li>库里只存单向散列（原版 bcrypt，本实现沿用 MD5 以兼容既有数据），**不可逆**</li>
 *   <li>页面上永远只显示 {@code chv_{encodeID(id)}_***}，完整 Key 只在生成时出现一次</li>
 * </ul>
 */
@Service
public class ApiKeyService {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PREFIX = "chv_";

    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;
    private final CheveretoIdCodec idCodec;

    public ApiKeyService(ApiKeyRepository apiKeyRepository, UserRepository userRepository,
                         CheveretoIdCodec idCodec) {
        this.apiKeyRepository = apiKeyRepository;
        this.userRepository = userRepository;
        this.idCodec = idCodec;
    }

    public Optional<User> resolveUser(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return Optional.empty();
        }
        return apiKeyRepository.findByApiKeyHash(md5(rawKey.trim()))
                .map(ApiKey::getApiKeyUserId)
                .flatMap(userRepository::findById)
                .filter(u -> !"banned".equals(u.getUserStatus()));
    }

    public Optional<ApiKey> findByUser(User user) {
        return apiKeyRepository.findFirstByApiKeyUserId(user.getUserId());
    }

    /**
     * 生成（或重新生成）API Key。
     * 与原版一致：先插入空 hash 拿到自增 id，再用 id 组成 Key，最后回写 hash。
     *
     * @return 实体，apiKeyHash 字段临时回填明文（仅此一次可见）
     */
    @Transactional
    public ApiKey createOrRegenerate(User user) {
        apiKeyRepository.deleteByApiKeyUserId(user.getUserId());
        ApiKey apiKey = new ApiKey();
        apiKey.setApiKeyUserId(user.getUserId());
        apiKey.setApiKeyName("默认 Key");
        apiKey.setApiKeyHash("");
        ApiKey saved = apiKeyRepository.save(apiKey);

        String raw = PREFIX + idCodec.encode(saved.getApiKeyId()) + "_" + random(128);
        apiKeyRepository.updateHash(saved.getApiKeyId(), md5(raw));

        saved.setApiKeyHash(raw);
        return saved;
    }

    /**
     * 页面展示用：{@code chv_{encodeID(id)}_***}（原版 getUserPublic 行为），
     * 只用于辨认是哪一把 Key，不含任何真实密钥信息。
     */
    public String publicKey(ApiKey apiKey) {
        if (apiKey == null || apiKey.getApiKeyId() == null) {
            return null;
        }
        return PREFIX + idCodec.encode(apiKey.getApiKeyId()) + "_***";
    }

    public static String random(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    public static String md5(String input) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("MD5").digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
