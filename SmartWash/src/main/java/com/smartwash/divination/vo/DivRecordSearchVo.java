package com.smartwash.divination.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 解读历史检索结果视图（/web/auth/div/records/search）。
 */
@Data
public class DivRecordSearchVo {

    /** 卦例 ID（与 recordId 同值，兼容端上两种取用习惯） */
    private Long id;

    /** 卦例 ID */
    private Long recordId;

    /** liuyao/meihua/qimen/liuren */
    private String method;

    /** general/career/wealth/... */
    private String category;

    /** 卦例问题 */
    private String question;

    /** 起卦时刻 */
    private LocalDateTime castAt;

    /** 命中内容预览：卦例问题或最新解读正文前 120 字 */
    private String contentPreview;
}
