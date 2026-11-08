package com.smartwash.notice.vo;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 管理端送达统计视图对象（GET /admin/notices/{id}/reads）。
 * 口径：发送总数 = 全平台用户总数（v1 不做定向推送，公告对全部用户可见）；
 * 已读数 = notice_read 中该 notice 的去重用户数（唯一索引保证一人一条）。
 */
@Data
@Accessors(chain = true)
public class NoticeReadStatVo {

    /** 发送总数（= 全平台用户数） */
    private Long totalUsers;

    /** 已读数 */
    private Long readCount;

    /** 已读率（如 "33.3%"），保留 1 位小数 */
    private String readRateText;
}
