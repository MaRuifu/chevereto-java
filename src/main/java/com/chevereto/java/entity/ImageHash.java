package com.chevereto.java.entity;

/**
 * 对应原版表 chv_images_hash —— 图片删图口令。
 *
 * <p>原版语义（app/src/Legacy/Classes/Image.php）：
 * <pre>
 *   $deletePassword = randomString(48);
 *   DB::insert('images_hash', ['image_id' =&gt; $uploaded_id, 'hash' =&gt; password_hash($deletePassword, PASSWORD_BCRYPT)]);
 * </pre>
 * 明文口令只在生成时返回给上传者一次，库里只存 bcrypt 哈希，
 * 凭 {@code /image/&lt;ID&gt;/delete/&lt;明文&gt;} 即可删除（无需登录），删图时一并删本行。
 */
public class ImageHash {

    /** 图片 ID（主键，一图最多一条口令） */
    private Long imageHashImageId;

    /** bcrypt 后的口令（PHP password_hash 产出 $2y$10$...） */
    private String imageHashHash;

    public Long getImageHashImageId() { return imageHashImageId; }
    public void setImageHashImageId(Long imageHashImageId) { this.imageHashImageId = imageHashImageId; }

    public String getImageHashHash() { return imageHashHash; }
    public void setImageHashHash(String imageHashHash) { this.imageHashHash = imageHashHash; }
}
