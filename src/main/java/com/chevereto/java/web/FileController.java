package com.chevereto.java.web;

import com.chevereto.java.entity.Image;
import com.chevereto.java.entity.Storage;
import com.chevereto.java.repository.ImageRepository;
import com.chevereto.java.repository.StorageRepository;
import com.chevereto.java.storage.LocalStorageDriver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;

/**
 * 本地文件对外服务：
 *  - /images/{相对路径} —— 与原版 Chevereto 直链完全一致（images 目录直出）
 *  - /file/{相对路径}   —— 兼容旧链接
 * 原图按 chv_images.image_path 匹配 MIME；缩略图（*.th.*）按自身扩展名判断。
 */
@Controller
public class FileController {

    private final LocalStorageDriver localStorageDriver;
    private final StorageRepository storageRepository;
    private final ImageRepository imageRepository;

    public FileController(LocalStorageDriver localStorageDriver,
                          StorageRepository storageRepository,
                          ImageRepository imageRepository) {
        this.localStorageDriver = localStorageDriver;
        this.storageRepository = storageRepository;
        this.imageRepository = imageRepository;
    }

    @GetMapping({"/images/**", "/file/**"})
    public ResponseEntity<Object> serve(HttpServletRequest request) throws IOException {
        String uri = request.getRequestURI();
        String context = request.getContextPath() == null ? "" : request.getContextPath();
        String prefix = uri.startsWith(context + "/images/") ? "/images/" : "/file/";
        String relPath = uri.substring(context.length() + prefix.length());
        try {
            relPath = java.net.URLDecoder.decode(relPath, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
        }
        if (relPath.isBlank() || relPath.contains("..")) {
            return ResponseEntity.notFound().build();
        }

        // 找本地存储行：优先 active 的 local 行，其次任意 local 行（原版 local 行
        // storage_service 可能为 NULL、storage_is_active 可能仍为 0）；
        // 都没有则直接用 app.local-storage-root 兜底，保证 /images/** 始终可用
        var locals = storageRepository.findAll().stream()
                .filter(s -> s.getStorageService() == null || "local".equalsIgnoreCase(s.getStorageService()))
                .toList();
        var local = locals.stream().filter(Storage::isStorageIsActive).findFirst()
                .or(() -> locals.stream().findFirst())
                .orElseGet(() -> {
                    Storage fallback = new Storage();
                    fallback.setStorageService("local");
                    fallback.setStorageUrl("");
                    fallback.setStorageServer(null);
                    return fallback;
                });

        Path file = localStorageDriver.resolve(local, relPath);
        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            return ResponseEntity.notFound().build();
        }

        String ext = extOf(relPath);
        String mime = imageRepository.findByImagePath(stripThumbSuffix(relPath))
                .map(Image::getImageExtension)
                .map(this::mimeOfExt)
                .orElseGet(() -> mimeOfExt(ext));

        MediaType mediaType = mime != null ? MediaType.parseMediaType(mime) : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .body(new FileSystemResource(file));
    }

    /** a.png.th.png → a.png（用于按原图记录取 MIME） */
    private String stripThumbSuffix(String path) {
        int idx = path.lastIndexOf(".th.");
        return idx > 0 ? path.substring(0, idx) : path;
    }

    private String extOf(String path) {
        int dot = path.lastIndexOf('.');
        return dot > 0 ? path.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    private String mimeOfExt(String ext) {
        return switch (ext == null ? "" : ext.toLowerCase(Locale.ROOT)) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "bmp" -> "image/bmp";
            case "avif" -> "image/avif";
            default -> null;
        };
    }
}
