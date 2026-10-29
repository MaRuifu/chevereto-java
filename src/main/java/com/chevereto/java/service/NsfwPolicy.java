package com.chevereto.java.service;

import com.chevereto.java.entity.User;
import org.springframework.stereotype.Service;

/**
 * NSFW 可见性与编辑策略 —— 对齐原版 Chevereto 的 Listing 隐私层 + content 设置。
 *
 * <h3>谁能看到列表里的 NSFW</h3>
 * <table>
 *   <tr><th>访问者</th><th>判定</th></tr>
 *   <tr><td>管理员</td><td>恒可见（原版 is_admin 完全跳过 NSFW 过滤）</td></tr>
 *   <tr><td>已登录用户</td><td>看自己的 {@code chv_users.user_show_nsfw_listings}，与全局开关无关</td></tr>
 *   <tr><td>游客</td><td>看全局 {@code show_nsfw_in_listings}</td></tr>
 * </table>
 *
 * <p>个人主页额外一条：看自己的主页时恒可见（原版 is_owner 跳过过滤），
 * 否则用户会看不到自己刚标记为 NSFW 的图片，误以为上传失败。
 *
 * <h3>只影响 images，不影响 albums</h3>
 * 与原版一致：{@code chv_albums} 没有 NSFW 列，专辑封面/列表不做过滤，
 * 只在专辑内的图片列表上应用同一套规则。
 *
 * <h3>模糊只是 CSS</h3>
 * {@code theme_nsfw_blur} 只给缩略图加一个模糊 class，图片地址不变、不禁用右键，
 * 属于「防误伤」而非「防访问」——原版也是这个定位。
 */
@Service
public class NsfwPolicy {

    private final SettingsService settingsService;

    public NsfwPolicy(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    /**
     * 当前访问者在「公开列表」（首页 / 探索 / 搜索 / 专辑内）能否看到 NSFW 内容。
     *
     * @param viewer 当前用户，游客传 null
     */
    public boolean allowInListings(User viewer) {
        if (viewer == null) {
            return settingsService.getBool("show_nsfw_in_listings");
        }
        if (viewer.isUserIsAdmin()) {
            return true;
        }
        return viewer.isUserShowNsfwListings();
    }

    /**
     * 当前访问者在某个用户主页能否看到 NSFW 内容。
     * 看自己的主页恒可见；其余沿用列表规则。
     */
    public boolean allowOnProfile(User viewer, User owner) {
        if (viewer != null && owner != null && viewer.getUserId() != null
                && viewer.getUserId().equals(owner.getUserId())) {
            return true;
        }
        return allowInListings(viewer);
    }

    /** 传给 Repository 的 viewerId：游客为 null，SQL 里据此只放行 image_nsfw = 0 */
    public Long viewerId(User viewer) {
        return viewer == null ? null : viewer.getUserId();
    }

    /** 列表缩略图是否做模糊（theme_nsfw_blur） */
    public boolean blurInListings() {
        return settingsService.getBool("theme_nsfw_blur");
    }

    /** 上传器是否显示「不适宜内容」复选框（theme_nsfw_upload_checkbox） */
    public boolean showUploadCheckbox() {
        return settingsService.getBool("theme_nsfw_upload_checkbox");
    }

    /**
     * NSFW 标记是否被锁定（image_lock_nsfw_editing）。
     * 锁定后只有管理员能改已有图片的 NSFW 标记；上传时自行勾选不受影响。
     */
    public boolean isEditingLocked() {
        return settingsService.getBool("image_lock_nsfw_editing");
    }

    /**
     * 编辑已有图片时，本次提交的 nsfw 值是否生效。
     *
     * @param operator 操作者
     * @param current  图片当前值
     * @param submitted 表单提交值
     * @return 真正要落库的值
     */
    public boolean resolveEditingNsfw(User operator, boolean current, boolean submitted) {
        if (!isEditingLocked()) {
            return submitted;
        }
        // 锁定状态下：管理员可改，其他人保持原值
        return operator != null && operator.isUserIsAdmin() ? submitted : current;
    }
}
