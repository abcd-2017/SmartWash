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
 * 游戏账号存档，对应表 game_user_save。
 * 一人一游戏一行（uk_user_game），H5 经桥代理读写。
 *
 * <p>state_json 为游戏自定义结构：服务端<b>不解析语义</b>，仅校验 JSON 合法性与大小上限；
 * 结构升级由游戏侧递增 schema_ver，服务端原样存取（不做字段级迁移）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("game_user_save")
public class GameUserSave implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属用户 */
    private Long userId;

    /** 游戏标识（game_config.game_key） */
    private String gameKey;

    /** 游戏自定义存档（JSON 字符串，服务端不解析） */
    private String stateJson;

    /** 存档结构版本（游戏侧升级时递增） */
    private Integer schemaVer;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
