package com.smartwash.game.storage;

/**
 * 游戏静态资源对象存储抽象（MinIO game/{gameKey}/ 前缀语义）。
 * 下发形态与头像先例一致：后端只管写路径，读取走 MinIO 公共读直链（game_config.url 存完整对象直链），
 * 对象元数据（Content-Type / Cache-Control）在上传时写入，由 MinIO 随 GET 返回，服务侧零转发代码。
 */
public interface GameObjectStorage {

    /** 上传对象统一写入的不可变缓存元数据：游戏内容视为不可变，重传为同键覆盖（契约见 docs/game-api.md） */
    String CACHE_CONTROL_IMMUTABLE = "public, max-age=31536000, immutable";

    /**
     * 写入一个对象（覆盖同键），携带 Content-Type 与 Cache-Control 元数据。
     *
     * @param objectKey    对象键（如 game/snake/index.html）
     * @param content      内容字节
     * @param contentType  MIME 类型（MinIO GET 时作为响应头返回）
     * @param cacheControl 浏览器缓存指令（MinIO GET 时作为响应头返回）
     */
    void putObject(String objectKey, byte[] content, String contentType, String cacheControl);

    /**
     * 删除指定前缀下全部对象（前缀回收，对齐 delivery 清理模式；前缀不存在时幂等静默）。
     *
     * @param prefix 对象键前缀（如 game/snake/）
     */
    void removeObjectsByPrefix(String prefix);
}
