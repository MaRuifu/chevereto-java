package com.chevereto.java.service;

import com.chevereto.java.entity.Album;
import com.chevereto.java.entity.Category;
import com.chevereto.java.entity.Image;
import com.chevereto.java.entity.Storage;
import com.chevereto.java.entity.User;
import com.chevereto.java.repository.AlbumRepository;
import com.chevereto.java.repository.CategoryRepository;
import com.chevereto.java.repository.ImageRepository;
import com.chevereto.java.repository.StorageRepository;
import com.chevereto.java.repository.UserRepository;
import com.chevereto.java.storage.StorageManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ImageService {

    private final ImageRepository imageRepository;
    private final AlbumRepository albumRepository;
    private final CategoryRepository categoryRepository;
    private final StorageRepository storageRepository;
    private final UserRepository userRepository;
    private final StorageManager storageManager;
    private final SettingsService settingsService;
    private final ImageUtils imageUtils;
    private final CheveretoIdCodec idCodec;
    private final IpBanService ipBanService;
    private final WatermarkService watermarkService;
    private final NsfwPolicy nsfwPolicy;
    private final StatsService statsService;
    private final ImageHashService imageHashService;

    public ImageService(ImageRepository imageRepository, AlbumRepository albumRepository,
                        CategoryRepository categoryRepository, StorageRepository storageRepository,
                        UserRepository userRepository, StorageManager storageManager,
                        SettingsService settingsService, ImageUtils imageUtils,
                        CheveretoIdCodec idCodec, IpBanService ipBanService,
                        WatermarkService watermarkService, NsfwPolicy nsfwPolicy,
                        StatsService statsService, ImageHashService imageHashService) {
        this.imageRepository = imageRepository;
        this.albumRepository = albumRepository;
        this.categoryRepository = categoryRepository;
        this.storageRepository = storageRepository;
        this.userRepository = userRepository;
        this.storageManager = storageManager;
        this.settingsService = settingsService;
        this.imageUtils = imageUtils;
        this.idCodec = idCodec;
        this.ipBanService = ipBanService;
        this.watermarkService = watermarkService;
        this.nsfwPolicy = nsfwPolicy;
        this.statsService = statsService;
        this.imageHashService = imageHashService;
    }

    // ---------------- 上传 ----------------

    @Transactional
    public Image upload(MultipartFile file, User uploader, Long albumId, Long categoryId,
                        String title, boolean nsfw, String ip) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的图片");
        }
        if (ipBanService.isBanned(ip)) {
            throw new IllegalArgumentException("你的 IP 已被封禁");
        }
        int maxMb = settingsService.getInt("upload_max_filesize_mb", 20);
        if (file.getSize() > maxMb * 1024L * 1024L) {
            throw new IllegalArgumentException("文件超过大小限制（最大 " + maxMb + " MB）");
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("读取上传文件失败");
        }
        return uploadBytes(data, file.getOriginalFilename(), uploader, albumId, categoryId, title, nsfw, ip);
    }

    @Transactional
    public Image uploadBytes(byte[] data, String originalName, User uploader, Long albumId, Long categoryId,
                             String title, boolean nsfw, String ip) {
        ImageUtils.ImageType type = imageUtils.sniff(data);

        int maxMb = settingsService.getInt("upload_max_filesize_mb", 20);
        if (data.length > maxMb * 1024L * 1024L) {
            throw new IllegalArgumentException("文件超过大小限制（最大 " + maxMb + " MB）");
        }

        String md5 = md5(data);
        String sourceMd5 = md5;

        // ---- 重复上传检测（对齐原版 Image::isDuplicatedUpload + uploadToWebsite）----
        // $do_dupe_check = !getSetting('enable_duplicate_uploads') && !($user['is_admin'] ?? false);
        // if ($do_dupe_check && self::isDuplicatedUpload($source)) throw new Exception(_s('Duplicated upload'), 101);
        // 原版语义：命中即「拒绝上传」，不是秒传返回已有图片；窗口为同一 IP 的 24 小时内。
        boolean isAdmin = uploader != null && uploader.isUserIsAdmin();
        if (!settingsService.getBool("enable_duplicate_uploads") && !isAdmin
                && ip != null && !ip.isBlank()
                && imageRepository.countDuplicatedUpload(
                        md5, ip, LocalDateTime.now(ZoneOffset.UTC).minusDays(1)) > 0) {
            throw new IllegalArgumentException("重复上传：24 小时内该图片已经上传过了");
        }

        Image image = new Image();
        image.setImageName(safeName(originalName, type.ext()));
        image.setImageExtension(type.ext());
        image.setImageOriginalFilename(safeName(originalName, type.ext()));
        image.setImageTitle(truncate(title == null || title.isBlank() ? stripExt(safeName(originalName, type.ext())) : title, 100));
        image.setImageSize(data.length);
        image.setImageMd5(md5);
        image.setImageNsfw(nsfw);
        image.setImageUploaderIp(ip == null ? "" : ip);
        image.setImageStorageMode("datefolder");
        image.setImageThumbSize(settingsService.getInt("thumbnail_size", 320));

        if (uploader != null) {
            image.setImageUserId(uploader.getUserId());
        }
        if (albumId != null && albumId > 0) {
            Album album = albumRepository.findById(albumId).orElseThrow();
            image.setImageAlbumId(album.getAlbumId());
        }
        if (categoryId != null && categoryId > 0) {
            image.setImageCategoryId(categoryId);
        }

        int[] dims = imageUtils.dimensions(data);
        image.setImageWidth(dims[0]);
        image.setImageHeight(dims[1]);

        // ---- 水印（原版管线：按角色开关、最小尺寸、GIF/动图跳过）----
        boolean animated = imageUtils.isAnimated(data, type.ext());
        if (watermarkService.shouldApply(dims[0], dims[1], type.ext(), animated, isAdmin, uploader != null)) {
            byte[] marked = watermarkService.apply(data, type.ext(), dims[0], dims[1]);
            if (marked != data && marked.length > 0) {
                data = marked;
                image.setImageSize(data.length);
                md5 = md5(data); // 原版：image_md5 为打水印后的文件 md5，source_md5 保留原始值
            }
        }
        image.setImageMd5(md5);
        image.setImageSourceMd5(sourceMd5);

        Storage storage = storageManager.defaultStorage();
        image.setImageStorageId(storage.getStorageId());

        LocalDate now = LocalDate.now();
        String dateDir = now.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String path = dateDir + "/" + imageUtils.randomBasename() + "." + type.ext();
        image.setImagePath(path);

        int thumbSize = image.getImageThumbSize();
        storageManager.store(storage, path, data);
        storageManager.store(storage, image.getThumbPath(), imageUtils.thumbnail(data, thumbSize, type.ext()));

        image = imageRepository.save(image);

        // 删图口令（原版：$deletePassword = randomString(48) → password_hash(BCRYPT) → insert images_hash）
        // 明文只在此刻交给上传者一次，库里仅存哈希；凭 /image/<ID>/delete/<口令> 即可删图（无需登录）
        image.setDeletePassword(imageHashService.issue(image.getImageId()));

        // 计数维护（与原版 album_image_count / user_image_count 一致）
        if (image.getImageAlbumId() != null) {
            albumRepository.findById(image.getImageAlbumId()).ifPresent(a -> {
                a.setAlbumImageCount(a.getAlbumImageCount() + 1);
                albumRepository.save(a);
            });
        }
        if (image.getImageUserId() != null) {
            userRepository.findById(image.getImageUserId()).ifPresent(u -> {
                u.setUserImageCount(u.getUserImageCount() + 1);
                userRepository.save(u);
            });
        }

        // 站点统计（原版 Stat::track('image', 'insert')）：只有真正新建的图片才计数，
        // 重复上传在上面的查重分支就已抛异常返回，不会走到这里
        statsService.imageInserted(image);

        populate(image);
        return image;
    }

    // ---------------- 查询 ----------------

    /**
     * 公开列表（首页「最新图片」）。
     *
     * @param allowNsfw 当前访问者能否看到 NSFW（由 {@link NsfwPolicy#allowInListings} 计算）
     * @param viewerId  当前用户 ID（游客为 null），用于「非 NSFW 模式也放行自己的图片」
     */
    public Page<Image> recent(int page, int size, boolean allowNsfw, Long viewerId) {
        Page<Image> p = imageRepository.findPublic(PageRequest.of(Math.max(0, page - 1), size),
                allowNsfw, viewerId);
        populateAll(p.getContent());
        return p;
    }

    public Page<Image> byCategory(Long categoryId, int page, int size, boolean allowNsfw, Long viewerId) {
        Page<Image> p = imageRepository.findPublicByCategory(categoryId,
                PageRequest.of(Math.max(0, page - 1), size), allowNsfw, viewerId);
        populateAll(p.getContent());
        return p;
    }

    public Page<Image> byUser(User user, int page, int size, boolean allowNsfw, Long viewerId) {
        Page<Image> p = imageRepository.findByImageUserIdOrderByImageIdDesc(user.getUserId(),
                PageRequest.of(Math.max(0, page - 1), size), allowNsfw, viewerId);
        populateAll(p.getContent());
        return p;
    }

    public Page<Image> byAlbum(Album album, int page, int size, boolean allowNsfw, Long viewerId) {
        Page<Image> p = imageRepository.findByImageAlbumIdOrderByImageIdDesc(album.getAlbumId(),
                PageRequest.of(Math.max(0, page - 1), size), allowNsfw, viewerId);
        populateAll(p.getContent());
        return p;
    }

    public Page<Image> search(String q, int page, int size, boolean allowNsfw, Long viewerId) {
        Page<Image> p = imageRepository.search(q, PageRequest.of(Math.max(0, page - 1), size),
                allowNsfw, viewerId);
        populateAll(p.getContent());
        return p;
    }

    public Optional<Image> findByIdEncoded(String idEncoded) {
        long id = idCodec.decode(idEncoded);
        if (id <= 0) {
            return Optional.empty();
        }
        return imageRepository.findById(id);
    }

    public Optional<Image> findById(Long id) {
        return imageRepository.findById(id);
    }

    public Optional<Image> findByMd5(String md5) {
        return imageRepository.findFirstByImageMd5OrderByImageIdAsc(md5);
    }

    /** 原版 V4 图片无独立隐私字段（隐私由专辑控制），图片详情一律可看 */
    public boolean canView(Image image, User viewer) {
        return true;
    }

    public boolean canManage(Image image, User viewer) {
        if (viewer == null) return false;
        return viewer.isUserIsAdmin() || (image.getImageUserId() != null && image.getImageUserId().equals(viewer.getUserId()));
    }

    /**
     * 浏览计数。原版按 PHP session 去重（同一会话重复刷新只算一次），
     * 由调用方（PagesController）判断是否首次，这里只负责落库。
     * 同时写站点统计的 stat_image_views 与上传者的 user_content_views。
     */
    @Transactional
    public void increaseViews(Image image) {
        image.setImageViews(image.getImageViews() + 1);
        imageRepository.save(image);
        statsService.imageViewed(image.getImageUserId());
    }

    @Transactional
    public void update(Image image, String title, String description,
                       boolean nsfw, Long categoryId, Long albumId, User operator) {
        if (!canManage(image, operator)) {
            throw new IllegalArgumentException("无权修改该图片");
        }
        image.setImageTitle(truncate(title, 100));
        image.setImageDescription(description);
        // image_lock_nsfw_editing：锁定后只有管理员能改已有图片的 NSFW 标记
        image.setImageNsfw(nsfwPolicy.resolveEditingNsfw(operator, image.isImageNsfw(), nsfw));
        image.setImageCategoryId(categoryId != null && categoryId > 0 ? categoryId : null);

        Long oldAlbumId = image.getImageAlbumId();
        if (albumId == null || albumId <= 0) {
            image.setImageAlbumId(null);
        } else {
            Album album = albumRepository.findById(albumId).orElseThrow();
            image.setImageAlbumId(album.getAlbumId());
        }
        imageRepository.save(image);

        // 专辑计数维护
        if (oldAlbumId != null && !oldAlbumId.equals(image.getImageAlbumId())) {
            albumRepository.findById(oldAlbumId).ifPresent(a -> {
                a.setAlbumImageCount(Math.max(0, a.getAlbumImageCount() - 1));
                albumRepository.save(a);
            });
        }
        if (image.getImageAlbumId() != null && !image.getImageAlbumId().equals(oldAlbumId)) {
            albumRepository.findById(image.getImageAlbumId()).ifPresent(a -> {
                a.setAlbumImageCount(a.getAlbumImageCount() + 1);
                albumRepository.save(a);
            });
        }
    }

    @Transactional
    public void delete(Image image, User operator) {
        if (!canManage(image, operator)) {
            throw new IllegalArgumentException("无权删除该图片");
        }
        deleteInternal(image);
    }

    /**
     * 凭删图口令删除（对齐原版 /image/&lt;ID&gt;/delete/&lt;口令&gt; 里的 {@code Image::delete($id)}）。
     *
     * <p>原版该入口不做登录态判断——「知道口令」本身就是授权，因此这里跳过 canManage；
     * 调用方必须先用 {@link ImageHashService#verify} 校验通过才可调用。
     */
    @Transactional
    public void deleteByToken(Image image) {
        deleteInternal(image);
    }

    private void deleteInternal(Image image) {
        Long storageId = image.getImageStorageId();
        Optional<Storage> storage = storageId == null ? Optional.empty() : storageRepository.findById(storageId);
        storage.ifPresent(s -> {
            storageManager.delete(s, image.getDiskPath());
            storageManager.delete(s, image.getThumbPath());
        });
        if (image.getImageAlbumId() != null) {
            albumRepository.findById(image.getImageAlbumId()).ifPresent(a -> {
                a.setAlbumImageCount(Math.max(0, a.getAlbumImageCount() - 1));
                albumRepository.save(a);
            });
        }
        if (image.getImageUserId() != null) {
            userRepository.findById(image.getImageUserId()).ifPresent(u -> {
                u.setUserImageCount(Math.max(0, u.getUserImageCount() - 1));
                userRepository.save(u);
            });
        }
        imageRepository.delete(image);
        // 原版 Image::delete 里的 DB::delete('images_hash', ['image_id' => $id])
        imageHashService.revoke(image.getImageId());
        // 站点统计（原版 Stat::track('image', 'delete')）：图片数、磁盘占用、点赞数回退
        statsService.imageDeleted(image);
    }

    // ---------------- 展示填充 ----------------

    public void populate(Image image) {
        image.setIdEncoded(idCodec.encode(image.getImageId()));
        // 迁移兼容：老数据 image_storage_id 为 NULL，回退默认存储
        Storage storage = image.getImageStorageId() == null
                ? null : storageRepository.findById(image.getImageStorageId()).orElse(null);
        if (storage == null) {
            try {
                storage = storageManager.migratedStorage();
            } catch (Exception ignored) {
            }
        }
        if (storage != null) {
            image.setUrl(storageManager.url(storage, image.getDiskPath()));
            image.setThumbUrl(storageManager.url(storage, image.getThumbPath()));
        }
        if (image.getImageUserId() != null) {
            image.setUser(userRepository.findById(image.getImageUserId()).orElse(null));
        }
        if (image.getImageAlbumId() != null) {
            image.setAlbum(albumRepository.findById(image.getImageAlbumId()).orElse(null));
        }
        if (image.getImageCategoryId() != null) {
            image.setCategory(categoryRepository.findById(image.getImageCategoryId()).orElse(null));
        }
    }

    public void populateAll(List<Image> images) {
        if (images.isEmpty()) return;
        Map<Long, User> users = userRepository.findAllById(
                        images.stream().map(Image::getImageUserId).filter(java.util.Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(User::getUserId, Function.identity()));
        Map<Long, Album> albums = albumRepository.findAllById(
                        images.stream().map(Image::getImageAlbumId).filter(java.util.Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(Album::getAlbumId, Function.identity()));
        Map<Long, Category> cats = categoryRepository.findAllById(
                        images.stream().map(Image::getImageCategoryId).filter(java.util.Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(Category::getCategoryId, Function.identity()));
        Map<Long, Storage> storages = storageRepository.findAllById(
                        images.stream().map(Image::getImageStorageId).filter(java.util.Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(Storage::getStorageId, Function.identity()));

        Storage defaultStorage = null;
        for (Image image : images) {
            image.setIdEncoded(idCodec.encode(image.getImageId()));
            // 迁移兼容：老数据 image_storage_id 为 NULL，固定回退本地存储（不跟随默认存储）
            Storage s = image.getImageStorageId() == null ? null : storages.get(image.getImageStorageId());
            if (s == null) {
                if (defaultStorage == null) {
                    try {
                        defaultStorage = storageManager.migratedStorage();
                    } catch (Exception ignored) {
                    }
                }
                s = defaultStorage;
            }
            if (s != null) {
                image.setUrl(storageManager.url(s, image.getDiskPath()));
                image.setThumbUrl(storageManager.url(s, image.getThumbPath()));
            }
            if (image.getImageUserId() != null) image.setUser(users.get(image.getImageUserId()));
            if (image.getImageAlbumId() != null) image.setAlbum(albums.get(image.getImageAlbumId()));
            if (image.getImageCategoryId() != null) image.setCategory(cats.get(image.getImageCategoryId()));
        }
    }

    // ---------------- helpers ----------------

    private String md5(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String safeName(String name, String ext) {
        if (name == null || name.isBlank()) {
            return ApiKeyService.random(10) + "." + ext;
        }
        String cleaned = name.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_");
        if (cleaned.length() > 120) {
            cleaned = cleaned.substring(cleaned.length() - 120);
        }
        return cleaned;
    }

    private String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
