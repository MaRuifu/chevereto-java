import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 把 images 目录里「数据库还没有记录」的图片补录进 chv_images。
 *
 * 规则：
 *  - 文件名 = 磁盘真实文件名（去掉扩展名），扩展名 = 真实扩展名（保留大小写）。
 *  - 文件位置一律不动：
 *      · 目录形如 Y/m/d 且日期合法 → 与原版一致，写 image_path = NULL，由 image_date 推导同一路径；
 *      · 其它目录（如 blog/blogimg）→ 直接把真实相对路径写进 image_path。
 *  - 跳过 .th.（缩略图）/ .md.（中图）等派生物，跳过非图片文件。
 *  - 缺失 .th. 缩略图的顺便生成（不改动原图），webp 因 JDK 无法解码而跳过。
 *
 * 用法（MySQL 实例；驱动 jar 用项目依赖里的 mysql-connector-j）：
 *   javac -cp mysql-connector-j.jar tools/ImportMissingImages.java
 *   java -cp .:mysql-connector-j.jar ImportMissingImages \
 *        --root=/path/to/images --user=<图片归属的用户 id> \
 *        [--dry-run] [--no-thumbs] [--thumb-size=320] [--limit=N] \
 *        [--url=jdbc:mysql://...] [--db-user=root] [--db-pass=...]
 *
 * 用法（内置 H2 实例；把 H2 的 jar 换进 classpath，URL 抄 data/instance.properties 里那条）：
 *   java -cp .:h2-2.x.x.jar ImportMissingImages \
 *        --root=/path/to/images --user=1 --dry-run \
 *        --url='jdbc:h2:file:./data/db/chevereto;MODE=MySQL;AUTO_SERVER=TRUE' \
 *        --db-user=sa --db-pass=
 *
 * 连接信息也可用环境变量 DB_URL / DB_USER / DB_PASSWORD（命令行优先级更高）。
 * 应用正在运行时也能连（H2 需带 AUTO_SERVER=TRUE，MySQL 无限制）。
 * 这只是一次性迁移脚本，不属于应用运行时，也不会被 mvn 编译。
 */
public class ImportMissingImages {

    private static final Set<String> IMAGE_EXT = Set.of("png", "jpg", "jpeg", "gif", "webp", "bmp", "avif");
    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private static String root;
    private static boolean dryRun;
    private static boolean makeThumbs = true;
    /** 0 = 未指定；必须由 --user 显式给出，避免误把图片挂到别人的账号下 */
    private static long userId = 0L;
    private static int thumbSize = 320;
    private static int limit = -1;

    // 连接信息一律从命令行参数或环境变量来，源码里不写任何凭据
    private static String jdbcUrl = System.getenv().getOrDefault("DB_URL",
            "jdbc:mysql://127.0.0.1:3306/chevereto?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false");
    private static String jdbcUser = System.getenv().getOrDefault("DB_USER", "root");
    private static String jdbcPass = System.getenv().getOrDefault("DB_PASSWORD", "");

    public static void main(String[] args) throws Exception {
        for (String a : args) {
            if (a.startsWith("--root=")) root = a.substring(7);
            else if (a.equals("--dry-run")) dryRun = true;
            else if (a.equals("--no-thumbs")) makeThumbs = false;
            else if (a.startsWith("--user=")) userId = Long.parseLong(a.substring(7));
            else if (a.startsWith("--thumb-size=")) thumbSize = Integer.parseInt(a.substring(13));
            else if (a.startsWith("--limit=")) limit = Integer.parseInt(a.substring(8));
            else if (a.startsWith("--url=")) jdbcUrl = a.substring(6);
            else if (a.startsWith("--db-user=")) jdbcUser = a.substring(10);
            else if (a.startsWith("--db-pass=")) jdbcPass = a.substring(10);
        }
        if (root == null) {
            System.err.println("必须指定 --root=/path/to/images");
            System.exit(2);
        }
        if (userId <= 0) {
            System.err.println("必须指定 --user=<图片归属的用户 id>（chv_users.user_id）");
            System.exit(2);
        }
        Path rootPath = Paths.get(root).toAbsolutePath().normalize();
        if (!Files.isDirectory(rootPath)) {
            System.err.println("目录不存在: " + rootPath);
            System.exit(2);
        }

        System.out.println("images 根目录: " + rootPath);
        System.out.println("模式: " + (dryRun ? "试运行（不写库）" : "正式导入") + " | 缩略图: " + (makeThumbs ? "生成" : "跳过"));

        try (Connection conn = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPass)) {
            Set<String> existing = loadExistingPaths(conn);
            System.out.println("数据库现有图片记录: " + existing.size() + " 条");

            List<Path> files = scan(rootPath);
            System.out.println("磁盘图片文件（已剔除缩略图/中图）: " + files.size() + " 个");

            List<Path> missing = new ArrayList<>();
            for (Path f : files) {
                String rel = rootPath.relativize(f).toString().replace('\\', '/');
                if (!existing.contains(rel)) missing.add(f);
            }
            System.out.println("需要补录: " + missing.size() + " 个");
            if (limit > 0 && missing.size() > limit) {
                missing = new ArrayList<>(missing.subList(0, limit));
                System.out.println("本次仅处理前 " + limit + " 个（--limit）");
            }
            if (missing.isEmpty()) {
                System.out.println("没有需要导入的图片。");
                return;
            }

            long thumbsMade = 0, skippedRead = 0, tooLong = 0;
            List<Row> rows = new ArrayList<>();
            for (int i = 0; i < missing.size(); i++) {
                Path f = missing.get(i);
                String rel = rootPath.relativize(f).toString().replace('\\', '/');
                String fileName = f.getFileName().toString();
                int dot = fileName.lastIndexOf('.');
                String baseName = dot > 0 ? fileName.substring(0, dot) : fileName;
                String ext = dot > 0 ? fileName.substring(dot + 1) : "";
                if (baseName.length() > 255 || fileName.length() > 255) {
                    tooLong++;
                    System.out.println("  跳过（文件名超 255 字节）: " + rel);
                    continue;
                }
                long size = Files.size(f);
                String md5 = md5(f);
                int[] dim = dimensions(f, ext);
                LocalDateTime date = resolveDate(f, rel);
                boolean datefolder = isDateFolderPath(rel);
                String imagePath = datefolder ? null : rel;

                Path thumb = thumbPath(f, ext);
                long thumbSize = 0;
                if (Files.exists(thumb)) {
                    thumbSize = Files.size(thumb);
                } else if (makeThumbs && !"webp".equalsIgnoreCase(ext)) {
                    byte[] t = thumbnail(f, ext);
                    if (t != null) {
                        if (!dryRun) Files.write(thumb, t);
                        thumbSize = t.length;
                        thumbsMade++;
                    }
                }
                if (dim[0] == 0 || dim[1] == 0) skippedRead++;

                rows.add(new Row(baseName, ext, size, dim[0], dim[1], date, imagePath, md5, fileName,
                        thumbSize, isAnimatedGif(f, ext)));

                if ((i + 1) % 200 == 0) System.out.println("  已处理 " + (i + 1) + " / " + missing.size());
            }

            System.out.println("生成缩略图: " + thumbsMade + " 个 | 尺寸读取失败: " + skippedRead + " 个 | 文件名超长跳过: " + tooLong + " 个");

            if (dryRun) {
                System.out.println("\n[试运行] 预览前 10 条：");
                for (int i = 0; i < Math.min(10, rows.size()); i++) {
                    Row r = rows.get(i);
                    System.out.printf("  %s.%s  %dx%d  %dKB  date=%s  path=%s%n",
                            r.name, r.ext, r.width, r.height, r.size / 1024, r.date,
                            r.imagePath == null ? "<按日期推导>" : r.imagePath);
                }
                System.out.println("[试运行] 未写入数据库，共待插入 " + rows.size() + " 条。");
                return;
            }

            int inserted = insert(conn, rows);
            System.out.println("完成：插入 " + inserted + " 条记录。");
        }
    }

    private static Set<String> loadExistingPaths(Connection conn) throws Exception {
        Set<String> set = new LinkedHashSet<>();
        // 不用 DATE_FORMAT()：MySQL 专有函数，H2 不认识；日期在 Java 侧格式化即可
        String sql = "SELECT image_name, image_extension, image_path, image_date FROM chv_images";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String p = rs.getString(3);
                if (p == null || p.isBlank()) {
                    java.sql.Timestamp ts = rs.getTimestamp(4);
                    String d = ts == null ? "" : DATE_DIR.format(ts.toLocalDateTime().toLocalDate());
                    p = d + "/" + rs.getString(1) + "." + rs.getString(2);
                }
                set.add(p);
            }
        }
        return set;
    }

    /** 扫描所有图片，剔除 .th./.md. 派生物与非图片文件 */
    private static List<Path> scan(Path rootPath) throws IOException {
        List<Path> out = new ArrayList<>();
        Files.walkFileTree(rootPath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String name = file.getFileName().toString();
                if (!attrs.isRegularFile()) return FileVisitResult.CONTINUE;
                if (name.startsWith(".") || name.equalsIgnoreCase(".DS_Store")) return FileVisitResult.CONTINUE;
                int dot = name.lastIndexOf('.');
                if (dot < 0) return FileVisitResult.CONTINUE;
                String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
                if (!IMAGE_EXT.contains(ext)) return FileVisitResult.CONTINUE;
                String lower = name.toLowerCase(Locale.ROOT);
                // 派生物：a.th.png（缩略图）、a.md.jpg（中图）
                if (lower.matches(".*\\.(th|md)\\.[a-z0-9]+$")) return FileVisitResult.CONTINUE;
                out.add(file);
                return FileVisitResult.CONTINUE;
            }
        });
        out.sort((a, b) -> rootPath.relativize(a).toString().compareTo(rootPath.relativize(b).toString()));
        return out;
    }

    /** rel 形如 2021/03/28/foo.png 且日期合法 → 走原版 datefolder 推导 */
    private static boolean isDateFolderPath(String rel) {
        String[] parts = rel.split("/");
        if (parts.length != 4) return false;
        try {
            LocalDate.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static LocalDateTime resolveDate(Path f, String rel) throws IOException {
        LocalDateTime mtime = LocalDateTime.ofInstant(Files.getLastModifiedTime(f).toInstant(),
                java.time.ZoneId.systemDefault());
        if (isDateFolderPath(rel)) {
            String[] parts = rel.split("/");
            LocalDate d = LocalDate.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            LocalTime t = mtime.toLocalTime();
            return LocalDateTime.of(d, t);
        }
        return mtime;
    }

    private static Path thumbPath(Path file, String ext) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String e = dot > 0 ? name.substring(dot + 1) : ext;
        return file.resolveSibling(base + ".th." + e);
    }

    private static String md5(Path f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (InputStream in = Files.newInputStream(f)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static int[] dimensions(Path f, String ext) {
        try {
            BufferedImage img = ImageIO.read(f.toFile());
            if (img != null) return new int[]{img.getWidth(), img.getHeight()};
        } catch (Exception ignored) {
        }
        // WebP/AVIF JDK 解不了，退回 macOS 的 sips
        try {
            Process p = new ProcessBuilder("sips", "-g", "pixelWidth", "-g", "pixelHeight", f.toString())
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes());
            p.waitFor();
            int w = 0, h = 0;
            for (String line : out.split("\n")) {
                line = line.trim();
                if (line.startsWith("pixelWidth:")) w = Integer.parseInt(line.substring(11).trim());
                if (line.startsWith("pixelHeight:")) h = Integer.parseInt(line.substring(12).trim());
            }
            if (w > 0 && h > 0) return new int[]{w, h};
        } catch (Exception ignored) {
        }
        return new int[]{0, 0};
    }

    private static boolean isAnimatedGif(Path f, String ext) {
        if (!"gif".equalsIgnoreCase(ext)) return false;
        try (ImageInputStream in = ImageIO.createImageInputStream(f.toFile())) {
            Iterator<ImageReader> it = ImageIO.getImageReaders(in);
            if (!it.hasNext()) return false;
            ImageReader reader = it.next();
            try {
                reader.setInput(in);
                return reader.getNumImages(true) > 1;
            } finally {
                reader.dispose();
            }
        } catch (Exception e) {
            return false;
        }
    }

    /** 生成缩略图字节（fit 到 thumbSize 方形内，不放大），解码失败返回 null */
    private static byte[] thumbnail(Path f, String ext) {
        BufferedImage src;
        try {
            src = ImageIO.read(f.toFile());
        } catch (Exception e) {
            return null;
        }
        if (src == null) return null;
        int w = src.getWidth(), h = src.getHeight();
        if (w <= 0 || h <= 0) return null;
        if (w <= thumbSize && h <= thumbSize) {
            try {
                return Files.readAllBytes(f);
            } catch (IOException e) {
                return null;
            }
        }
        double scale = Math.min((double) thumbSize / w, (double) thumbSize / h);
        int tw = Math.max(1, (int) Math.round(w * scale));
        int th = Math.max(1, (int) Math.round(h * scale));
        String format = switch (ext.toLowerCase(Locale.ROOT)) {
            case "png" -> "png";
            case "gif" -> "gif";
            case "bmp" -> "bmp";
            default -> "jpg";
        };
        BufferedImage dst;
        boolean alpha = src.getColorModel().hasAlpha();
        if ("jpg".equals(format) || !alpha) {
            dst = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
        } else {
            dst = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
        }
        Graphics2D g = dst.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, tw, th, null);
        } finally {
            g.dispose();
        }
        try {
            return encode(dst, format);
        } catch (IOException e) {
            return null;
        }
    }

    private static byte[] encode(BufferedImage img, String format) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);
        if (!writers.hasNext()) return null;
        ImageWriter writer = writers.next();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(bos)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed() && ("jpg".equals(format) || "jpeg".equals(format))) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(0.85f);
            }
            writer.write(null, new IIOImage(img, null, null), param);
        } finally {
            writer.dispose();
        }
        return bos.toByteArray();
    }

    private static int insert(Connection conn, List<Row> rows) throws Exception {
        String sql = "INSERT INTO chv_images (" +
                "image_name, image_extension, image_size, image_width, image_height, " +
                "image_date, image_date_gmt, image_title, image_description, image_nsfw, " +
                "image_user_id, image_album_id, image_uploader_ip, image_storage_mode, image_path, " +
                "image_storage_id, image_md5, image_source_md5, image_original_filename, image_original_exifdata, " +
                "image_views, image_category_id, image_chain, image_thumb_size, image_medium_size, " +
                "image_expiration_date_gmt, image_likes, image_is_animated, image_is_approved, image_is_360, image_privacy" +
                ") VALUES (?,?,?,?,?, ?,?,NULL,NULL,0, ?,NULL,?,?,?, NULL,?,NULL,?,NULL, 0,NULL,5,?,0, NULL,0,?,1,0,'public')";
        boolean oldAuto = conn.getAutoCommit();
        conn.setAutoCommit(false);
        int n = 0;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Row r : rows) {
                ps.setString(1, r.name);
                ps.setString(2, r.ext);
                ps.setLong(3, r.size);
                ps.setInt(4, r.width);
                ps.setInt(5, r.height);
                ps.setObject(6, r.date);
                ps.setObject(7, r.date);
                ps.setLong(8, userId);
                ps.setString(9, "127.0.0.1");
                ps.setString(10, "datefolder");
                ps.setString(11, r.imagePath);
                ps.setString(12, r.md5);
                ps.setString(13, r.originalFilename);
                ps.setLong(14, r.thumbSize);
                ps.setBoolean(15, r.animated);
                ps.addBatch();
                if (++n % 200 == 0) ps.executeBatch();
            }
            ps.executeBatch();
            conn.commit();
        } catch (Exception e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(oldAuto);
        }
        return rows.size();
    }

    private static final class Row {
        final String name;
        final String ext;
        final long size;
        final int width;
        final int height;
        final LocalDateTime date;
        final String imagePath;
        final String md5;
        final String originalFilename;
        final long thumbSize;
        final boolean animated;

        Row(String name, String ext, long size, int width, int height, LocalDateTime date,
            String imagePath, String md5, String originalFilename, long thumbSize, boolean animated) {
            this.name = name;
            this.ext = ext;
            this.size = size;
            this.width = width;
            this.height = height;
            this.date = date;
            this.imagePath = imagePath;
            this.md5 = md5;
            this.originalFilename = originalFilename;
            this.thumbSize = thumbSize;
            this.animated = animated;
        }
    }
}
