package com.chevereto.java.entity;


import java.time.LocalDateTime;

/**
 * 对应 Chevereto 原版表 chv_images（字段名与原版 schema 完全一致）。
 * 扩展列（原版没有，Java 版需要）：image_privacy —— 无专辑图片的独立隐私级别。
 */
public class Image {

    private Long imageId;

    private String imageName;

    private String imageExtension;

    private long imageSize;

    private int imageWidth;

    private int imageHeight;

    private LocalDateTime imageDate = LocalDateTime.now();

    private LocalDateTime imageDateGmt = LocalDateTime.now();

    private String imageTitle;

    private String imageDescription;

    private boolean imageNsfw = false;

    private Long imageUserId;

    private Long imageAlbumId;

    private String imageUploaderIp = "";

    private String imageStorageMode = "datefolder";

    private String imagePath;

    private Long imageStorageId;

    private String imageMd5;

    private String imageSourceMd5;

    private String imageOriginalFilename;

    private long imageViews = 0;

    private Long imageCategoryId;

    private int imageThumbSize = 320;

    private int imageMediumSize = 0;

    private long imageLikes = 0;

    private boolean imageIsAnimated = false;

    private boolean imageIsApproved = true;

    // ---- 展示用 transient（不入库）----
    private String idEncoded;
    private String url;
    private String thumbUrl;
    private User user;
    private Album album;
    private Category category;

    /**
     * 删图口令明文（transient，不入库）：只在本次上传成功时持有，
     * 用于拼 {@code /image/<ID>/delete/<口令>} 返回给上传者（原版 Image::getDeleteUrl）。
     * 库里只存 bcrypt 哈希（chv_images_hash）。
     */
    private String deletePassword;

    public Long getImageId() { return imageId; }
    public void setImageId(Long imageId) { this.imageId = imageId; }
    public String getImageName() { return imageName; }
    public void setImageName(String imageName) { this.imageName = imageName; }
    public String getImageExtension() { return imageExtension; }
    public void setImageExtension(String imageExtension) { this.imageExtension = imageExtension; }
    public long getImageSize() { return imageSize; }
    public void setImageSize(long imageSize) { this.imageSize = imageSize; }
    public int getImageWidth() { return imageWidth; }
    public void setImageWidth(int imageWidth) { this.imageWidth = imageWidth; }
    public int getImageHeight() { return imageHeight; }
    public void setImageHeight(int imageHeight) { this.imageHeight = imageHeight; }
    public LocalDateTime getImageDate() { return imageDate; }
    public void setImageDate(LocalDateTime imageDate) { this.imageDate = imageDate; }
    public LocalDateTime getImageDateGmt() { return imageDateGmt; }
    public void setImageDateGmt(LocalDateTime imageDateGmt) { this.imageDateGmt = imageDateGmt; }
    public String getImageTitle() { return imageTitle; }
    public void setImageTitle(String imageTitle) { this.imageTitle = imageTitle; }
    public String getImageDescription() { return imageDescription; }
    public void setImageDescription(String imageDescription) { this.imageDescription = imageDescription; }
    public boolean isImageNsfw() { return imageNsfw; }
    public void setImageNsfw(boolean imageNsfw) { this.imageNsfw = imageNsfw; }
    public Long getImageUserId() { return imageUserId; }
    public void setImageUserId(Long imageUserId) { this.imageUserId = imageUserId; }
    public Long getImageAlbumId() { return imageAlbumId; }
    public void setImageAlbumId(Long imageAlbumId) { this.imageAlbumId = imageAlbumId; }
    public String getImageUploaderIp() { return imageUploaderIp; }
    public void setImageUploaderIp(String imageUploaderIp) { this.imageUploaderIp = imageUploaderIp; }
    public String getImageStorageMode() { return imageStorageMode; }
    public void setImageStorageMode(String imageStorageMode) { this.imageStorageMode = imageStorageMode; }
    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }
    public Long getImageStorageId() { return imageStorageId; }
    public void setImageStorageId(Long imageStorageId) { this.imageStorageId = imageStorageId; }
    public String getImageMd5() { return imageMd5; }
    public void setImageMd5(String imageMd5) { this.imageMd5 = imageMd5; }
    public String getImageSourceMd5() { return imageSourceMd5; }
    public void setImageSourceMd5(String imageSourceMd5) { this.imageSourceMd5 = imageSourceMd5; }
    public String getImageOriginalFilename() { return imageOriginalFilename; }
    public void setImageOriginalFilename(String imageOriginalFilename) { this.imageOriginalFilename = imageOriginalFilename; }
    public long getImageViews() { return imageViews; }
    public void setImageViews(long imageViews) { this.imageViews = imageViews; }
    public Long getImageCategoryId() { return imageCategoryId; }
    public void setImageCategoryId(Long imageCategoryId) { this.imageCategoryId = imageCategoryId; }
    public int getImageThumbSize() { return imageThumbSize; }
    public void setImageThumbSize(int imageThumbSize) { this.imageThumbSize = imageThumbSize; }
    public int getImageMediumSize() { return imageMediumSize; }
    public void setImageMediumSize(int imageMediumSize) { this.imageMediumSize = imageMediumSize; }
    public long getImageLikes() { return imageLikes; }
    public void setImageLikes(long imageLikes) { this.imageLikes = imageLikes; }
    public boolean isImageIsAnimated() { return imageIsAnimated; }
    public void setImageIsAnimated(boolean imageIsAnimated) { this.imageIsAnimated = imageIsAnimated; }
    public boolean isImageIsApproved() { return imageIsApproved; }
    public void setImageIsApproved(boolean imageIsApproved) { this.imageIsApproved = imageIsApproved; }
    public String getIdEncoded() { return idEncoded; }
    public void setIdEncoded(String idEncoded) { this.idEncoded = idEncoded; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getThumbUrl() { return thumbUrl; }
    public void setThumbUrl(String thumbUrl) { this.thumbUrl = thumbUrl; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Album getAlbum() { return album; }
    public void setAlbum(Album album) { this.album = album; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public String getDeletePassword() { return deletePassword; }
    public void setDeletePassword(String deletePassword) { this.deletePassword = deletePassword; }

    /** 删图链接（原版 Image::getDeleteUrl）：/image/&lt;ID&gt;/delete/&lt;口令&gt;，无口令时返回 null */
    public String getDeleteUrl() {
        if (deletePassword == null || deletePassword.isBlank() || idEncoded == null) return null;
        return "/image/" + idEncoded + "/delete/" + deletePassword;
    }

    /**
     * 磁盘/URL 相对路径。迁移自原版的历史数据 image_path 为 NULL（datefolder 模式
     * 原版不落库），此时按原版规则从 image_date 推导：Y/m/d/文件名.扩展名。
     */
    public String getDiskPath() {
        if (imagePath != null && !imagePath.isBlank()) {
            return imagePath;
        }
        String dateDir = imageDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        return dateDir + "/" + imageName + "." + imageExtension;
    }

    /** 缩略图路径派生（原版规则：原名 + .th. + 原扩展名，如 a.png → a.th.png） */
    public String getThumbPath() {
        String p = getDiskPath();
        int dot = p.lastIndexOf('.');
        String ext = dot > 0 ? p.substring(dot + 1) : "jpg";
        return (dot > 0 ? p.substring(0, dot) : p) + ".th." + ext;
    }
}
