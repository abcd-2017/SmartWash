package com.smartwash.game.service;

/**
 * 游戏静态转发支撑服务。
 * 只做「定位与校验」，不掺 HTTP 语义：controller 负责 304 / 响应头 / 流生命周期。
 */
public interface GameStaticService {

    /**
     * 解析转发请求 → 命中的对象；任何不命中（key 不合法 / 不存在 / 停用 / 外链 / 对象缺失 / 路径穿越）按契约返回 null 或抛业务异常。
     *
     * @param gameKey      URL 中的游戏标识
     * @param rawRelPath   URL 中 /web/game/{gameKey} 之后的原始（未解码）相对路径，可为空串
     * @return 转发对象元数据；不命中为 null（404 语义）
     */
    ResolvedObject resolve(String gameKey, String rawRelPath);

    /**
     * 转发命中结果：对象流 + 响应元数据；close() 释放底层流。
     */
    record ResolvedObject(GameStaticStream stream, String contentType, String etag, long contentLength) {
    }

    /**
     * 对象流封装（隔离存储层类型）。
     */
    record GameStaticStream(java.io.InputStream input) implements AutoCloseable {
        @Override
        public void close() throws java.io.IOException {
            input.close();
        }
    }
}
