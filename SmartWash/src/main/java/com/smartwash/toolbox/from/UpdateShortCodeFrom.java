package com.smartwash.toolbox.from;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 更新短码请求 DTO（活码改向走这里）。
 * 字段不传即不修改；更新成功后主动清空跳转缓存，302 语义下改向立即生效。
 */
@Data
public class UpdateShortCodeFrom {

    /** 目标长链（http/https），不传不修改 */
    private String target;

    /** 过期时刻，不传不修改 */
    private LocalDateTime expireAt;
}
