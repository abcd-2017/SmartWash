package com.smartwash.dining.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 家庭点单-菜谱分类。
 * Web 管理后台维护平台公共菜谱库的分类目录，菜谱（dining_recipe，02 号票）按分类挂靠，
 * 用户端按分类组织点单菜单左侧分类栏。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("dining_category")
public class DiningCategory implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 分类名称（如 家常菜/汤羹/凉菜），uk_name 唯一索引裁决 */
    private String name;

    /** 排序，越小越靠前 */
    private Integer sort;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
