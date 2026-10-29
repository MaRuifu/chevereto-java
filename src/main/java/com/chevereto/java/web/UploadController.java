package com.chevereto.java.web;

import com.chevereto.java.entity.Image;
import com.chevereto.java.entity.User;
import com.chevereto.java.service.AlbumService;
import com.chevereto.java.service.FloodGuard;
import com.chevereto.java.service.IpBanService;
import com.chevereto.java.service.ImageService;
import com.chevereto.java.service.NsfwPolicy;
import com.chevereto.java.service.SettingsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** 上传接口（网页 JSON 上传 + 表单回跳） */
@Controller
@RequestMapping("/upload")
public class UploadController {

    private final ImageService imageService;
    private final AlbumService albumService;
    private final SettingsService settingsService;
    private final IpBanService ipBanService;
    private final FloodGuard floodGuard;
    private final NsfwPolicy nsfwPolicy;
    private final AuthSupport auth;

    public UploadController(ImageService imageService, AlbumService albumService,
                            SettingsService settingsService, IpBanService ipBanService,
                            FloodGuard floodGuard, NsfwPolicy nsfwPolicy, AuthSupport auth) {
        this.imageService = imageService;
        this.albumService = albumService;
        this.settingsService = settingsService;
        this.ipBanService = ipBanService;
        this.floodGuard = floodGuard;
        this.nsfwPolicy = nsfwPolicy;
        this.auth = auth;
    }

    /**
     * 上传入口。注意这里<b>不能</b>加 {@code @ResponseBody}：
     * 该方法有两种返回形态——JSON（给前端 fetch 用）与 302 重定向（表单提交用）。
     * 一旦标了 {@code @ResponseBody}，{@code "redirect:/image/xxx"} 会被当成字符串直接写进响应体，
     * 表单上传就不会跳转了。改为返回 {@link ResponseEntity}（自动序列化 JSON）+ String（视图名）。
     */
    @PostMapping
    public Object upload(@RequestParam(value = "file", required = false) MultipartFile file,
                         @RequestParam(value = "album", required = false) Long albumId,
                         @RequestParam(value = "category", required = false) Long categoryId,
                         @RequestParam(value = "title", required = false) String title,
                         @RequestParam(value = "nsfw", defaultValue = "false") boolean nsfw,
                         @RequestParam(value = "source", required = false) String source,
                         @RequestParam(value = "json", defaultValue = "false") boolean json,
                         HttpServletRequest request,
                         HttpSession session,
                         RedirectAttributes ra) {
        User user = auth.currentUser(session).orElse(null);
        if (!settingsService.getBool("enable_uploads")) {
            return fail(json, ra, 403, "本站已关闭上传功能");
        }
        boolean guestAllowed = settingsService.getBool("guest_uploads");
        if (user == null && !guestAllowed) {
            return fail(json, ra, 403, "本站不允许游客上传，请先登录");
        }
        String ip = auth.clientIp(request);
        if (ipBanService.isBanned(ip)) {
            return fail(json, ra, 403, "你的 IP 已被封禁");
        }

        try {
            // 洪水保护：按 5 个滚动窗口拦截，管理员豁免（原版 Upload::handleFlood）
            floodGuard.assertUploadAllowed(user, ip);
            // 上传器关掉 NSFW 复选框时，表单里即使有值也不认（API 上传不受此影响）
            boolean nsfwEffective = nsfw && nsfwPolicy.showUploadCheckbox();

            Image image;
            if (file != null && !file.isEmpty()) {
                image = imageService.upload(file, user, albumId, categoryId, title, nsfwEffective, ip);
            } else if (source != null && !source.isBlank()) {
                image = imageService.uploadBytes(fetchSource(source), null, user, albumId, categoryId,
                        title, nsfwEffective, ip);
            } else {
                throw new IllegalArgumentException("请提供文件或图片地址/URL");
            }
            if (json) {
                imageService.populate(image); // 填充 idEncoded / url / thumbUrl（JSON 响应必需）
                return ResponseEntity.ok(okJson(image));
            }
            // 表单上传：把删图口令随 flash 带到图片页（口令明文只此一次可见，页面负责提示保存）
            if (image.getDeleteUrl() != null) {
                ra.addFlashAttribute("deleteUrl", WebUrls.absolute(image.getDeleteUrl()));
            }
            ra.addFlashAttribute("success", "上传成功");
            return "redirect:/image/" + image.getIdEncoded();
        } catch (IllegalArgumentException | IOException e) {
            return fail(json, ra, 400, e.getMessage());
        }
    }

    /** source 支持：http(s) URL / 纯 base64 / data:base64 */
    private byte[] fetchSource(String source) throws IOException {
        try {
            if (source.startsWith("http://") || source.startsWith("https://")) {
                HttpClient client = HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .connectTimeout(Duration.ofSeconds(10))
                        .build();
                HttpResponse<byte[]> resp = client.send(
                        HttpRequest.newBuilder(URI.create(source)).timeout(Duration.ofSeconds(20)).GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray());
                if (resp.statusCode() != 200) {
                    throw new IllegalArgumentException("抓取远程图片失败：HTTP " + resp.statusCode());
                }
                return resp.body();
            }
            String b64 = source.startsWith("data:") && source.contains(",")
                    ? source.substring(source.indexOf(',') + 1) : source;
            return Base64.getDecoder().decode(b64.replaceAll("\\s", ""));
        } catch (IOException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("请求被中断");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("解析 source 失败：" + e.getMessage());
        }
    }

    private Map<String, Object> okJson(Image image) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status_code", 200);
        Map<String, Object> img = new LinkedHashMap<>();
        img.put("id", image.getImageId());
        img.put("id_encoded", image.getIdEncoded());
        img.put("name", image.getImageName());
        img.put("title", image.getImageTitle());
        img.put("filename", image.getImageOriginalFilename());
        img.put("md5", image.getImageMd5());
        img.put("width", image.getImageWidth());
        img.put("height", image.getImageHeight());
        img.put("size", image.getImageSize());
        img.put("url", image.getUrl());
        Map<String, Object> thumb = new LinkedHashMap<>();
        thumb.put("url", image.getThumbUrl());
        img.put("thumb", thumb);
        // 原版 Image::getDeleteUrl()：/image/<id>/delete/<口令>，凭链接即可删图（无需登录）
        img.put("delete_url", WebUrls.absolute(image.getDeleteUrl() != null
                ? image.getDeleteUrl() : "/image/" + image.getIdEncoded()));
        resp.put("image", img);
        return resp;
    }

    private Object fail(boolean json, RedirectAttributes ra, int code, String message) {
        if (json) {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("status_code", code);
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("message", message);
            err.put("code", code);
            resp.put("error", err);
            // 保持原有约定：HTTP 200 + 响应体里的 status_code 表示业务错误，避免前端判断逻辑变化
            return ResponseEntity.ok(resp);
        }
        ra.addFlashAttribute("error", message);
        return "redirect:/login";
    }
}
