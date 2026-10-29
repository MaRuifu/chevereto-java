package com.chevereto.java.storage;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import com.chevereto.java.entity.Storage;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;

/**
 * SFTP 存储驱动（JSch）。
 * 字段映射（与原版一致，无扩展列）：
 *  - storage_server = 主机（host 或 host:端口，端口默认 22）
 *  - storage_key / storage_secret = 用户名 / 密码
 *  - storage_account_id = 可选：私钥文件路径（密钥认证时填，密码作私钥口令）
 *  - 上传路径 = 登录目录（HOME）下的相对路径（原版行为）
 *  - storage_url = 对外访问基础 URL（建议配置 HTTP/CDN 域名）
 */
@Component
public class SftpStorageDriver implements StorageDriver {

    private static final int CONNECT_TIMEOUT_MS = 15_000;

    @Override
    public void store(Storage storage, String relativePath, byte[] data) {
        ChannelSftp sftp = null;
        Session session = null;
        try {
            session = connect(storage);
            sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect(CONNECT_TIMEOUT_MS);
            mkdirs(sftp, dirOf(storage, relativePath));
            try (var in = new ByteArrayInputStream(data)) {
                sftp.put(in, fullPath(storage, relativePath));
            }
        } catch (Exception e) {
            throw new StorageException("SFTP 上传失败: " + e.getMessage(), e);
        } finally {
            close(sftp, session);
        }
    }

    @Override
    public void delete(Storage storage, String relativePath) {
        ChannelSftp sftp = null;
        Session session = null;
        try {
            session = connect(storage);
            sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect(CONNECT_TIMEOUT_MS);
            try {
                sftp.rm(fullPath(storage, relativePath));
            } catch (SftpException e) {
                if (e.id != 2 /* NO_SUCH_FILE */) throw e;
            }
        } catch (Exception e) {
            throw new StorageException("SFTP 删除失败: " + e.getMessage(), e);
        } finally {
            close(sftp, session);
        }
    }

    private Session connect(Storage storage) throws Exception {
        String host = FtpStorageDriver.hostOf(storage.getStorageServer());
        int port = FtpStorageDriver.portOf(storage.getStorageServer(), 22);
        JSch jsch = new JSch();
        // 可选：私钥认证（storage_account_id = 私钥路径，storage_secret 作为私钥口令）
        String keyPath = storage.getStorageAccountId();
        if (keyPath != null && !keyPath.isBlank() && java.nio.file.Files.exists(java.nio.file.Path.of(keyPath.trim()))) {
            jsch.addIdentity(keyPath.trim(), storage.getStorageSecret());
        }
        Session session = jsch.getSession(storage.getStorageKey(), host, port);
        if (storage.getStorageSecret() != null && !storage.getStorageSecret().isBlank()) {
            session.setPassword(storage.getStorageSecret());
        }
        session.setConfig("StrictHostKeyChecking", "no");
        session.setConfig("PreferredAuthentications", "publickey,password,keyboard-interactive");
        session.setTimeout(CONNECT_TIMEOUT_MS);
        session.connect(CONNECT_TIMEOUT_MS);
        return session;
    }

    /** 逐级创建远程目录（已存在时忽略） */
    private void mkdirs(ChannelSftp sftp, String dir) throws SftpException {
        if (dir == null || dir.isBlank() || "/".equals(dir)) return;
        String[] parts = dir.split("/");
        StringBuilder cur = new StringBuilder();
        for (String p : parts) {
            if (p.isBlank()) { cur.append("/"); continue; }
            if (cur.isEmpty() || !cur.toString().endsWith("/")) cur.append("/");
            cur.append(p);
            if (!exists(sftp, cur.toString())) {
                sftp.mkdir(cur.toString());
            }
        }
    }

    private boolean exists(ChannelSftp sftp, String path) {
        try {
            sftp.stat(path);
            return true;
        } catch (SftpException e) {
            return false;
        }
    }

    private String dirOf(Storage storage, String relativePath) {
        int idx = relativePath.lastIndexOf('/');
        return idx > 0 ? relativePath.substring(0, idx) : "";
    }

    private String fullPath(Storage storage, String relativePath) {
        return "/" + relativePath;
    }

    private void close(ChannelSftp sftp, Session session) {
        if (sftp != null && sftp.isConnected()) sftp.disconnect();
        if (session != null && session.isConnected()) session.disconnect();
    }
}
