package com.smartwash.notice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 站内信主表（运营通知/公告/业务事件定向通知）。
 * 已读标记采用懒创建（notice_read 仅在用户首次标记时插入）：未读 = notice 不在 read 表，
 * 未读数 = 「可见 notice 数（广播或定向给我）」− 当前用户已读数，两个 COUNT 相减，不做全量 fan-out。
 * v2 若单用户未读数查询成为热点，可改为 fan-out（发送时写 notice_read 全量行 + Redis 计数缓存）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("notice")
public class Notice implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 标题 */
    private String title;

    /** 正文（TEXT，可含换行） */
    private String content;

    /** 类型：SYSTEM/ANNOUNCEMENT（v2 可扩展业务事件类型） */
    private String type;

    /** 深链：客户端点击跳转目标（smartwash://... 或 https://...），可空表示仅纯文案公告 */
    private String deepLink;

    /**
     * 定向接收人（users.user_id）；NULL=全平台广播。
     * 定向消息按接收人一人一行（实现取舍见 NoticeServiceImpl#publishTargeted 注释），
     * 广播行 user_id 保持 NULL，既有广播行为完全不变。
     */
    private Long userId;

    /** 发送人（admin_users.admin_id），管理端从登录上下文写入 */
    private Long creatorAdminId;

    private LocalDateTime createdAt;
}
