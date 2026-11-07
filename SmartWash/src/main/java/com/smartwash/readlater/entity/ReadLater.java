package com.smartwash.readlater.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 稍后读（AI 摘要）收藏记录。
 * 收藏 https 链接 → 后端抓取标题 → 按需 AI 生成摘要 → 标签/已读管理。
 * 本表使用 is_delete 逻辑删除（区别于主库物理删除惯例）：删除仅打标，列表查询一律过滤。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("read_later")
public class ReadLater implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 未读 */
    public static final int STATUS_UNREAD = 0;
    /** 已读 */
    public static final int STATUS_READ = 1;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** users.id */
    private Long userId;

    /** 收藏的 https 链接 */
    private String url;

    /** 页面标题（抓取失败时兜底为 url） */
    private String title;

    /** AI 摘要（≤150 字，按需生成） */
    private String digest;

    /** 标签，逗号分隔 */
    private String tags;

    /** 0未读 1已读 */
    private Integer status;

    /** 逻辑删除：0正常 1已删除 */
    private Integer isDelete;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
