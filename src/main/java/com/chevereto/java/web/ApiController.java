package com.chevereto.java.web;

import com.chevereto.java.entity.Image;
import com.chevereto.java.entity.User;
import com.chevereto.java.service.ApiKeyService;
import com.chevereto.java.service.ImageService;
import com.chevereto.java.service.SettingsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 兼容 Chevereto API v1.1：
 * POST /api/1.1/upload（action=upload，source=文件/source_url/source_base64，key）
 * POST /api/1.1/get（id_encoded 或 id 或 md5）
 * POST /api/1.1/delete（id_encoded + key）
 */
@Controller
public class ApiController {

    private final ImageService imageService;
    private final ApiKeyService apiKeyService;
    private final SettingsService settingsService;
    private final AuthSupport auth;

    public ApiController(ImageService imageService, ApiKeyService apiKeyService,
                         SettingsService settingsService, AuthSupport auth) {
        this.imageService = imageService;
        this.apiKeyService = apiKeyService;
        this.settingsService = settingsService;
        this.auth = auth;
    }

    @PostMapping({"/api/1.1/upload", "/api/1.1"})
    @ResponseBody
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam(value = "source", required = false) MultipartFile file,
            @RequestParam(value = "source_url", required = false) String sourceUrl,
            @RequestParam(value = "source_base64", required = false) String sourceBase64,
            @RequestParam(value = "key", required = false) String key,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "nsfw", defaultValue = "false") boolean nsfw,
            HttpServletRequest request) {
        try {
            if (!settingsService.getBool("upload_api_enabled")) {
                return error(HttpStatus.FORBIDDEN, "API 上传已被关闭");
            }
            User user = apiKeyService.resolveUser(key).orElse(null);
            if (user == null) {
                return error(HttpStatus.UNAUTHORIZED, "无效的 API Key");
            }

            Image image;
            if (file != null && !file.isEmpty()) {
                image = imageService.upload(file, user, null, null, title, nsfw, auth.clientIp(request));
            } else if (sourceUrl != null && !sourceUrl.isBlank()) {
                image = imageService.uploadBytes(fetchUrl(sourceUrl), null, user, null, null,
                        title, nsfw, auth.clientIp(request));
            } else if (sourceBase64 != null && !sourceBase64.isBlank()) {
                String b64 = sourceBase64.startsWith("data:") && sourceBase64.contains(",")
                        ? sourceBase64.substring(sourceBase64.indexOf(',') + 1) : sourceBase64;
                image = imageService.uploadBytes(Base64.getDecoder().decode(b64.replaceAll("\\s", "")),
                        null, user, null, null, title, nsfw, auth.clientIp(request));
            } else {
                return error(HttpStatus.BAD_REQUEST, "缺少 source 参数（文件 / source_url / source_base64）");
            }
            return ResponseEntity.ok(successBody(image));
        } catch (IllegalArgumentException | IOException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误: " + e.getMessage());
        }
    }

    @PostMapping("/api/1.1/get")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> get(@RequestParam(required = false) String id_encoded,
                                                   @RequestParam(required = false) Long id,
                                                   @RequestParam(required = false) String md5) {
        Image image = null;
        if (id_encoded != null && !id_encoded.isBlank()) {
            image = imageService.findByIdEncoded(id_encoded).orElse(null);
        } else if (id != null) {
            image = imageService.findById(id).orElse(null);
        } else if (md5 != null && !md5.isBlank()) {
            image = imageService.findByMd5(md5).orElse(null);
        }
        if (image == null) {
            return error(HttpStatus.NOT_FOUND, "图片不存在或不可访问");
        }
        imageService.populate(image);
        return ResponseEntity.ok(successBody(image));
    }

    @PostMapping("/api/1.1/delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> delete(@RequestParam String id_encoded,
                                                      @RequestParam String key,
                                                      HttpServletRequest request) {
        User user = apiKeyService.resolveUser(key).orElse(null);
        if (user == null) {
            return error(HttpStatus.UNAUTHORIZED, "无效的 API Key");
        }
        Image image = imageService.findByIdEncoded(id_encoded).orElse(null);
        if (image == null) {
            return error(HttpStatus.NOT_FOUND, "图片不存在");
        }
        try {
            imageService.delete(image, user);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("status_code", 200);
            resp.put("success", Map.of("code", 200, "message", "image deleted"));
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.FORBIDDEN, e.getMessage());
        }
    }

    private byte[] fetchUrl(String url) throws IOException {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<byte[]> resp = client.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() != 200) {
                throw new IllegalArgumentException("抓取远程图片失败：HTTP " + resp.statusCode());
            }
            return resp.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("请求被中断");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("抓取远程图片失败: " + e.getMessage());
        }
    }

    private Map<String, Object> successBody(Image image) {
        Map<String, Object> img = new LinkedHashMap<>();
        img.put("name", image.getImageName());
        img.put("extension", image.getImageExtension());
        img.put("width", image.getImageWidth());
        img.put("height", image.getImageHeight());
        img.put("size", image.getImageSize());
        img.put("date", image.getImageDate().format(DateTimeFormatter.ISO_DATE_TIME));
        img.put("date_gmt", image.getImageDateGmt().format(DateTimeFormatter.ISO_DATE_TIME));
        img.put("title", image.getImageTitle());
        img.put("nsfw", image.isImageNsfw() ? 1 : 0);
        img.put("md5", image.getImageMd5());
        img.put("id", image.getImageId());
        img.put("id_encoded", image.getIdEncoded());
        img.put("path", image.getImagePath());
        img.put("url", image.getUrl());
        Map<String, Object> thumb = new LinkedHashMap<>();
        thumb.put("url", image.getThumbUrl());
        img.put("thumb", thumb);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status_code", 200);
        resp.put("success", Map.of("code", 200, "message", "image uploaded", "name", image.getImageName()));
        resp.put("image", img);
        // 原版 api.php：$image['delete_url'] = Image::getDeleteUrl(encodeID($id), $deletePassword)
        resp.put("delete_url", WebUrls.absolute(image.getDeleteUrl() != null
                ? image.getDeleteUrl() : "/image/" + image.getIdEncoded()));
        return resp;
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status_code", status.value());
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("message", message);
        err.put("code", status.value());
        resp.put("error", err);
        return ResponseEntity.status(status).body(resp);
    }

    @ExceptionHandler(Exception.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> onException(Exception e) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误: " + e.getMessage());
    }
}
