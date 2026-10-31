package com.smartwash.game.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * H5 游戏库与下发配置。
 * 管理端上传游戏包（单 html / zip，存 MinIO game/{gameKey}/ 前缀）并配置元数据，
 * 用户端经 /web/auth/game/list 下发启用项；公开转发 /web/game/{gameKey}/** 按对象键前缀流式回源。
 * game_key 是端上与 URL 共同引用的业务唯一标识，创建后不可修改（改 key 等于换游戏）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("game_config")
public class GameConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 业务唯一标识：管理端、端上与转发 URL 共同引用，uk_game_key 唯一索引裁决 */
    private String gameKey;

    /** 游戏名 */
    private String title;

    /** 一句话玩法描述 */
    private String description;

    /** 图标 key，端上白名单枚举映射，未知值显示默认图标 */
    private String icon;

    /** 图标色组 key：green/blue/orange/pink/teal/purple */
    private String color;

    /** 分类：puzzle/arcade/word/board/other */
    private String category;

    /** 来源类型：1上传包 2外链 */
    private Integer sourceType;

    /** source_type=1：包内入口相对路径，默认 index.html */
    private String entryPath;

    /** 最终可玩地址（构造规则：{public-base-url}/web/game/{gameKey}/，source_type=2 为外部 https 地址） */
    private String url;

    /** source_type=1：MinIO 对象键前缀 game/{gameKey}/ */
    private String objectKey;

    /** 包体积字节（source_type=1） */
    private Long fileSize;

    /** 角标文案（新/热），空则不显示 */
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
