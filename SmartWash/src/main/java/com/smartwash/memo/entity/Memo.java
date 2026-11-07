package com.smartwash.memo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 速记手账实体（flomo 式随手记：灵感/待办/日记）。
 * 单人闭环，管理端无页面；tags 为逗号分隔字符串，端上交互为标签数组；
 * 删除为逻辑删除（is_delete=1），读路径一律过滤，物理数据保留。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("memo")
public class Memo implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** users.id */
    private Long userId;

    /** 手账正文（灵感/待办/日记），上限 2000 字符 */
    private String content;

    /** 标签，逗号分隔（如 工作,灵感），可空；标签内禁止半角逗号 */
    private String tags;

    /** 1置顶 0普通，置顶在列表中优先展示 */
    private Integer pinned;

    /** 逻辑删除标记：1已删除（读路径一律过滤），0正常 */
    private Integer isDelete;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
