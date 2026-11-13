package com.smartwash.dining.from;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 管理端修改菜谱请求 DTO（全量字段语义：除 id 外的字段不传即不修改，与 MyBatis-Plus
 * updateById 跳过 null 列的默认行为对齐；上下架走专用状态接口，不在本接口表达）。
 */
@Data
public class UpdateDiningRecipeFrom {

    @NotNull(message = "菜谱ID不能为空")
    private Long id;

    /** 菜名，null=不修改 */
    @Size(max = 64, message = "菜名过长（上限64字符）")
    private String name;

    /** 所属分类 ID：null=不修改；0=改回未分类（归一为 null 落库）；&gt;0=改到指定分类（后端校验存在） */
    private Long categoryId;

    /** 食材名数组，null=不修改 */
    @Size(max = 100, message = "食材数量过多（上限100项）")
    private List<@Size(max = 32, message = "单个食材名过长（上限32字符）") String> ingredients;

    /** 做法步骤，null=不修改 */
    @Size(max = 20000, message = "步骤内容过长（上限20000字符）")
    private String steps;

    /** 封面对象键（封面上传接口返回的新键），null=不修改 */
    @Size(max = 255, message = "封面对象键过长")
    private String coverObjectKey;
}
