package com.chevereto.java.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.Directory;
import com.drew.metadata.TagDescriptor;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;

/**
 * 图片工具：魔数嗅探、尺寸检测、缩略图生成。
 */
@Service
public class ImageUtils {

    public record ImageType(String ext, String mime) {}

    private static final Map<String, String> MIME_BY_EXT = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "gif", "image/gif",
            "webp", "image/webp",
            "bmp", "image/bmp",
            "avif", "image/avif"
    );

    public boolean supported(String ext) {
        return ext != null && MIME_BY_EXT.containsKey(ext.toLowerCase(Locale.ROOT));
    }

    public String mimeOf(String ext) {
        return MIME_BY_EXT.getOrDefault(ext.toLowerCase(Locale.ROOT), "application/octet-stream");
    }

    /** 通过魔数判断真实图片类型，非法内容抛出异常 */
    public ImageType sniff(byte[] data) {
        if (data == null || data.length < 12) {
            throw new IllegalArgumentException("文件内容为空或过小，不是有效图片");
        }
        if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
            return new ImageType("jpg", "image/jpeg");
        }
        if ((data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
            return new ImageType("png", "image/png");
        }
        if (data[0] == 'G' && data[1] == 'I' && data[2] == 'F' && data[3] == '8') {
            return new ImageType("gif", "image/gif");
        }
        if (data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            return new ImageType("webp", "image/webp");
        }
        if (data[0] == 'B' && data[1] == 'M') {
            return new ImageType("bmp", "image/bmp");
        }
        if ((data[4] & 0xFF) == 0x66 && (data[5] & 0xFF) == 0x74 && (data[6] & 0xFF) == 0x79
                && (data[7] & 0xFF) == 0x70) { // ftyp box
            return new ImageType("avif", "image/avif");
        }
        throw new IllegalArgumentException("不支持的图片格式（仅支持 JPG/PNG/GIF/WebP/BMP/AVIF）");
    }

    /** 读取宽高；读取失败返回 0x0（不阻断上传） */
    public int[] dimensions(byte[] data) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(data));
            for (Directory directory : metadata.getDirectories()) {
                Integer w = directory.getInteger(com.drew.metadata.exif.ExifIFD0Directory.TAG_IMAGE_WIDTH);
                Integer h = directory.getInteger(com.drew.metadata.exif.ExifIFD0Directory.TAG_IMAGE_HEIGHT);
                if (w == null && "PNG".equals(directory.getString(0x0001))) {
                    // metadata-extractor PNG 目录 tag 不同，兜底用 ImageIO
                    break;
                }
                if (w != null && h != null) {
                    return new int[]{w, h};
                }
            }
        } catch (Exception ignored) {
            // 继续走 ImageIO
        }
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
            if (img != null) {
                return new int[]{img.getWidth(), img.getHeight()};
            }
        } catch (Exception ignored) {
        }
        return new int[]{0, 0};
    }

    /** 是否为多帧动图（GIF 动图 / 动态 WebP）；仅 gif/webp 检测，其余返回 false */
    public boolean isAnimated(byte[] data, String ext) {
        if (ext == null) return false;
        String e = ext.toLowerCase(Locale.ROOT);
        try {
            javax.imageio.stream.ImageInputStream in = javax.imageio.ImageIO.createImageInputStream(new ByteArrayInputStream(data));
            if (in == null) return false;
            java.util.Iterator<javax.imageio.ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) return false;
            javax.imageio.ImageReader reader = readers.next();
            if (!("gif".equals(e) || "webp".equals(e))) {
                reader.dispose();
                return false;
            }
            try {
                reader.setInput(in);
                return reader.getNumImages(true) > 1;
            } finally {
                reader.dispose();
            }
        } catch (Exception e2) {
            // 读取失败按静态图处理
            return false;
        }
    }

    /**
     * 生成缩略图（fit 边长 size），格式与原图扩展名一致（原版规则：a.png → a.th.png）。
     * 失败时返回原图字节。
     */
    public byte[] thumbnail(byte[] data, int size, String ext) {
        String format = switch (ext == null ? "" : ext.toLowerCase(Locale.ROOT)) {
            case "png" -> "png";
            case "gif" -> "gif";
            case "bmp" -> "bmp";
            default -> "jpg";
        };
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Thumbnails.of(new ByteArrayInputStream(data))
                    .size(size, size)
                    .outputFormat(format)
                    .toOutputStream(out);
            return out.toByteArray();
        } catch (IOException e) {
            return data;
        }
    }

    /** 随机文件基名（不含扩展名），按原版风格使用 base62 随机串 */
    public String randomBasename() {
        return com.chevereto.java.service.ApiKeyService.random(12);
    }
}
