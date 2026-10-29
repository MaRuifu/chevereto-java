package com.chevereto.java.service;

import com.chevereto.java.entity.Album;
import com.chevereto.java.entity.Image;
import com.chevereto.java.entity.User;
import com.chevereto.java.repository.AlbumRepository;
import com.chevereto.java.repository.ImageRepository;
import com.chevereto.java.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** 专辑服务（chv_albums；隐私枚举与原版一致，支持密码保护） */
@Service
public class AlbumService {

    private static final List<String> PRIVACIES = List.of("public", "private", "private_but_link", "password");

    private final AlbumRepository albumRepository;
    private final ImageRepository imageRepository;
    private final UserRepository userRepository;
    private final ImageService imageService;
    private final CheveretoIdCodec idCodec;
    private final StatsService statsService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AlbumService(AlbumRepository albumRepository, ImageRepository imageRepository,
                        UserRepository userRepository, ImageService imageService,
                        CheveretoIdCodec idCodec, StatsService statsService) {
        this.albumRepository = albumRepository;
        this.imageRepository = imageRepository;
        this.userRepository = userRepository;
        this.imageService = imageService;
        this.idCodec = idCodec;
        this.statsService = statsService;
    }

    public Optional<Album> findByIdEncoded(String idEncoded) {
        long id = idCodec.decode(idEncoded);
        if (id <= 0) return Optional.empty();
        return albumRepository.findById(id);
    }

    public boolean canView(Album album, User viewer) {
        if (!"private".equals(album.getAlbumPrivacy())) {
            return true;
        }
        return viewer != null && (viewer.isUserIsAdmin()
                || (album.getAlbumUserId() != null && album.getAlbumUserId().equals(viewer.getUserId())));
    }

    public boolean canManage(Album album, User viewer) {
        if (viewer == null) return false;
        return viewer.isUserIsAdmin() || (album.getAlbumUserId() != null && album.getAlbumUserId().equals(viewer.getUserId()));
    }

    /** password 型专辑的密码校验 */
    public boolean checkPassword(Album album, String password) {
        if (!"password".equals(album.getAlbumPrivacy()) || album.getAlbumPassword() == null) {
            return true;
        }
        return password != null && encoder.matches(password, album.getAlbumPassword());
    }

    @Transactional
    public Album create(User user, String name, String description, String privacy, String password, String ip) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("专辑名称不能为空");
        }
        Album album = new Album();
        album.setAlbumName(truncate(name.trim(), 100));
        album.setAlbumDescription(description);
        album.setAlbumPrivacy(normalizePrivacy(privacy));
        if ("password".equals(album.getAlbumPrivacy())) {
            if (password == null || password.isBlank()) {
                throw new IllegalArgumentException("密码保护专辑必须设置密码");
            }
            album.setAlbumPassword(encoder.encode(password));
        }
        album.setAlbumCreationIp(ip == null ? "" : ip);
        if (user != null) {
            album.setAlbumUserId(user.getUserId());
        }
        album = albumRepository.save(album);
        if (user != null) {
            user.setUserAlbumCount(user.getUserAlbumCount() + 1);
            userRepository.save(user);
        }
        statsService.albumInserted(album.getAlbumDateGmt());
        return album;
    }

    @Transactional
    public void update(Album album, String name, String description, String privacy,
                       String password, User operator) {
        if (!canManage(album, operator)) {
            throw new IllegalArgumentException("无权修改该专辑");
        }
        if (name != null && !name.isBlank()) {
            album.setAlbumName(truncate(name.trim(), 100));
        }
        album.setAlbumDescription(description);
        if (privacy != null) {
            String p = normalizePrivacy(privacy);
            album.setAlbumPrivacy(p);
            if ("password".equals(p)) {
                if (password != null && !password.isBlank()) {
                    album.setAlbumPassword(encoder.encode(password));
                } else if (album.getAlbumPassword() == null) {
                    throw new IllegalArgumentException("密码保护专辑必须设置密码");
                }
            } else {
                album.setAlbumPassword(null);
            }
        }
        albumRepository.save(album);
    }

    @Transactional
    public void delete(Album album, User operator) {
        if (!canManage(album, operator)) {
            throw new IllegalArgumentException("无权删除该专辑");
        }
        // 专辑内图片移出专辑（不删除图片，与原版一致）
        // allowNsfw=true + viewerId=null：这是维护性操作，不能受可见性过滤影响，
        // 否则 NSFW 图片会变成"孤儿"（image_album_id 仍指向已删除的专辑）
        imageRepository.findByImageAlbumIdOrderByImageIdDesc(album.getAlbumId(),
                        PageRequest.ofSize(1000), true, null)
                .forEach(img -> {
                    img.setImageAlbumId(null);
                    imageRepository.save(img);
                });
        albumRepository.delete(album);
        if (album.getAlbumUserId() != null) {
            userRepository.findById(album.getAlbumUserId()).ifPresent(u -> {
                u.setUserAlbumCount(Math.max(0, u.getUserAlbumCount() - 1));
                userRepository.save(u);
            });
        }
        statsService.albumDeleted(album.getAlbumDateGmt());
    }

    public Page<Album> byUser(User user, int page, int size) {
        return albumRepository.findByAlbumUserIdOrderByAlbumIdDesc(user.getUserId(), PageRequest.of(Math.max(0, page - 1), size));
    }

    /** 个人主页展示：排除私密专辑 */
    public Page<Album> byUserVisible(User user, int page, int size) {
        return albumRepository.findByAlbumUserIdAndAlbumPrivacyNotOrderByAlbumIdDesc(
                user.getUserId(), "private", PageRequest.of(Math.max(0, page - 1), size));
    }

    /**
     * 相册浏览计数：落库 album_views，并写站点统计的 stat_album_views
     * 与创建者的 user_content_views（原版 Stat::track('album', 'view')）。
     */
    @Transactional
    public void increaseViews(Album album) {
        album.setAlbumViews(album.getAlbumViews() + 1);
        albumRepository.save(album);
        statsService.albumViewed(album.getAlbumUserId());
    }

    public void populate(Album album) {
        album.setIdEncoded(idCodec.encode(album.getAlbumId()));
        if (album.getAlbumUserId() != null) {
            album.setUser(userRepository.findById(album.getAlbumUserId()).orElse(null));
        }
        if (album.getAlbumImageCount() <= 0) {
            // 计数用于展示，必须含 NSFW 图片 → 绕过可见性过滤
            album.setAlbumImageCount(imageRepository.countByImageAlbumId(album.getAlbumId(), true, null));
        }
        Image cover = null;
        if (album.getAlbumCoverId() != null) {
            cover = imageRepository.findById(album.getAlbumCoverId()).orElse(null);
        }
        if (cover == null) {
            cover = imageRepository.findFirstByImageAlbumIdOrderByImageIdAsc(album.getAlbumId()).orElse(null);
        }
        if (cover != null) {
            imageService.populate(cover);
            album.setCoverUrl(cover.getThumbUrl());
        }
    }

    public void populateAll(List<Album> albums) {
        albums.forEach(this::populate);
    }

    private String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    private String normalizePrivacy(String privacy) {
        String p = privacy == null ? "" : privacy.trim().toLowerCase(Locale.ROOT);
        return PRIVACIES.contains(p) ? p : "public";
    }
}
