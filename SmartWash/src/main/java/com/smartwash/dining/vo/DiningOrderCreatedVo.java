package com.smartwash.dining.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 创建点单订单返回 VO。
 * joinToken 供端上拼二维码内容（https://{host}/dining/join/{orderId}?t={join_token}，host 对齐深链体系）。
 */
@Data
@Accessors(chain = true)
@AllArgsConstructor
public class DiningOrderCreatedVo {

    /** 新订单 ID */
    private Long orderId;

    /** 加入凭证（发起者重置后需重新生成二维码） */
    private String joinToken;
}
