package com.smartwash.toolbox.vo;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 私密文本元信息视图对象（公开查看页首屏）。
 * 故意不携带任何内容相关字段：不存在/已焚毁/已过期的语义由信封业务码（404/410）表达。
 */
@Data
@Accessors(chain = true)
public class SecretMetaVo {

    /** 是否需要口令（揭示时须携带） */
    private boolean requiresPassword;
}
