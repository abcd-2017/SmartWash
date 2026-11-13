package com.smartwash.toolbox.from;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 私密文本一次性揭示请求 DTO（公开接口，匿名访问）。
 */
@Data
public class SecretViewFrom {

    /** 访问口令（创建时设置了口令则必传；口令错误不焚毁） */
    @Size(min = 4, max = 32, message = "口令须为4-32位")
    private String password;
}
