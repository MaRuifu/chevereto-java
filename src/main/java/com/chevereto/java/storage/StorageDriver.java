package com.chevereto.java.storage;

import com.chevereto.java.entity.Storage;

/**
 * 存储驱动统一接口。
 */
public interface StorageDriver {

    /**
     * 保存文件。
     *
     * @param storage      存储配置
     * @param relativePath 存储内相对路径，如 2026/10/27/abc.jpg
     * @param data         文件内容
     */
    void store(Storage storage, String relativePath, byte[] data);

    /** 删除文件（不存在时静默成功） */
    void delete(Storage storage, String relativePath);
}
