package com.smartwash.game.storage;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

/**
 * 游戏静态资源对象存储抽象（MinIO game/{gameKey}/ 前缀语义）。
 * 与上传用的 FileStorageService 分离：游戏域需要「前缀清理 + 原始流回读 + 元数据」能力，
 * 且访问一律经后端转发（方案 §四 方案 B），桶保持私有可收敛（不依赖公共读直链）。
 */
public interface GameObjectStorage {

    /**
     * 写入一个对象（覆盖同键）。
     *
     * @param objectKey   对象键（如 game/snake/index.html）
     * @param content     内容字节
     * @param contentType MIME 类型
     */
    void putObject(String objectKey, byte[] content, String contentType);

    /**
     * 删除指定前缀下全部对象（前缀回收，对齐 delivery 清理模式；前缀不存在时幂等静默）。
     *
     * @param prefix 对象键前缀（如 game/snake/）
     */
    void removeObjectsByPrefix(String prefix);

    /**
     * 打开对象读取流与元数据；对象不存在返回 null（转发层据此给 404 语义）。
     *
     * @param objectKey 对象键
     * @return 对象流元数据，不存在为 null
     */
    GameObject getObject(String objectKey);

    /**
     * 对象流 + 元数据封装；close() 释放底层流。
     */
    record GameObject(InputStream stream, long contentLength, String etag) implements Closeable {

        @Override
        public void close() throws IOException {
            if (stream != null) {
                stream.close();
            }
        }
    }
}
