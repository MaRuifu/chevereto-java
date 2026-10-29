package com.chevereto.java.storage;

import com.chevereto.java.entity.Storage;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * FTP 存储驱动（Apache Commons Net）。
 * 字段映射（与原版一致，无扩展列）：
 *  - storage_server = 主机（host 或 host:端口，端口默认 21）
 *  - storage_key / storage_secret = 用户名 / 密码
 *  - 上传路径 = 登录目录（HOME）下的相对路径，无远程根配置（原版行为）
 *  - storage_url = 对外访问基础 URL（FTP 无站内直链，建议配置 CDN/HTTP 域名）
 */
@Component
public class FtpStorageDriver implements StorageDriver {

    @Override
    public void store(Storage storage, String relativePath, byte[] data) {
        FTPClient ftp = new FTPClient();
        try {
            connect(storage, ftp);
            mkdirs(ftp, dirOf(storage, relativePath));
            String path = fullPath(storage, relativePath);
            ftp.setFileType(FTP.BINARY_FILE_TYPE);
            try (var in = new ByteArrayInputStream(data)) {
                if (!ftp.storeFile(path, in)) {
                    throw new StorageException("FTP 上传失败: " + path + " -> " + ftp.getReplyString());
                }
            }
            ftp.logout();
        } catch (IOException e) {
            throw new StorageException("FTP 连接/传输失败: " + e.getMessage(), e);
        } finally {
            if (ftp.isConnected()) {
                try { ftp.disconnect(); } catch (IOException ignored) { }
            }
        }
    }

    @Override
    public void delete(Storage storage, String relativePath) {
        FTPClient ftp = new FTPClient();
        try {
            connect(storage, ftp);
            ftp.deleteFile(fullPath(storage, relativePath));
            ftp.logout();
        } catch (IOException e) {
            throw new StorageException("FTP 删除失败: " + e.getMessage(), e);
        } finally {
            if (ftp.isConnected()) {
                try { ftp.disconnect(); } catch (IOException ignored) { }
            }
        }
    }

    private void connect(Storage storage, FTPClient ftp) throws IOException {
        String host = hostOf(storage.getStorageServer());
        int port = portOf(storage.getStorageServer(), 21);
        ftp.setControlEncoding("UTF-8");
        ftp.connect(host, port);
        if (!FTPReply.isPositiveCompletion(ftp.getReplyCode())) {
            throw new IOException("FTP 连接被拒绝: " + host + ":" + port + " -> " + ftp.getReplyString());
        }
        String user = storage.getStorageKey() == null || storage.getStorageKey().isBlank()
                ? "anonymous" : storage.getStorageKey();
        String pass = storage.getStorageSecret() == null ? "" : storage.getStorageSecret();
        if (!ftp.login(user, pass)) {
            throw new IOException("FTP 登录失败（用户名/密码错误）: " + user);
        }
        ftp.enterLocalPassiveMode();
        ftp.setFileType(FTP.BINARY_FILE_TYPE);
    }

    /** 逐级创建远程目录（已存在时忽略 550） */
    private void mkdirs(FTPClient ftp, String dir) throws IOException {
        if (dir == null || dir.isBlank() || "/".equals(dir)) return;
        String[] parts = dir.split("/");
        StringBuilder cur = new StringBuilder();
        for (String p : parts) {
            if (p.isBlank()) { cur.append("/"); continue; }
            if (cur.isEmpty() || !cur.toString().endsWith("/")) cur.append("/");
            cur.append(p);
            ftp.makeDirectory(cur.toString());
            // 已存在返回 550，忽略即可
        }
    }

    /** 相对路径的目录部分（远程根 = 登录目录，与原版一致） */
    private String dirOf(Storage storage, String relativePath) {
        int idx = relativePath.lastIndexOf('/');
        return idx > 0 ? relativePath.substring(0, idx) : "";
    }

    private String fullPath(Storage storage, String relativePath) {
        return "/" + relativePath;
    }

    static String hostOf(String server) {
        String s = server == null ? "" : server.trim();
        s = s.replaceFirst("^ftps?://", "").replaceFirst("^sftp://", "");
        int slash = s.indexOf('/');
        if (slash > 0) s = s.substring(0, slash);
        int colon = s.lastIndexOf(':');
        return colon > 0 ? s.substring(0, colon) : s;
    }

    static int portOf(String server, int defaultPort) {
        String s = server == null ? "" : server.trim();
        s = s.replaceFirst("^ftps?://", "").replaceFirst("^sftp://", "");
        int slash = s.indexOf('/');
        if (slash > 0) s = s.substring(0, slash);
        int colon = s.lastIndexOf(':');
        if (colon > 0) {
            try { return Integer.parseInt(s.substring(colon + 1)); } catch (NumberFormatException ignored) { }
        }
        return defaultPort;
    }
}
