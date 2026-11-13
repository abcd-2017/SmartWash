package com.smartwash.dining.from;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 管理端新增菜谱请求 DTO。
 * 分类可空=未分类；封面走独立上传接口返回对象键后回填（或编辑时另行上传），新增时可先不带封面。
 */
@Data
public class AddDiningRecipeFrom {

    /** 菜名（不做唯一约束，同名菜谱是合法业务态） */
    @NotBlank(message = "菜名不能为空")
    @Size(max = 64, message = "菜名过长（上限64字符）")
    private String name;

    /** 所属分类 ID：null 或 0=未分类（归一为 null 落库）；&gt;0 时校验分类存在 */
    private Long categoryId;

    /** 食材名数组（如 ["西红柿","鸡蛋"]），null=暂不填写 */
    @Size(max = 100, message = "食材数量过多（上限100项）")
    private List<@Size(max = 32, message = "单个食材名过长（上限32字符）") String> ingredients;

    /** 做法步骤（纯文本），null=暂不填写 */
    @Size(max = 20000, message = "步骤内容过长（上限20000字符）")
    private String steps;

    /** 封面对象键（封面上传接口返回），null=暂不设置 */
    @Size(max = 255, message = "封面对象键过长")
    private String coverObjectKey;
}
