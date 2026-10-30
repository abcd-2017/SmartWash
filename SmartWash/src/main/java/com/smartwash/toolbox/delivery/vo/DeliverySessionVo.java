package com.smartwash.toolbox.delivery.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 投递会话视图对象（会话元信息不含 password_hash，仅回是否已设密）。
 */
@Data
@Accessors(chain = true)
public class DeliverySessionVo {

    /** 投递码（7 位） */
    private String code;

    /** 会话过期时刻（关闭 = 提前置为当前时刻） */
    private LocalDateTime expireAt;

    /** 累计进入次数（对端凭码进入时原子自增） */
    private Long clickCount;

    /** 是否已设置进入密码 */
    private Boolean hasPassword;

    /** 条目总数（含已焚毁占位） */
    private Long itemCount;

    /** 未取走条目数 */
    private Long pendingCount;

    /** 对端 H5 页地址（拼 {public-base-url}/d/{code}，二维码内容由端侧生成） */
    private String enterUrl;

    /** 会话创建时刻 */
    private LocalDateTime createdAt;
}
