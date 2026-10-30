package com.smartwash.toolbox.delivery.from;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 对端进入投递会话请求 DTO（匿名凭码，密码可选）。
 */
@Data
public class EnterDeliverySessionFrom {

    /** 进入密码（会话设置密码时必传） */
    @Size(max = 64, message = "密码过长")
    private String password;
}
