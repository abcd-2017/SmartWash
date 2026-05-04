package com.smartwash.config;

import com.smartwash.common.Result;
import com.smartwash.common.ResultCodeEnum;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.exception.UserAuthenticationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class ExceptionControllerAdvice {
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public Result<String> handleValidException(MethodArgumentNotValidException e) {
        log.error("数据校验出现问题：{}，异常类型：{}", e.getMessage(), e.getClass());
        BindingResult bindingResult = e.getBindingResult();
        StringBuilder sb = new StringBuilder();
        bindingResult.getFieldErrors().forEach(item -> {
            //获取错误信息
            String message = item.getDefaultMessage();
            //获取错误的属性名字
            String field = item.getField();
            sb.append(field).append(":").append(message).append(" ");
        });
        return Result.build(sb.toString(), ResultCodeEnum.FAIL);
    }

    /**
     * 用户token校验失败异常捕获
     */
    @ExceptionHandler(value = UserAuthenticationException.class)
    public Result<String> handleUserAuthenticationException(UserAuthenticationException e) {
        log.error("用户登录状态异常：{}，异常类型：{}", e.getMessage(), e.getClass());
        return Result.build(null, ResultCodeEnum.UNAUTHORIZED);
    }

    @ExceptionHandler(value = CustomExceptions.class)
    public Result<String> runTimeException(CustomExceptions e) {
        log.error("业务异常", e);
        return Result.failMsg(e.getMessage());
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
    @ExceptionHandler(value = Throwable.class)
    public Result<String> handleException(Throwable throwable) {
        log.error("系统异常", throwable);
        return Result.build("系统异常，请稍后再试", ResultCodeEnum.FAIL);
    }
}
