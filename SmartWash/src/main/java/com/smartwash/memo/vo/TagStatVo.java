package com.smartwash.memo.vo;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 标签统计视图对象（GET /web/auth/memo/tags 返回体）。
 */
@Data
@Accessors(chain = true)
public class TagStatVo {

    /** 标签名 */
    private String tag;

    /** 该标签下的速记条数（按 count 倒序） */
    private Long count;
}
