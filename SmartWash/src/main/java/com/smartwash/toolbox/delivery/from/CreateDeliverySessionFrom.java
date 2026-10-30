package com.smartwash.toolbox.delivery.from;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建投递会话请求 DTO。
 * 有效期默认 30 分钟，可传 1-120 分钟；密码可选（BCrypt 落库，复用内核 password_hash 语义）。
 */
@Data
public class CreateDeliverySessionFrom {

    /** 会话有效期（分钟），不传默认 30 */
    @Min(value = 1, message = "有效期须为1-120分钟")
    @Max(value = 120, message = "有效期须为1-120分钟")
    private Integer durationMinutes;

    /** 对端进入密码（可选，4-64 位明文，服务端 BCrypt 加密存储） */
    @Size(min = 4, max = 64, message = "密码须为4-64位")
    private String password;
}
