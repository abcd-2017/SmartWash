package com.smartwash.toolbox.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 私密文本一次性揭示结果。仅揭示成功时返回，明文不落日志、不二次下发。
 */
@Data
@Accessors(chain = true)
public class SecretViewVo {

    /** 明文内容（揭示即焚毁，仅此一次下发） */
    private String content;

    /** 过期时刻（回显给接收者，提示链接何时失效） */
    private LocalDateTime expireAt;
}
