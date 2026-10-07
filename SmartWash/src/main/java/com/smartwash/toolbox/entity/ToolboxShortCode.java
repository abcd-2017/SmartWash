package com.smartwash.toolbox.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工具箱短码内核主表。
 * 五入口（短链/活码/交付箱/图床/时间胶囊）共用，差异全部体现为 content_type + 可空列，不做子表。
 * 过期/解锁/上限判断全部在读路径完成，定时任务只做物理清理。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("toolbox_short_code")
public class ToolboxShortCode implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 短码：7 位随机或 4-16 位自定义别名，uk_code 唯一索引是并发下的最终裁决 */
    private String code;

    /** 1短链 2活码 3交付箱 4图床 5时间胶囊 */
    private Integer contentType;

    /** 类型1/2:目标长链;类型4:MinIO对象名 */
    private String target;

    /** 类型3/5:文本正文(交付箱密文/胶囊内容) */
    private String content;

    /** users.id */
    private Long ownerUserId;

    /** 1公开 2私有;私有仅owner可解析,匿名一律404 */
    private Integer isPublic;

    /** 类型3访问密码(BCrypt),其余NULL */
    private String passwordHash;

    /** 类型3:阅后即焚(读取后内容即毁) */
    private Integer burnAfterRead;

    /** 类型5解锁时刻,NULL不锁,读路径判断 */
    private LocalDateTime unlockAt;

    /** 过期时刻,NULL永不过期,读路径判断 */
    private LocalDateTime expireAt;

    /** 访问上限,NULL不限 */
    private Long maxVisits;

    /** 累计跳转点击（统计链路用 setSql 原子自增，防并发丢失更新） */
    private Long clickCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
