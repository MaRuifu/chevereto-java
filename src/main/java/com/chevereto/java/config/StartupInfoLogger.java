package com.chevereto.java.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 启动完成后在日志里打印可点击/可复制的访问链接。
 *
 * <p>端口不读配置，而是从已启动的 WebServer 取真实端口——{@code SERVER__PORT}
 * 环境变量、{@code --server.port=0} 随机端口等场景都能显示正确值。
 *
 * <p>排在 {@link StartupInitializer}（order=1）之后执行，因此打印时
 * 「是否已配置数据库」已经确定。
 */
@Component
@Order(100)
public class StartupInfoLogger implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupInfoLogger.class);

    private final ApplicationContext ctx;
    private final Environment env;
    private final InstanceConfig instanceConfig;
    private final String storageRoot;

    public StartupInfoLogger(ApplicationContext ctx,
                             Environment env,
                             InstanceConfig instanceConfig,
                             @Value("${app.local-storage-root:./data/uploads}") String storageRoot) {
        this.ctx = ctx;
        this.env = env;
        this.instanceConfig = instanceConfig;
        this.storageRoot = storageRoot;
    }

    @Override
    public void run(ApplicationArguments args) {
        int port = resolvePort();
        String ctxPath = ctxPath();
        String local = "http://localhost:" + port + ctxPath;
        boolean configured = instanceConfig.isConfigured();

        StringBuilder sb = new StringBuilder();
        sb.append('\n');
        sb.append("==========================================================\n");
        sb.append("  Chevereto-Java 启动完成，访问链接如下\n");
        sb.append("----------------------------------------------------------\n");
        sb.append("  首页      : ").append(local).append("/\n");
        sb.append("  后台管理  : ").append(local).append("/admin\n");
        sb.append("  登录/注册 : ").append(local).append("/login\n");
        sb.append("  我的主页  : ").append(local).append("/dashboard\n");
        if (!configured) {
            sb.append("  安装向导  : ").append(local).append("/setup   ← 实例未配置，请先完成安装\n");
        } else {
            sb.append("  安装向导  : ").append(local).append("/setup   （实例已配置，可忽略）\n");
        }
        List<String> lan = lanAddresses();
        if (lan.isEmpty()) {
            sb.append("  局域网    : 未检测到可用网卡地址\n");
        } else {
            for (int i = 0; i < lan.size(); i++) {
                sb.append(i == 0 ? "  局域网    : " : "              ")
                        .append("http://").append(lan.get(i)).append(':').append(port).append(ctxPath).append("/\n");
            }
        }
        sb.append("----------------------------------------------------------\n");
        sb.append("  数据库    : ").append(configured ? describeDb() : "未配置（等待安装向导）").append('\n');
        sb.append("  图片目录  : ").append(describePath(storageRoot)).append('\n');
        sb.append("  监听端口  : ").append(port).append('\n');
        sb.append("==========================================================");
        log.info(sb.toString());
    }

    private int resolvePort() {
        if (ctx instanceof WebServerApplicationContext wac) {
            var server = wac.getWebServer();
            if (server != null) {
                return server.getPort();
            }
        }
        return env.getProperty("local.server.port", Integer.class,
                env.getProperty("server.port", Integer.class, 8080));
    }

    private String ctxPath() {
        String p = env.getProperty("server.servlet.context-path", "").trim();
        if (p.isEmpty() || "/".equals(p)) {
            return "";
        }
        return p.startsWith("/") ? p : "/" + p;
    }

    private String describeDb() {
        if (instanceConfig.isH2()) {
            return "H2（内置）./data/db/chevereto";
        }
        String url = instanceConfig.dbUrl();
        // 只显示 host:port/db，隐去账号密码
        String shortUrl = url.replaceAll("\\?.*$", "");
        return "MySQL（外部）" + shortUrl + "   用户：" + instanceConfig.dbUsername();
    }

    /** 相对路径转绝对路径，并标出目录是否存在，省得用户猜图片到底存哪了 */
    private static String describePath(String raw) {
        try {
            java.nio.file.Path p = java.nio.file.Paths.get(raw).toAbsolutePath().normalize();
            return p + (java.nio.file.Files.isDirectory(p) ? "" : "  (目录不存在)");
        } catch (Exception e) {
            return raw;
        }
    }

    /** 取本机所有可用 IPv4 地址（过滤回环、虚拟网卡、各类隧道网卡） */
    private static List<String> lanAddresses() {
        List<String> list = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual() || ni.isPointToPoint()) {
                    continue;
                }
                String name = ni.getName();
                if (name == null || name.startsWith("docker") || name.startsWith("br-")
                        || name.startsWith("veth") || name.startsWith("utun")
                        || name.startsWith("llw") || name.startsWith("awdl")
                        || name.startsWith("bridge") || name.startsWith("vmnet")
                        || name.startsWith("vboxnet")) {
                    continue;
                }
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress() && !addr.isLinkLocalAddress()) {
                        String ip = addr.getHostAddress();
                        if (!list.contains(ip)) {
                            list.add(ip);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // 取不到就不显示局域网地址，不影响启动
        }
        return list;
    }
}
