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
 * 站内信已读账本（懒创建）。
 * uk_user_notice(user_id, notice_id) 唯一索引 = 幂等 DB 闸门：
 * 并发/重复标记只允许一条插入成功，其余捕获 DuplicateKeyException 判幂等成功。
 * 无逻辑删除、无「取消已读」语义，故 insert ignore 风格即可（不需要状态位）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("notice_read")
public class NoticeRead implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** users.user_id */
    private Long userId;

    /** notice.id */
    private Long noticeId;

    /** 首次标记已读时间 */
    private LocalDateTime readAt;
}
