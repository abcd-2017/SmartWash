package com.smartwash.notice.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 站内信视图对象（用户端收件箱条目）。
 * isRead 为「当前登录用户」的已读状态，由服务端按 read 表批量回填；
 * 收件箱口径 = 广播（user_id IS NULL）或定向给我（user_id = 本人）的公告列表。
 */
@Data
@Accessors(chain = true)
public class NoticeVo {

    private Long id;

    private String title;

    private String content;

    /** 类型：SYSTEM/ANNOUNCEMENT */
    private String type;

    /** 深链跳转目标，空表示纯文案公告 */
    private String deepLink;

    /** 当前用户是否已读 */
    private Boolean isRead;

    private LocalDateTime createdAt;
}
