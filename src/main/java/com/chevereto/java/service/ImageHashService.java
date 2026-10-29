package com.chevereto.java.service;

import com.chevereto.java.entity.ImageHash;
import com.chevereto.java.repository.ImageHashRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Optional;

/**
 * 图片删图口令（对齐原版 app/src/Legacy/Classes/Image.php）。
 *
 * <ul>
 *   <li>上传成功后 {@code randomString(48)} 生成本地口令，bcrypt 后存 chv_images_hash，
 *       明文仅返回给上传者一次（库中不可反推）；</li>
 *   <li>{@code GET /image/&lt;ID&gt;/delete/&lt;明文&gt;} 校验口令即可删图，<b>无需登录</b> ——
 *       原版 {@code Image::verifyPassword()} + {@code Image::delete()} 的等价实现，
 *       这是原版给游客上传者留的「凭链接删图」通道；</li>
 *   <li>删除图片时同步删除口令行，避免孤儿数据。</li>
 * </ul>
 */
@Service
public class ImageHashService {

    /** 与 PHP randomString(48) 等长；字符集取 URL 安全集合，避免口令出现在路径里需要转义 */
    private static final int PASSWORD_LENGTH = 48;
    private static final char[] ALPHABET =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();

    private final ImageHashRepository imageHashRepository;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();

    public ImageHashService(ImageHashRepository imageHashRepository) {
        this.imageHashRepository = imageHashRepository;
    }

    /**
     * 为图片签发删图口令。
     *
     * @return 明文口令（仅此一次可见），图片 ID 为空时返回 null（不入库）
     */
    public String issue(Long imageId) {
        if (imageId == null) return null;
        String password = randomString(PASSWORD_LENGTH);
        ImageHash row = new ImageHash();
        row.setImageHashImageId(imageId);
        row.setImageHashHash(encoder.encode(password));
        imageHashRepository.save(row);
        return password;
    }

    /**
     * 校验口令。对齐原版 {@code Image::verifyPassword()}：
     * 没有口令行 = false；兼容 PHP 产出的 {@code $2y$} 前缀（与 {@code $2a$} 算法等价）。
     */
    public boolean verify(Long imageId, String password) {
        if (imageId == null || password == null || password.isBlank()) {
            return false;
        }
        Optional<ImageHash> row = imageHashRepository.findById(imageId);
        if (row.isEmpty() || row.get().getImageHashHash() == null) {
            return false;
        }
        String hash = row.get().getImageHashHash();
        if (encoder.matches(password, hash)) {
            return true;
        }
        return hash.startsWith("$2y$") && encoder.matches(password, "$2a$" + hash.substring(4));
    }

    /** 图片被删除时同步清除口令行（原版 Image::delete 里的 DB::delete('images_hash', ...)） */
    public void revoke(Long imageId) {
        if (imageId != null) {
            imageHashRepository.deleteByImageId(imageId);
        }
    }

    /** 是否已签发过口令 */
    public boolean exists(Long imageId) {
        return imageId != null && imageHashRepository.findById(imageId).isPresent();
    }

    private String randomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
