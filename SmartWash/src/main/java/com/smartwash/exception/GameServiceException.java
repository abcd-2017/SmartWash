package com.smartwash.exception;

import lombok.Getter;

/**
 * 游戏中心业务异常：携带 HTTP 语义业务码，由 ExceptionControllerAdvice 统一转为 Result 信封 code。
 * 业务码语义（对齐多端联动的错误码契约，见 docs/game-api.md）：
 * 400 参数/包内容不合法；403 权限或路径穿越拒绝；404 资源不存在；409 game_key 并发冲突；413 上传体积超限。
 */
@Getter
public class GameServiceException extends RuntimeException {

    public static final int BAD_REQUEST = 400;
    /** 权限不足 / 路径穿越拒绝 */
    public static final int FORBIDDEN = 403;
    /** 资源不存在（含停用游戏的转发 404 语义） */
    public static final int NOT_FOUND = 404;
    /** game_key 并发唯一索引冲突 */
    public static final int CONFLICT = 409;
    public static final int PAYLOAD_TOO_LARGE = 413;
    public static final int INTERNAL_ERROR = 500;

    private final int code;

    public GameServiceException(int code, String message) {
        super(message);
        this.code = code;
    }
}
