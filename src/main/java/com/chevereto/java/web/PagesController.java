package com.chevereto.java.web;

import com.chevereto.java.entity.Album;
import com.chevereto.java.entity.Category;
import com.chevereto.java.entity.Image;
import com.chevereto.java.entity.User;
import com.chevereto.java.repository.CategoryRepository;
import com.chevereto.java.service.AlbumService;
import com.chevereto.java.service.ImageService;
import com.chevereto.java.service.NsfwPolicy;
import com.chevereto.java.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Optional;

/**
 * 公开页面（路由对齐原版）：
 * / 首页上传、/explore 探索（?category=url_key）、/search、/image/{id_encoded}、
 * /album/{id_encoded}（支持密码保护）、/{username} 个人主页。
 *
 * <p>所有图片列表都经 {@link NsfwPolicy} 决定 {@code allowNsfw}，并把
 * {@code nsfwBlur} 传给模板做缩略图模糊，两者口径保持一致：
 * 看不到的内容不会被"模糊着显示出来"，模糊只作用于已被放行的 NSFW 内容。
 */
@Controller
public class PagesController {

    private final ImageService imageService;
    private final AlbumService albumService;
    private final UserService userService;
    private final CategoryRepository categoryRepository;
    private final NsfwPolicy nsfwPolicy;
    private final AuthSupport auth;

    public PagesController(ImageService imageService, AlbumService albumService,
                           UserService userService, CategoryRepository categoryRepository,
                           NsfwPolicy nsfwPolicy, AuthSupport auth) {
        this.imageService = imageService;
        this.albumService = albumService;
        this.userService = userService;
        this.categoryRepository = categoryRepository;
        this.nsfwPolicy = nsfwPolicy;
        this.auth = auth;
    }

    @GetMapping("/")
    public String index(@RequestParam(defaultValue = "1") int page, Model model, HttpSession session) {
        auth.common(model, session);
        User me = auth.currentUser(session).orElse(null);
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("images", imageService.recent(page, 24,
                nsfwPolicy.allowInListings(me), nsfwPolicy.viewerId(me)));
        model.addAttribute("nsfwBlur", nsfwPolicy.blurInListings());
        model.addAttribute("nsfwCheckbox", nsfwPolicy.showUploadCheckbox());
        model.addAttribute("currentPage", page);
        return "index";
    }

    @GetMapping("/explore")
    public String explore(@RequestParam(defaultValue = "1") int page,
                          @RequestParam(required = false) String category,
                          Model model, HttpSession session) {
        auth.common(model, session);
        User me = auth.currentUser(session).orElse(null);
        boolean allowNsfw = nsfwPolicy.allowInListings(me);
        Long viewerId = nsfwPolicy.viewerId(me);
        Page<Image> images;
        if (category != null && !category.isBlank()) {
            Optional<Category> cat = categoryRepository.findAll().stream()
                    .filter(c -> c.getCategoryUrlKey().equals(category)).findFirst();
            if (cat.isEmpty()) {
                return "redirect:/explore";
            }
            model.addAttribute("activeCategory", cat.get());
            images = imageService.byCategory(cat.get().getCategoryId(), page, 24, allowNsfw, viewerId);
        } else {
            images = imageService.recent(page, 24, allowNsfw, viewerId);
        }
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("images", images);
        model.addAttribute("nsfwBlur", nsfwPolicy.blurInListings());
        model.addAttribute("currentPage", page);
        model.addAttribute("path", "/explore");
        model.addAttribute("query", category == null ? "" : category);
        return "explore";
    }

    @GetMapping("/search")
    public String search(@RequestParam(required = false) String q,
                         @RequestParam(defaultValue = "1") int page,
                         Model model, HttpSession session) {
        auth.common(model, session);
        User me = auth.currentUser(session).orElse(null);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("nsfwBlur", nsfwPolicy.blurInListings());
        if (q == null || q.isBlank()) {
            model.addAttribute("images", Page.empty());
            return "search";
        }
        Page<Image> images = imageService.search(q.trim(), page, 24,
                nsfwPolicy.allowInListings(me), nsfwPolicy.viewerId(me));
        model.addAttribute("images", images);
        model.addAttribute("currentPage", page);
        return "search";
    }

    @GetMapping("/image/{idEncoded}")
    public String image(@PathVariable String idEncoded, Model model, HttpSession session) {
        auth.common(model, session);
        Optional<Image> opt = imageService.findByIdEncoded(idEncoded);
        if (opt.isEmpty()) {
            return notFound(model);
        }
        Image image = opt.get();
        imageService.populate(image);
        User me = auth.currentUser(session).orElse(null);
        if (!imageService.canView(image, me)) {
            model.addAttribute("errorMsg", "该图片为私密图片，无权查看");
            return "error/generic";
        }
        // 浏览量按会话去重：同一浏览器重复刷新只算一次（原版用 PHP session 的同一做法）
        String viewKey = "viewed_image_" + image.getImageId();
        if (session.getAttribute(viewKey) == null) {
            session.setAttribute(viewKey, Boolean.TRUE);
            imageService.increaseViews(image);
        }
        model.addAttribute("image", image);
        model.addAttribute("canManage", imageService.canManage(image, me));
        // 编辑框里 NSFW 复选框是否可改（image_lock_nsfw_editing 时只有管理员能改）
        model.addAttribute("nsfwEditable", !nsfwPolicy.isEditingLocked()
                || (me != null && me.isUserIsAdmin()));
        model.addAttribute("categories", categoryRepository.findAll());
        if (me != null) {
            model.addAttribute("myAlbums", albumService.byUser(me, 1, 100).getContent());
        }
        return "image";
    }

    @GetMapping("/album/{idEncoded}")
    public String album(@PathVariable String idEncoded,
                        @RequestParam(defaultValue = "1") int page,
                        @RequestParam(required = false) String password,
                        Model model, HttpSession session) {
        auth.common(model, session);
        Optional<Album> opt = albumService.findByIdEncoded(idEncoded);
        if (opt.isEmpty()) {
            return notFound(model);
        }
        Album album = opt.get();
        User me = auth.currentUser(session).orElse(null);
        if (!albumService.canView(album, me)) {
            model.addAttribute("errorMsg", "该专辑为私密专辑，无权查看");
            return "error/generic";
        }
        // 密码保护专辑（与原版一致）
        if ("password".equals(album.getAlbumPrivacy())
                && !albumService.checkPassword(album, (String) session.getAttribute("album_pwd_" + album.getAlbumId()))
                && !albumService.checkPassword(album, password)) {
            model.addAttribute("album", album);
            return "album-password";
        }
        if (password != null) {
            session.setAttribute("album_pwd_" + album.getAlbumId(), password);
        }
        albumService.populate(album);
        String viewKey = "viewed_album_" + album.getAlbumId();
        if (session.getAttribute(viewKey) == null) {
            session.setAttribute(viewKey, Boolean.TRUE);
            albumService.increaseViews(album);
        }
        model.addAttribute("album", album);
        model.addAttribute("images", imageService.byAlbum(album, page, 24,
                nsfwPolicy.allowInListings(me), nsfwPolicy.viewerId(me)));
        model.addAttribute("nsfwBlur", nsfwPolicy.blurInListings());
        model.addAttribute("currentPage", page);
        model.addAttribute("canManage", albumService.canManage(album, me));
        return "album";
    }

    /**
     * 个人主页：/u/{username} 为规范地址；/{username} 保留兼容原版直链。
     * 注意 /{username} 会与 /admin 等固定路由冲突（管理员用户名恰为 admin 时
     * 会命中管理后台），因此站内链接统一使用 /u/ 前缀。
     */
    @GetMapping({"/u/{username}", "/{username:[A-Za-z0-9_\\-\\u4e00-\\u9fa5]{2,32}}"})
    public String profile(@PathVariable String username,
                          @RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "images") String tab,
                          Model model, HttpSession session) {
        auth.common(model, session);
        Optional<User> user = userService.findByUsername(username);
        if (user.isEmpty()) {
            return notFound(model);
        }
        User me = auth.currentUser(session).orElse(null);
        boolean owner = me != null && me.getUserId().equals(user.get().getUserId());
        // 看自己的主页恒可见自己的 NSFW 图片（原版 is_owner 跳过过滤）
        Page<Image> images = imageService.byUser(user.get(), page, 24,
                nsfwPolicy.allowOnProfile(me, user.get()), nsfwPolicy.viewerId(me));
        model.addAttribute("profile", user.get());
        // 供模板用 @{...} 路径变量生成链接；URL 参数（page/tab）必须写在 @{...} 的
        // 括号内才会被解析成查询串，不能靠字符串拼接（拼接会原样输出成 /u/admin(tab=albums)）
        model.addAttribute("profileUsername", user.get().getUserUsername());
        model.addAttribute("tab", tab);
        model.addAttribute("isOwner", owner);
        model.addAttribute("images", images);
        model.addAttribute("nsfwBlur", nsfwPolicy.blurInListings());
        model.addAttribute("currentPage", images.getNumber() + 1);
        model.addAttribute("totalPages", images.getTotalPages());
        // populate 负责 idEncoded / 封面 / 真实图片数，缺了它专辑链接会变成 /album/null
        var albums = albumService.byUserVisible(user.get(), 1, 60).getContent();
        albumService.populateAll(albums);
        model.addAttribute("albums", albums);
        return "user";
    }

    private String notFound(Model model) {
        model.addAttribute("errorMsg", "内容不存在或已被删除");
        return "error/generic";
    }
}
