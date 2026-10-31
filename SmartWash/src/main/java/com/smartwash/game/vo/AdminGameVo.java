package com.smartwash.game.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 游戏管理端视图对象。
 * 相比用户端 Vo 额外暴露 id/enabled/来源类型/包信息/备注/时间戳，供 Web 后台游戏库页与治理操作使用。
 */
@Data
@Accessors(chain = true)
public class AdminGameVo {

    private Long id;

    /** 业务唯一标识（创建后不可修改） */
    private String gameKey;

    /** 游戏名 */
    private String title;

    /** 一句话玩法描述 */
    private String description;

    /** 图标 key */
    private String icon;

    /** 图标色组 key：green/blue/orange/pink/teal/purple */
    private String color;

    /** 分类：puzzle/arcade/word/board/other */
    private String category;

    /** 来源类型：1上传包 2外链 */
    private Integer sourceType;

    /** 包内入口相对路径（source_type=1） */
    private String entryPath;

    /** 最终可玩地址 */
    private String url;

    /** MinIO 对象键前缀（source_type=1） */
    private String objectKey;

    /** 包体积字节（source_type=1） */
    private Long fileSize;

    /** 角标文案，空则不显示 */
    private String badge;

    /** 1下发 0不下发（上架开关） */
    private Integer enabled;

    /** 排序，越小越靠前 */
    private Integer sort;

    /** 备注 */
    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
