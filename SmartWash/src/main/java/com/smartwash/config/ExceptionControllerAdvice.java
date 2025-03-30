package com.smartwash.config;

import com.smartwash.common.Result;
import com.smartwash.common.ResultCodeEnum;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.exception.UserAuthenticationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class ExceptionControllerAdvice {
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public Result<String> handleVaildException(MethodArgumentNotValidException e) {
        log.error("数据校验出现问题：{}，异常类型：{}", e.getMessage(), e.getClass());
        BindingResult bindingResult = e.getBindingResult();
        StringBuffer stringBuffer = new StringBuffer();
        bindingResult.getFieldErrors().forEach(item -> {
            //获取错误信息
            String message = item.getDefaultMessage();
            //获取错误的属性名字
            String field = item.getField();
            stringBuffer.append(field).append(":").append(message).append(" ");
        });
        return Result.build(stringBuffer + "", ResultCodeEnum.FAIL);
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
    public Result<String> runTimeException(Throwable throwable) {
        log.error(throwable.getMessage());
        return Result.failMsg(throwable.getMessage());
    }

    @ExceptionHandler(value = Throwable.class)
    public Result<String> handleException(Throwable throwable) {
        log.error("错误", throwable);
        return Result.build("系统异常", ResultCodeEnum.FAIL);
    }
}
