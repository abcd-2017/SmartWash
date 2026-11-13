package com.smartwash.dining.from;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 加入点单订单请求 DTO。
 * joinToken 来自二维码内容（https://{host}/dining/join/{orderId}?t={join_token}），服务端校验匹配。
 */
@Data
public class JoinDiningOrderFrom {

    /** 加入凭证（与订单当前 join_token 一致才放行；发起者重置后旧 token 全部作废） */
    @NotBlank(message = "加入凭证不能为空")
    private String joinToken;
}
