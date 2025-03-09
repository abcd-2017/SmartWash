package com.smartwash.config;

import com.smartwash.common.Result;
import com.smartwash.common.ResultCodeEnum;
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

    @ExceptionHandler(value = Throwable.class)
    public Result<String> handleException(Throwable throwable) {
        log.error("错误", throwable);
        return Result.build("系统异常", ResultCodeEnum.FAIL);
    }
}
