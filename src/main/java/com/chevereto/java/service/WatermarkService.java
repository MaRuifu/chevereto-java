package com.chevereto.java.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * 水印服务 —— 逐行移植原版 Chevereto Image::watermark()（Intervention/Image 实现）。
 *
 * 原版规则：
 *  - 水印面积 = 图宽 × 图高 × (watermark_percentage / 100)，按水印宽高比解出目标高
 *  - 超出图片边界时逐级收缩（含 margin 联动收缩）
 *  - position 形如 "left top"（水平 垂直），映射为 Intervention 的 "top-left" 等
 *  - opacity 0~100（100 为不透明）
 *  - 尺寸 < watermark_target_min_width/height 或为 GIF（未开 enable_file_gif）时跳过
 */
@Service
public class WatermarkService {

    private final SettingsService settingsService;

    public WatermarkService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    /** 水印是否应该应用到该图片（原版上传管线中的判断逻辑） */
    public boolean shouldApply(int width, int height, String ext, boolean animated, boolean isAdmin, boolean loggedIn) {
        if (!settingsService.getBool("watermark_enable")) {
            return false;
        }
        String role = isAdmin ? "admin" : (loggedIn ? "user" : "guest");
        if (!settingsService.getBool("watermark_enable_" + role)) {
            return false;
        }
        boolean isGif = "gif".equalsIgnoreCase(ext);
        if (isGif && !settingsService.getBool("watermark_enable_file_gif")) {
            return false;
        }
        if (animated) {
            return false; // 原版：动图一律不打水印
        }
        int minW = settingsService.getInt("watermark_target_min_width", 100);
        int minH = settingsService.getInt("watermark_target_min_height", 100);
        if (minW > 0 && width < minW) {
            return false;
        }
        if (minH > 0 && height < minH) {
            return false;
        }
        return true;
    }

    /**
     * 给图片字节打上水印并按原格式重编码。
     * 编码失败或水印文件缺失时返回原字节（不阻断上传，与原版容错一致）。
     */
    public byte[] apply(byte[] data, String ext, int width, int height) {
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(data));
            if (source == null) {
                return data;
            }
            BufferedImage mark = loadWatermark();
            if (mark == null) {
                return data;
            }

            double ratio = clampRatio(settingsService.getInt("watermark_percentage", 4) / 100.0);
            String[] position = resolvePosition(settingsService.get("watermark_position"));
            int margin = settingsService.getInt("watermark_margin", 10);
            int opacity = Math.max(0, Math.min(100, settingsService.getInt("watermark_opacity", 50)));

            // ---- 面积比算法（原版 Image::watermark()）----
            int imgW = source.getWidth();
            int imgH = source.getHeight();
            double wmRatio = (double) mark.getWidth() / mark.getHeight();
            double area = imgW * (double) imgH * ratio;
            double newH = Math.round(Math.sqrt(area / wmRatio));
            if (newH > imgH) {
                newH = imgH;
            }
            if (margin > 0 && !"center".equals(position[1]) && newH + margin > imgH) {
                newH -= newH + 2.0 * margin - imgH;
            }
            double newW = Math.round(wmRatio * newH);
            if (newW > imgW) {
                newW = imgW;
            }
            if (margin > 0 && !"center".equals(position[0]) && newW + margin > imgW) {
                newW -= newW + 2.0 * margin - imgW;
                newH = newW / wmRatio;
            }
            if (newW < 1 || newH < 1) {
                return data;
            }

            BufferedImage scaled = scale(mark, (int) Math.round(newW), (int) Math.round(newH));

            // ---- 定位（Intervention insert 语义：anchor 名 + margin）----
            int x = anchorX(position[0], imgW, scaled.getWidth(), margin);
            int y = anchorY(position[1], imgH, scaled.getHeight(), margin);

            // ---- 透明度（0~100，100 为原样）----
            BufferedImage markOpaque = argb(scaled);
            if (opacity < 100) {
                float alpha = opacity / 100.0f;
                Graphics2D g = markOpaque.createGraphics();
                g.setComposite(AlphaComposite.SrcOver.derive(alpha));
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(scaled, 0, 0, null);
                g.dispose();
            }

            // ---- 合成 ----
            BufferedImage canvas = argb(source);
            Graphics2D g = canvas.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(markOpaque, x, y, null);
            g.dispose();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            String format = encodeFormat(ext);
            // JPEG / GIF 不支持 ARGB：必须先压平到 RGB，否则 ImageIO.write 失败（水印会被静默丢弃）
            BufferedImage toWrite = ("png".equals(format) || "bmp".equals(format)) ? canvas : flattenToRgb(canvas);
            if (!ImageIO.write(toWrite, format, out)) {
                return data;
            }
            return out.toByteArray();
        } catch (Exception e) {
            return data; // 打水印失败不阻断上传
        }
    }

    // ---------------- 水印文件解析 ----------------

    /**
     * 水印图片来源（已去掉原版 content 目录依赖）：
     * 1. 设置项 watermark_image 写成<b>绝对路径</b>且文件存在 → 用它（自定义水印）
     * 2. 内置默认水印（随 jar 打包）：classpath:static/assets/watermark-default.png
     * 3. 运行时生成的文字水印（兜底，保证功能不缺失）
     */
    private BufferedImage loadWatermark() {
        String setting = settingsService.get("watermark_image");
        if (setting != null && !setting.isBlank()) {
            try {
                Path file = Paths.get(setting.trim());
                if (file.isAbsolute() && Files.isRegularFile(file)) {
                    BufferedImage custom = ImageIO.read(file.toFile());
                    if (custom != null) {
                        return custom;
                    }
                }
            } catch (Exception ignored) {
                // 路径非法/读不出来就退回内置水印
            }
        }
        BufferedImage bundled = bundledWatermark();
        return bundled != null ? bundled : fallbackWatermark();
    }

    /** 内置默认水印（static/assets/watermark-default.png，fat jar 内同样可读） */
    private BufferedImage bundledWatermark() {
        try (InputStream in = new ClassPathResource("static/assets/watermark-default.png").getInputStream()) {
            return ImageIO.read(in);
        } catch (Exception e) {
            return null;
        }
    }

    /** 水印文件缺失时运行时生成兜底（半透明圆角文字），避免功能不可用 */
    private BufferedImage fallbackWatermark() {
        try {
            String text = "WATERMARK";
            java.awt.Font font = new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 48);
            BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            java.awt.geom.Rectangle2D bounds = probe.createGraphics().getFontMetrics(font).getStringBounds(text, null);
            int w = (int) Math.ceil(bounds.getWidth()) + 40;
            int h = (int) Math.ceil(bounds.getHeight()) + 30;
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setComposite(AlphaComposite.Clear);
            g.fillRect(0, 0, w, h);
            g.setComposite(AlphaComposite.SrcOver);
            g.setColor(new java.awt.Color(255, 255, 255, 170));
            g.fillRoundRect(0, 0, w, h, 18, 18);
            g.setColor(new java.awt.Color(0, 0, 0, 200));
            g.setFont(font);
            g.drawString(text, 20, h - 18);
            g.dispose();
            return img;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------- 定位与工具 ----------------

    /** 设置格式 "水平 垂直"，如 "left top" / "center center"；非法值回退 right/bottom（与原版一致） */
    private String[] resolvePosition(String setting) {
        String[] parts = setting == null ? new String[0] : setting.trim().toLowerCase(Locale.ROOT).split("\\s+");
        String h = parts.length > 0 ? parts[0] : "center";
        String v = parts.length > 1 ? parts[1] : "center";
        if (!java.util.List.of("left", "center", "right").contains(h)) {
            h = "right";
        }
        if (!java.util.List.of("top", "center", "bottom").contains(v)) {
            v = "bottom";
        }
        return new String[]{h, v};
    }

    private int anchorX(String h, int imgW, int markW, int margin) {
        return switch (h) {
            case "left" -> margin;
            case "center" -> (imgW - markW) / 2;
            default -> imgW - markW - margin;
        };
    }

    private int anchorY(String v, int imgH, int markH, int margin) {
        return switch (v) {
            case "top" -> margin;
            case "center" -> (imgH - markH) / 2;
            default -> imgH - markH - margin;
        };
    }

    private double clampRatio(double ratio) {
        return Math.min(1.0, Math.max(0.01, ratio));
    }

    private String encodeFormat(String ext) {
        return switch (ext == null ? "" : ext.toLowerCase(Locale.ROOT)) {
            case "png" -> "png";
            case "gif" -> "gif";
            case "bmp" -> "bmp";
            default -> "jpg";
        };
    }

    /** 压平到不透明 RGB（白底），供 JPEG / GIF 编码使用 */
    private BufferedImage flattenToRgb(BufferedImage src) {
        BufferedImage img = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, src.getWidth(), src.getHeight());
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return img;
    }

    private BufferedImage argb(BufferedImage src) {        if (src.getType() == BufferedImage.TYPE_INT_ARGB) {
            return src;
        }
        BufferedImage img = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return img;
    }

    private BufferedImage scale(BufferedImage src, int w, int h) {
        Image scaled = src.getScaledInstance(w, h, Image.SCALE_SMOOTH);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(scaled, 0, 0, null);
        g.dispose();
        return img;
    }
}
