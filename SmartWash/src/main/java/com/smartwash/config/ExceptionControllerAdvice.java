package com.smartwash.config;

import com.smartwash.common.Result;
import com.smartwash.common.ResultCodeEnum;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.exception.GameServiceException;
import com.smartwash.exception.UserAuthenticationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class ExceptionControllerAdvice {
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public Result<String> handleValidException(MethodArgumentNotValidException e) {
        log.warn("数据校验出现问题：{}，异常类型：{}", e.getMessage(), e.getClass());
        BindingResult bindingResult = e.getBindingResult();
        StringBuilder sb = new StringBuilder();
        bindingResult.getFieldErrors().forEach(item -> {
            //获取错误信息
            String message = item.getDefaultMessage();
            //获取错误的属性名字
            String field = item.getField();
            sb.append(field).append(":").append(message).append(" ");
        });
        // 校验提示放在 message 字段：三端拦截器与 Web 提示均消费 message，data 保持 null
        return Result.build(null, ResultCodeEnum.FAIL.getCode(), sb.toString());
    }

    /**
     * 方法参数校验失败（@Validated 类级校验，如 @PathVariable 上的 @Pattern）：
     * 返回 HTTP 400 + 统一 Result 信封，避免 ConstraintViolationException 落到全局兜底变成 500 语义。
     */
    @ExceptionHandler(value = ConstraintViolationException.class)
    public ResponseEntity<Result<String>> handleConstraintViolationException(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(" "));
        log.warn("方法参数校验失败：{}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.build(null, HttpStatus.BAD_REQUEST.value(), message));
    }

    /**
     * 用户token校验失败异常捕获
     */
    @ExceptionHandler(value = UserAuthenticationException.class)
    public Result<String> handleUserAuthenticationException(UserAuthenticationException e) {
        log.warn("用户登录状态异常：{}，异常类型：{}", e.getMessage(), e.getClass());
        return Result.build(null, ResultCodeEnum.UNAUTHORIZED);
    }

    @ExceptionHandler(value = CustomExceptions.class)
    public Result<String> runTimeException(CustomExceptions e) {
        log.warn("业务异常: {}", e.getMessage());
        // code 由异常携带（如设备投递的 404/403/409/413 语义），未携带时沿用默认失败码
        return Result.build(null, e.getCode() != null ? e.getCode() : ResultCodeEnum.FAIL.getCode(), e.getMessage());
    }

    /**
     * 游戏中心业务异常捕获：按异常携带的 HTTP 语义业务码返回信封
     * （400 参数/包内容不合法、403 路径穿越、404 不存在、409 gameKey 冲突、413 体积超限）。
     */
    @ExceptionHandler(value = GameServiceException.class)
    public Result<String> handleGameServiceException(GameServiceException e) {
        log.warn("游戏中心业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Result.build(null, e.getCode(), e.getMessage());
    }

    /**
     * multipart 体积超限捕获（multipart 上限先于业务校验触发）：413 语义，对齐上传链路错误码契约。
     */
    @ExceptionHandler(value = MaxUploadSizeExceededException.class)
    public Result<String> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.warn("上传文件超过 multipart 上限: {}", e.getMessage());
        return Result.build(null, GameServiceException.PAYLOAD_TOO_LARGE, "上传文件过大（单文件上限10MB）");
    }

    /**
     * 数据库异常捕获，防止SQL错误信息泄露到前端
     */
    @ExceptionHandler(value = DataAccessException.class)
    public Result<String> handleDataAccessException(DataAccessException e) {
        log.error("数据库异常", e);
        return Result.build("系统异常，请稍后再试", ResultCodeEnum.FAIL);
    }

    /**
     * 兜底异常处理，不暴露内部错误细节
     */
    @ExceptionHandler(value = Exception.class)
    public Result<String> handleException(Exception throwable) {
        log.error("系统异常", throwable);
        return Result.build("系统异常，请稍后再试", ResultCodeEnum.FAIL);
    }
}
