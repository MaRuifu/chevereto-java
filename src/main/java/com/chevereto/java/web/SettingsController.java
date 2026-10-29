package com.chevereto.java.web;

import com.chevereto.java.entity.Album;
import com.chevereto.java.entity.ApiKey;
import com.chevereto.java.entity.User;
import com.chevereto.java.service.AlbumService;
import com.chevereto.java.service.ApiKeyService;
import com.chevereto.java.service.ImageHashService;
import com.chevereto.java.service.ImageService;
import com.chevereto.java.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** 用户设置与管理（对齐原版 /settings：个人资料/密码/API + 内容管理动作） */
@Controller
public class SettingsController {

    private final ImageService imageService;
    private final AlbumService albumService;
    private final ApiKeyService apiKeyService;
    private final UserService userService;
    private final ImageHashService imageHashService;
    private final AuthSupport auth;

    public SettingsController(ImageService imageService, AlbumService albumService,
                              ApiKeyService apiKeyService, UserService userService,
                              ImageHashService imageHashService, AuthSupport auth) {
        this.imageService = imageService;
        this.albumService = albumService;
        this.apiKeyService = apiKeyService;
        this.userService = userService;
        this.imageHashService = imageHashService;
        this.auth = auth;
    }

    private User me(HttpSession session) {
        return auth.currentUser(session).orElse(null);
    }

    @GetMapping("/settings")
    public String settings(@RequestParam(defaultValue = "profile") String tab, Model model, HttpSession session) {
        User user = me(session);
        if (user == null) return "redirect:/login";
        auth.common(model, session, user);
        model.addAttribute("tab", tab);
        if ("api".equals(tab)) {
            // 与原版一致：只暴露 chv_{encodeID(id)}_***，完整 Key 不可回显（库里只有散列）
            apiKeyService.findByUser(user).ifPresent(k -> model.addAttribute("apiKeyPublic", apiKeyService.publicKey(k)));
        }
        return "settings";
    }

    @GetMapping("/dashboard")
    public String dashboard(HttpSession session) {
        User user = me(session);
        if (user == null) return "redirect:/login";
        return user.isUserIsAdmin() ? "redirect:/admin" : "redirect:/settings";
    }

    // ---------------- 个人资料 / 密码 / API ----------------

    @PostMapping("/settings/profile")
    public String saveProfile(@RequestParam(required = false) String name,
                              @RequestParam(required = false) String bio,
                              @RequestParam(required = false) String website,
                              @RequestParam(defaultValue = "false") boolean showNsfw,
                              HttpSession session, RedirectAttributes ra) {
        User user = me(session);
        if (user == null) return "redirect:/login";
        userService.updateProfile(user, name, bio, website, showNsfw);
        ra.addFlashAttribute("success", "个人资料已更新");
        return "redirect:/settings";
    }

    @PostMapping("/settings/password")
    public String changePassword(@RequestParam String oldPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirm,
                                 HttpSession session, RedirectAttributes ra) {
        User user = me(session);
        if (user == null) return "redirect:/login";
        try {
            if (!newPassword.equals(confirm)) {
                throw new IllegalArgumentException("两次输入的新密码不一致");
            }
            userService.changePassword(user, oldPassword, newPassword);
            ra.addFlashAttribute("success", "密码修改成功");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/settings?tab=password";
    }

    @PostMapping("/settings/api")
    public String regenerateApiKey(HttpSession session, RedirectAttributes ra) {
        User user = me(session);
        if (user == null) return "redirect:/login";
        ApiKey key = apiKeyService.createOrRegenerate(user);
        // 与原版一致：完整 Key 只在生成后显示这一次（flash 只活一次重定向）
        ra.addFlashAttribute("newApiKey", key.getApiKeyHash());
        ra.addFlashAttribute("success", "API Key 已重新生成，旧 Key 立即失效");
        return "redirect:/settings?tab=api";
    }

    // ---------------- 图片管理 ----------------

    @PostMapping("/image/{idEncoded}/edit")
    public String editImage(@PathVariable String idEncoded,
                            @RequestParam(required = false) String title,
                            @RequestParam(required = false) String description,
                            @RequestParam(defaultValue = "false") boolean nsfw,
                            @RequestParam(defaultValue = "0") Long categoryId,
                            @RequestParam(defaultValue = "0") Long albumId,
                            HttpSession session, RedirectAttributes ra) {
        User user = me(session);
        if (user == null) return "redirect:/login";
        return imageService.findByIdEncoded(idEncoded).map(image -> {
            try {
                imageService.update(image, title, description, nsfw, categoryId, albumId, user);
                ra.addFlashAttribute("success", "图片信息已更新");
            } catch (IllegalArgumentException e) {
                ra.addFlashAttribute("error", e.getMessage());
            }
            return "redirect:/image/" + idEncoded;
        }).orElse("redirect:/");
    }

    @PostMapping("/image/{idEncoded}/delete")
    public String deleteImage(@PathVariable String idEncoded, HttpSession session, RedirectAttributes ra) {
        User user = me(session);
        if (user == null) return "redirect:/login";
        return imageService.findByIdEncoded(idEncoded).map(image -> {
            try {
                imageService.delete(image, user);
                ra.addFlashAttribute("success", "图片已删除");
                return "redirect:/u/" + pathSegment(user.getUserUsername());
            } catch (IllegalArgumentException e) {
                ra.addFlashAttribute("error", e.getMessage());
                return "redirect:/image/" + idEncoded;
            }
        }).orElse("redirect:/");
    }

    /**
     * 凭删图口令删除（对齐原版 app/legacy/routes/image.php）：
     * <pre>
     *   if ($request_handle[1] === 'delete') {
     *       if (Image::verifyPassword($id, $request_handle[2] ?? '')) { Image::delete($id); redirect(...); }
     *       $handler->issueError(404);
     *   }
     * </pre>
     *
     * <p><b>无需登录</b>——口令即授权，这是原版给游客上传者留的删图通道。
     * 口令错误或该图没有口令记录一律 404（与原版一致，不区分「无此图」和「口令错」）。
     * 删除成功后跳首页并提示；原版跳到已删图片的查看页会 404，这里改为落到首页。
     */
    @GetMapping("/image/{idEncoded}/delete/{password}")
    public String deleteImageByToken(@PathVariable String idEncoded,
                                     @PathVariable String password,
                                     RedirectAttributes ra) {
        var image = imageService.findByIdEncoded(idEncoded)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!imageHashService.verify(image.getImageId(), password)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        imageService.deleteByToken(image);
        ra.addFlashAttribute("success", "图片已删除");
        return "redirect:/";
    }

    // ---------------- 专辑管理 ----------------

    @PostMapping("/album/create")
    public String createAlbum(@RequestParam String name,
                              @RequestParam(required = false) String description,
                              @RequestParam(defaultValue = "public") String privacy,
                              @RequestParam(required = false) String password,
                              HttpServletRequest request, HttpSession session, RedirectAttributes ra) {
        User user = me(session);
        if (user == null) return "redirect:/login";
        try {
            Album album = albumService.create(user, name, description, privacy, password, auth.clientIp(request));
            ra.addFlashAttribute("success", "专辑「" + album.getAlbumName() + "」创建成功");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/u/" + pathSegment(user.getUserUsername()) + "?tab=albums";
    }

    @PostMapping("/album/{idEncoded}/edit")
    public String editAlbum(@PathVariable String idEncoded,
                            @RequestParam(required = false) String name,
                            @RequestParam(required = false) String description,
                            @RequestParam(required = false) String privacy,
                            @RequestParam(required = false) String password,
                            HttpSession session, RedirectAttributes ra) {
        User user = me(session);
        if (user == null) return "redirect:/login";
        return albumService.findByIdEncoded(idEncoded).map(album -> {
            try {
                albumService.update(album, name, description, privacy, password, user);
                ra.addFlashAttribute("success", "专辑已更新");
            } catch (IllegalArgumentException e) {
                ra.addFlashAttribute("error", e.getMessage());
            }
            return "redirect:/album/" + idEncoded;
        }).orElse("redirect:/");
    }

    @PostMapping("/album/{idEncoded}/delete")
    public String deleteAlbum(@PathVariable String idEncoded, HttpSession session, RedirectAttributes ra) {
        User user = me(session);
        if (user == null) return "redirect:/login";
        albumService.findByIdEncoded(idEncoded).ifPresent(album -> {
            try {
                albumService.delete(album, user);
                ra.addFlashAttribute("success", "专辑已删除（图片保留）");
            } catch (IllegalArgumentException e) {
                ra.addFlashAttribute("error", e.getMessage());
            }
        });
        return "redirect:/u/" + pathSegment(user.getUserUsername()) + "?tab=albums";
    }

    /** 用户名可直接含中文，拼进重定向 URL 前需要做路径段编码 */
    private static String pathSegment(String username) {
        return UriUtils.encodePathSegment(username == null ? "" : username, StandardCharsets.UTF_8);
    }
}
